import random
from scipy import stats
from typing import Callable
import pandas as pd
import torch
import numpy as np
import time
import os
import glob
from itertools import cycle, count
import matplotlib.pyplot as plt
from IPython import display
from torch.utils.data import TensorDataset

from ppo.cnn import CNNActor
from ppo.custom_dataset import CustomDictDataset
from ppo.episodebuffer import EpisodeBuffer
from ppo.ewc import EWC

LEAVE_PRINT_EVERY_N_SECS = 300
ERASE_LINE = '\x1b[2K'
EPS = 1e-6

class PPO():
    def __init__(self, 
                 policy_model_fn, 
                 policy_model_max_grad_norm,
                 policy_optimizer_fn,
                 policy_optimizer_lr,
                 policy_optimization_epochs,
                 policy_sample_ratio,
                 policy_clip_range,
                 policy_stopping_kl,
                 value_model_fn, 
                 value_model_max_grad_norm,
                 value_optimizer_fn,
                 value_optimizer_lr,
                 value_optimization_epochs,
                 value_sample_ratio,
                 value_clip_range,
                 value_stopping_mse,
                 ewc_fn,
                 ewc_lambda,
                 episode_buffer_fn,
                 max_buffer_episodes,
                 max_buffer_episode_steps,
                 entropy_loss_weight,
                 tau,
                 n_workers):
        assert n_workers > 1
        assert max_buffer_episodes >= n_workers

        self.policy_model_fn = policy_model_fn
        self.policy_model_max_grad_norm = policy_model_max_grad_norm
        self.policy_optimizer_fn = policy_optimizer_fn
        self.policy_optimizer_lr = policy_optimizer_lr
        self.policy_optimization_epochs = policy_optimization_epochs
        self.policy_sample_ratio = policy_sample_ratio
        self.policy_clip_range = policy_clip_range
        self.policy_stopping_kl = policy_stopping_kl

        self.value_model_fn = value_model_fn
        self.value_model_max_grad_norm = value_model_max_grad_norm
        self.value_optimizer_fn = value_optimizer_fn
        self.value_optimizer_lr = value_optimizer_lr
        self.value_optimization_epochs = value_optimization_epochs
        self.value_sample_ratio = value_sample_ratio
        self.value_clip_range = value_clip_range
        self.value_stopping_mse = value_stopping_mse

        self.ewc_fn = ewc_fn
        self.ewc_lambda = ewc_lambda

        self.episode_buffer_fn = episode_buffer_fn
        self.max_buffer_episodes = max_buffer_episodes
        self.max_buffer_episode_steps = max_buffer_episode_steps

        self.entropy_loss_weight = entropy_loss_weight
        self.tau = tau
        self.n_workers = n_workers

    def optimize_model(self):
        states, actions, returns, gaes, logpas = self.episode_buffer.get_stacks()
        with torch.no_grad():
            values = self.value_model(states).detach()
        gaes = (gaes - gaes.mean()) / (gaes.std() + EPS)
        n_samples = len(actions)
        
        for _ in range(self.policy_optimization_epochs):
            batch_size = int(self.policy_sample_ratio * n_samples)
            batch_idxs = np.random.choice(n_samples, batch_size, replace=False)
            states_batch = {key: val[batch_idxs] for key, val in states.items()}
            actions_batch = actions[batch_idxs]
            gaes_batch = gaes[batch_idxs]
            logpas_batch = logpas[batch_idxs]

            logpas_pred, entropies_pred = self.policy_model.get_predictions(states_batch, actions_batch)

            ratios = (logpas_pred - logpas_batch).exp()
            pi_obj = gaes_batch * ratios
            pi_obj_clipped = gaes_batch * ratios.clamp(1.0 - self.policy_clip_range,
                                                       1.0 + self.policy_clip_range)
            policy_loss = -torch.min(pi_obj, pi_obj_clipped).mean()
            entropy_loss = -entropies_pred.mean() * self.entropy_loss_weight

            # Calculate the EWC penalty if the ewc object exists
            ewc_penalty = self.ewc.penalty()

            self.policy_optimizer.zero_grad()
            total_loss = policy_loss + entropy_loss + ewc_penalty
            total_loss.backward()
            torch.nn.utils.clip_grad_norm_(self.policy_model.parameters(), 
                                           self.policy_model_max_grad_norm)
            self.policy_optimizer.step()
            
            with torch.no_grad():
                logpas_pred_all, _ = self.policy_model.get_predictions(states, actions)
                kl = (logpas - logpas_pred_all).mean()
                if kl.item() > self.policy_stopping_kl:
                    break

        for _ in range(self.value_optimization_epochs):
            batch_size = int(self.value_sample_ratio * n_samples)
            batch_idxs = np.random.choice(n_samples, batch_size, replace=False)
            states_batch = {key: val[batch_idxs] for key, val in states.items()}
            returns_batch = returns[batch_idxs]
            values_batch = values[batch_idxs]

            values_pred = self.value_model(states_batch)
            values_pred_clipped = values_batch + (values_pred - values_batch).clamp(-self.value_clip_range, 
                                                                                    self.value_clip_range)
            v_loss = (returns_batch - values_pred).pow(2)
            v_loss_clipped = (returns_batch - values_pred_clipped).pow(2)
            value_loss = torch.max(v_loss, v_loss_clipped).mul(0.5).mean()

            self.value_optimizer.zero_grad()
            value_loss.backward()
            torch.nn.utils.clip_grad_norm_(self.value_model.parameters(), 
                                           self.value_model_max_grad_norm)
            self.value_optimizer.step()

            with torch.no_grad():
                values_pred_all = self.value_model(states)
                mse = (values - values_pred_all).pow(2).mul(0.5).mean()
                if mse.item() > self.value_stopping_mse:
                    break

    def plot(self, eva100_reward, save=False, early_stop_config=None):
        fig = plt.gcf()
        display.clear_output(wait=True)
        display.display(fig)
        plt.clf()
        rewards_series = pd.Series(eva100_reward)
        moving_avg = rewards_series.rolling(window=50, min_periods=10).mean()
        plt.title('Result')
        plt.xlabel('Episode')
        plt.ylabel('Reward')
        plt.plot(eva100_reward, alpha=0.5, label='Episode Reward (Mean-100)')
        plt.plot(moving_avg, color='red', linewidth=2, label='Smoothed Average (50ep window)')
        last_ma_value = moving_avg.iloc[-1]
        plt.text(len(eva100_reward)-1, last_ma_value, f'{last_ma_value:.2f}')
        plt.legend()


        if early_stop_config and early_stop_config.get('enabled', False):
            min_episodes = early_stop_config.get('min_episodes', 200)
            trend_window = early_stop_config.get('trend_window', 50) 
            slope_threshold = early_stop_config.get('slope_threshold', 0.01) 

            if len(eva100_reward) > min_episodes and len(moving_avg) > trend_window:
                recent_trend_data = moving_avg.iloc[-trend_window:]
                x_axis = np.arange(len(recent_trend_data))
                slope, intercept, r_value, p_value, std_err = stats.linregress(x_axis, recent_trend_data)

                if slope < slope_threshold:
                    print(f"\n\n*** EARLY STOPPING CRITERION MET ***")
                    print(f"Performance trend slope ({slope:.4f}) is below the threshold ({slope_threshold:.4f}).")
                    print("Raising KeyboardInterrupt to gracefully end training.")
                    plt.savefig('C:/thesis_data/result_plot_EARLY_STOP.png')
                    raise KeyboardInterrupt

        # --- Saving logic (unchanged) ---
        if len(eva100_reward) % 20 == 0 or save:
            plt.savefig('C:/thesis_data/result_plot_episode_{}.png'.format(len(eva100_reward)))

        plt.show(block=False)
        plt.pause(1)
        try:
            manager = fig.canvas.manager
            manager.window.lower()
        except Exception:
            pass

    def find_model_file_path(self, start_with):
        current_dir = os.getcwd()
        for f in os.listdir(current_dir):
            if f.startswith(start_with) and f.endswith(".tar"):
                return os.path.join(current_dir, f)
        return None

    def train(self, make_envs_fn:Callable, make_env_fn:Callable, gamma, 
              max_minutes, max_episodes, goal_mean_100_reward, level_pool:list):
        training_start, last_debug_time = time.time(), float('-inf')

        self.make_envs_fn = make_envs_fn
        self.make_env_fn = make_env_fn
        self.gamma = gamma
        
        env = self.make_env_fn()
        envs = self.make_envs_fn(make_env_fn, self.n_workers)
        SEEDS = (12, 34, 56, 78, 90)
        seed = random.choice(SEEDS)
        torch.manual_seed(seed) ; np.random.seed(seed) ; random.seed(seed)
    
        self.nS, nA = env.observation_space, env.action_space.n
        self.episode_timestep, self.episode_reward = [], []
        self.episode_seconds, self.episode_exploration = [], []
        self.evaluation_scores = []

        self.policy_model = self.policy_model_fn(self.nS, nA)
        self.policy_optimizer = self.policy_optimizer_fn(self.policy_model, self.policy_optimizer_lr)

        self.value_model = self.value_model_fn(self.nS)
        self.value_optimizer = self.value_optimizer_fn(self.value_model, self.value_optimizer_lr)

        self.ewc:EWC = self.ewc_fn(self.policy_model, self.ewc_lambda)

        policy_model_state = self.find_model_file_path('model.policy')
        if policy_model_state is not None:
            self.policy_model.load_state_dict(torch.load(policy_model_state, weights_only=True))
            self.policy_model.eval()

        value_model_state = self.find_model_file_path('model.value')
        if value_model_state is not None:
            self.value_model.load_state_dict(torch.load(value_model_state, weights_only=True))
            self.value_model.eval()

        ewc_state_path = self.find_model_file_path('model.ewc_state')
        if ewc_state_path is not None:
            ewc_state = torch.load(ewc_state_path, map_location=self.policy_model.device)
            self.ewc.fisher_matrix = ewc_state.get('fisher', self.ewc.create_empty_clone())
            self.ewc.optimal_params = ewc_state.get('params', {}) # Params can start as empty dict

        self.episode_buffer:EpisodeBuffer = self.episode_buffer_fn(self.nS, self.gamma, self.tau,
                                                     self.n_workers, 
                                                     self.max_buffer_episodes,
                                                     self.max_buffer_episode_steps)

        result = np.empty((max_episodes, 5))
        result[:] = np.nan
        training_time = 0
        episode = 0
        self.eva100 = []
        # ls = level_pool = [ 
        #     ]
        # for i in ls:
        #     final_eval_score, score_std = self.evaluate(self.policy_model, env, i, n_episodes=1)

        stop_config = {
            'enabled': False,
            'min_episodes': 250,
            'trend_window': 20,
            'slope_threshold': 0.01
        }
       
        try:
            while True:
                episode_timestep, episode_reward, episode_exploration, \
                episode_seconds = self.episode_buffer.fill(envs, self.policy_model, self.value_model, episode, level_pool, visual=False)
                
                n_ep_batch = len(episode_timestep)
                self.episode_timestep.extend(episode_timestep)
                self.episode_reward.extend(episode_reward)
                self.episode_exploration.extend(episode_exploration)
                self.episode_seconds.extend(episode_seconds)
                self.optimize_model()
                self.episode_buffer.clear()

                # stats
                evaluation_score, _ = self.evaluate(self.policy_model, env, random.choice(level_pool))

                self.eva100.append(np.mean(self.evaluation_scores[-100:]))
                if len(self.eva100) % 5 == 0:
                    self.plot(self.eva100, early_stop_config=stop_config)
                
                self.evaluation_scores.extend([evaluation_score,] * n_ep_batch)
                # for e in range(episode, episode + n_ep_batch):
                    
                training_time += episode_seconds.sum()
                wallclock_time = time.time() - training_start
                with open("C:/thesis_data/result.txt", "a") as file:
                    file.write("pool [{}]\n".format(', '.join(level_pool)))
                    file.write("n_ep_batch {}\n".format(n_ep_batch))
                    file.write("episode_timestep {}\n".format(episode_timestep))
                    file.write("episode_reward {}\n".format(np.round(episode_reward, 2)))
                    file.write("episode_exploration {}\n".format(np.round(episode_exploration, 2)))
                    file.write("episode_seconds {}\n".format(np.round(episode_seconds, 2)))
                    file.write("training_time {}\n".format(training_time))
                    file.write("wallclock_time {}\n".format(wallclock_time))

                mean_10_reward = np.mean(self.episode_reward[-10:])
                std_10_reward = np.std(self.episode_reward[-10:])
                mean_100_reward = np.mean(self.episode_reward[-100:])
                std_100_reward = np.std(self.episode_reward[-100:])
                mean_100_eval_score = np.mean(self.evaluation_scores[-100:])
                std_100_eval_score = np.std(self.evaluation_scores[-100:])
                mean_100_exp_rat = np.mean(self.episode_exploration[-100:])
                std_100_exp_rat = np.std(self.episode_exploration[-100:])
                
                total_step = int(np.sum(self.episode_timestep))
                wallclock_elapsed = time.time() - training_start
                result[episode:episode+n_ep_batch] = total_step, mean_100_reward, \
                    mean_100_eval_score, training_time, wallclock_elapsed

                episode += n_ep_batch

                # debug stuff
                reached_debug_time = time.time() - last_debug_time >= LEAVE_PRINT_EVERY_N_SECS
                reached_max_minutes = wallclock_elapsed >= max_minutes * 60            
                reached_max_episodes = episode + self.max_buffer_episodes >= max_episodes
                reached_goal_mean_reward = mean_100_eval_score >= goal_mean_100_reward
                training_is_over = reached_max_minutes or \
                                reached_max_episodes or \
                                reached_goal_mean_reward
                elapsed_str = time.strftime("%H:%M:%S", time.gmtime(time.time() - training_start))
                debug_message = 'el {}, ep {:04}, ts {:07}, '
                debug_message += 'ar 10 {:05.1f}\u00B1{:05.1f}, '
                debug_message += '100 {:05.1f}\u00B1{:05.1f}, '
                debug_message += 'ex 100 {:02.1f}\u00B1{:02.1f}, '
                debug_message += 'ev {:05.1f}\u00B1{:05.1f}'
                debug_message = debug_message.format(
                    elapsed_str, episode-1, total_step, mean_10_reward, std_10_reward, 
                    mean_100_reward, std_100_reward, mean_100_exp_rat, std_100_exp_rat,
                    mean_100_eval_score, std_100_eval_score)
                print(debug_message, end='\r', flush=True)
                if reached_debug_time or training_is_over:
                    print(ERASE_LINE + debug_message, flush=True)
                    last_debug_time = time.time()
                if training_is_over:
                    if reached_max_minutes: print(u'--> reached_max_minutes \u2715')
                    if reached_max_episodes: print(u'--> reached_max_episodes \u2715')
                    if reached_goal_mean_reward: print(u'--> reached_goal_mean_reward \u2713')
                    break
        
        except KeyboardInterrupt:
            print("!Ctrl+C detected! Saving progress before exiting...")
        finally:
            if 'env' in locals():
                env.close()
                del env
            if 'envs' in locals():
                envs.close()
                del envs
            self.save_checkpoint(len(self.eva100), self.policy_model, 'policy', True)
            self.save_checkpoint(len(self.eva100), self.value_model, 'value', True)
            self.plot(self.eva100, True)


    def evaluate(self, eval_model:CNNActor, eval_env, level:str, n_episodes=1, greedy=True):
        rs = []
        for _ in range(n_episodes):
            info = {"episode" : 0, "evaluation" : True, "visual":True, "level" : level}
            s, _  = eval_env.reset(options=info)
            d = False
            rs.append(0)
            for _ in count():
                if greedy:
                    a = eval_model.select_greedy_action(s)
                else: 
                    a = eval_model.select_action(s)
                s, r, d, _, _ = eval_env.step(a)
                rs[-1] += r
                if d: break
        return np.mean(rs), np.std(rs)

    def finish_task(self, level_pool: list):
        # Create a temporary buffer to collect data
        temp_buffer:EpisodeBuffer = self.episode_buffer_fn(
            self.nS,
            self.gamma,
            self.tau,
            self.n_workers,
            self.max_buffer_episodes,
            self.max_buffer_episode_steps
        )
        
        # Use existing environments to fill the buffer
        envs = self.make_envs_fn(self.make_env_fn, self.n_workers)
        temp_buffer.fill(envs, self.policy_model, self.value_model, 
                        episodeStart=0,
                        level_pool=level_pool)
        envs.close()
        
        # Get the collected states and actions
        states, actions, _, _, _ = temp_buffer.get_stacks()
        
        # The EWC class needs a PyTorch TensorDataset
        dataset = CustomDictDataset(
            states_dict=states, 
            actions_tensor=actions
        )
        
        # Register the task dataset with EWC
        self.ewc.register_task(dataset)

    def save_checkpoint(self, evaluation_idx, model, suffix, manual = False):
        if evaluation_idx > 30 or manual:
            torch.save(model.state_dict(), 
                            os.path.join('C:/thesis_data', 'model.{}.{}.tar'.format(suffix, evaluation_idx)))
            
    def save_ewc(self, level_pool):
        self.finish_task(level_pool=level_pool)

        ewc_state = {
            'fisher': self.ewc.fisher_matrix,
            'params': self.ewc.optimal_params
        }
        
        save_path = os.path.join('C:/thesis_data', 'model.ewc_state.tar')
        torch.save(ewc_state, save_path)