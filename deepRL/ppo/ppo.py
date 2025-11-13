import base64
import json
import logging
import random
import matplotlib
import rlstatistics as statistics
from logger import setup_logging
matplotlib.use('TkAgg')
from scipy import stats
from typing import Any, Callable, Dict, Tuple
import pandas as pd
import torch
import numpy as np
import time
import os
import glob
from itertools import cycle, count
import matplotlib.pyplot as plt
import traceback
import shutil
from IPython import display
from torch.utils.data import TensorDataset

from ppo.cnn import CNNActor
from ppo.custom_dataset import CustomDictDataset
from ppo.episodebuffer import EpisodeBuffer
# from ppo.ewc import EWC
import socket
import threading
import time
import queue
from torch.optim.lr_scheduler import LinearLR

logger = logging.getLogger('Agent:PPO')

LEAVE_PRINT_EVERY_N_SECS = 300
ERASE_LINE = '\x1b[2K'
EPS = 1e-6

fig, ax = plt.subplots()

hyper_params_mapper = {
    "policyOptimizerLr": "policy_optimizer_lr",
    "policyOptimizationEpochs": "policy_optimization_epochs",
    "policyClipRange": "policy_clip_range",
    "policyStoppingKl": "policy_stopping_kl",
    "valueOptimizerLr": "value_optimizer_lr",
    "valueOptimizationEpochs": "value_optimization_epochs",
    "valueClipRange": "value_clip_range",
    "ewcLambda": "ewc_lambda",
    "maxBufferEpisodes": "max_buffer_episodes",
    "maxBufferEpisodeSteps": "max_buffer_episode_steps",
    "entropyLossWeight": "entropy_loss_weight",
    "batchSize": "batch_size",
    "valueStoppingMse" : "value_stopping_mse"
}

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
                 n_workers,
                 batch_size, 
                 load_optimizer:bool):
        assert n_workers > 1
        assert max_buffer_episodes >= n_workers
        setup_logging(logging.INFO)
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
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
        self.received_data_queue: queue.Queue[Tuple[str, Dict[str, Any]]] = queue.Queue()
        self.best_score = 0
        self.batch_size = batch_size
        self.load_optimizer = load_optimizer
        self.visual_train:bool = False

        self.grid_keys = [
            'gridSolid', 'gridBlocks', 'gridCoins', 'gridGoomba', 'gridGoombaWing',
            'gridGreenKoompa', 'gridGreenKoompaWing', 'gridRedKoompa', 'gridRedKoompaWing',
            'gridSpiky', 'gridSpikyWing', 'gridEnemyFlower', 'gridShell', 'gridBulletBill',
            'gridMushroom', 'gridFirepower', 'gridLifeMushroom', 'gridBrick',
            'gridSemiSolid', 'gridFlags', 'gridFireball'
        ]

        logger.info(f'policy_optimizer_lr {self.policy_optimizer_lr}')
        logger.info(f'policy_sample_ratio {self.policy_sample_ratio}')
        logger.info(f'policy_clip_range {self.policy_clip_range}')
        logger.info(f'policy_stopping_kl {self.policy_stopping_kl}')

        logger.info(f'value_optimizer_lr {self.value_optimizer_lr}')
        logger.info(f'value_sample_ratio {self.value_sample_ratio}')
        logger.info(f'value_clip_range {self.value_clip_range}')
        logger.info(f'value_stopping_mse {self.value_stopping_mse}')

        logger.info(f'max_buffer_episodes {self.max_buffer_episodes}')
        logger.info(f'max_buffer_episode_steps {self.max_buffer_episode_steps}')
        logger.info(f'entropy_loss_weight {self.entropy_loss_weight}')
        logger.info(f'batch_size {self.batch_size}')
        logger.info(f'n_workers {self.n_workers}')
        logger.info(f'load_optimizer {self.load_optimizer}')
        self.parallel_eval = True
        # Persistent evaluation envs (created on first use, closed in training cleanup)
        self._eval_envs = None
        self._eval_envs_workers = 0

    def optimize_model(self):
        all_data = self.episode_buffer.get_data()
        state_keys = self.episode_buffer.state_keys
        num_state_keys = len(state_keys)
        state_data_np = {state_keys[i]: all_data[i] for i in range(num_state_keys)}
        actions_np, returns_np, gaes_np, logpas_np, old_values_np = all_data[num_state_keys:]
        
        device = self.device
        n_samples = len(actions_np)
        policy_cut = int(n_samples * self.policy_sample_ratio)
        value_cut = int(n_samples * self.value_sample_ratio)

        # self.batch_size = n_samples // 4

        policy_losses, value_losses, entropy_losses = [], [], []
        entropies, values_, kls, mses = [], [], [], []

        clipfracs = []

        logger.info(f'Starting model optimization with {n_samples} samples...')
        start_optimize_time = time.time()

        # =================================================================
        # Policy Optimization Loop
        # =================================================================
        policy_start_time = time.time()
        for j in range(self.policy_optimization_epochs):
            early_stop = False
            indices = np.random.permutation(policy_cut)
            for i in range(0, policy_cut, self.batch_size):
                batch_idxs = indices[i : i + self.batch_size]
                states_batch = {key: data[batch_idxs] for key, data in state_data_np.items()}
                
                actions_batch = torch.from_numpy(actions_np[batch_idxs]).to(device)
                gaes_batch = torch.from_numpy(gaes_np[batch_idxs]).to(device)
                logpas_batch = torch.from_numpy(logpas_np[batch_idxs]).to(device)

                logpas_pred, entropies_pred = self.policy_model.get_predictions(states_batch, actions_batch)
                ratios = (logpas_pred - logpas_batch).exp()

                pi_obj = gaes_batch * ratios
                pi_obj_clipped = gaes_batch * ratios.clamp(
                    1.0 - self.policy_clip_range, 1.0 + self.policy_clip_range
                )
                
                policy_loss = -torch.min(pi_obj, pi_obj_clipped).mean()
                entropy_loss = -entropies_pred.mean() * self.entropy_loss_weight
                
                self.policy_optimizer.zero_grad()
                total_policy_loss = policy_loss + entropy_loss
                total_policy_loss.backward()
                torch.nn.utils.clip_grad_norm_(self.policy_model.parameters(), self.policy_model_max_grad_norm)
                self.policy_optimizer.step()

                policy_losses.append(policy_loss.item())
                entropy_losses.append(entropy_loss.item())
                entropies.append(entropies_pred.mean().item())

                with torch.no_grad():
                    log_ratio = logpas_pred - logpas_batch
                    kl_div = 0.5 * (log_ratio ** 2).mean().item()
                    log_ratio = (logpas_pred - logpas_batch)
                kls.append(round(kl_div, 4))

                #  clipfrac ~ 0.1–0.3 is healthy.
                # If clipfrac is very high, reduce LR / epochs / clip range.
                # If near zero while KL still trips, relax KL a bit or increase epochs.
                clip_low, clip_high = 1.0 - self.policy_clip_range, 1.0 + self.policy_clip_range
                clipfrac = ( (ratios < clip_low) | (ratios > clip_high) ).float().mean().item()
                clipfracs.append(round(clipfrac, 4))
                
                if kl_div > self.policy_stopping_kl:
                    early_stop = True
                    break
            
            if early_stop:
                logger.warning(f'Early stopping policy training at epoch {j} due to KL divergence: {kl_div:.4f}')
                break
        
        logger.info(f'Epoch {j} kl {kls} clipfrac {clipfracs}')
        logger.info(f'Policy optimization finished in {time.time() - policy_start_time:.2f} seconds kl {kl_div:.4f}')
            
        # =================================================================
        # Value Optimization Loop
        # =================================================================
        value_start_time = time.time()

        for _ in range(self.value_optimization_epochs):
            early_stop = False
            indices = np.random.permutation(value_cut)
            for i in range(0, value_cut, self.batch_size):
                batch_idxs = indices[i:i + self.batch_size]

                states_batch = {
                    k: torch.from_numpy(v[batch_idxs]).to(device, non_blocking=True)
                    for k, v in state_data_np.items()
                }
                returns_batch    = torch.from_numpy(returns_np[batch_idxs]).to(device, non_blocking=True)
                old_values_batch = torch.from_numpy(old_values_np[batch_idxs]).to(device, non_blocking=True)

                # critic forward
                values_pred = self.value_model(states_batch)
                # clipping
                values_pred_clipped = old_values_batch + (values_pred - old_values_batch).clamp(
                    -self.value_clip_range, self.value_clip_range
                )

                v_loss_unclipped = (returns_batch - values_pred).pow(2)
                v_loss_clipped   = (returns_batch - values_pred_clipped).pow(2)
                value_loss = 0.5 * torch.max(v_loss_unclipped, v_loss_clipped).mean()

                self.value_optimizer.zero_grad()
                value_loss.backward()
                torch.nn.utils.clip_grad_norm_(self.value_model.parameters(), self.value_model_max_grad_norm)
                self.value_optimizer.step()

                value_losses.append(value_loss.item())
                values_.append(values_pred.mean().item())

                with torch.no_grad():
                    mse = 0.5 * (returns_batch - values_pred).pow(2).mean().item()
                mses.append(mse)

                if hasattr(self, 'value_stopping_mse') and mse < self.value_stopping_mse:
                    early_stop = True

                # Free batch tensors
                del states_batch, returns_batch, old_values_batch, values_pred, values_pred_clipped
                del v_loss_unclipped, v_loss_clipped, value_loss

                if early_stop:
                    break
            if early_stop:
                logger.warning(f'Early stopping value training due to MSE: {mse:.4f}')
                break

        logger.info(f'Value optimization finished in {time.time() - value_start_time:.2f} seconds mse {mse:.4f}')
        logger.info(f'Total optimization finished in {time.time() - start_optimize_time:.2f} seconds')
        
        return (np.mean(policy_losses), np.mean(value_losses), np.mean(entropy_losses), 
                np.mean(entropies), np.mean(values_), np.mean(kls), np.mean(mses))

    def train(self, make_envs_fn:Callable, make_env_fn:Callable, gamma, 
              max_minutes, max_episodes, goal_mean_100_reward, 
              levelBase64:str, hyper_params:str, rehearsal_level_tasks:list[list]):
        training_start, last_debug_time = time.time(), float('-inf')
        self.make_envs_fn = make_envs_fn
        self.make_env_fn = make_env_fn
        self.gamma = gamma

        SERVER_HOST = '0.0.0.0'
        SERVER_PORT = 6100

        server_listener_thread = threading.Thread(target=self.socket_server, args=(SERVER_HOST, SERVER_PORT))
        server_listener_thread.daemon = True
        server_listener_thread.start()

        self.create_dir()

        env = self.make_env_fn()
        envs = self.make_envs_fn(make_env_fn, self.n_workers, self.working_dir)

        SEEDS = (12, 34, 56, 78, 90)
        seed = random.choice(SEEDS)
        torch.manual_seed(seed) ; np.random.seed(seed) ; random.seed(seed)
    
        self.nS, nA = env.observation_space, env.action_space.n

        total_iterations= 500
        start_factor=1.0
        end_factor=1.0
        logger.info(f'scheduler lr total iteration {total_iterations} start factor {start_factor} end factor {end_factor}')

        self.policy_model = self.policy_model_fn(self.nS, nA)
        self.policy_optimizer = self.policy_optimizer_fn(self.policy_model, self.policy_optimizer_lr)

        
        # self.policy_scheduler = LinearLR(
        #     self.policy_optimizer,
        #     start_factor=start_factor,
        #     end_factor=end_factor,
        #     total_iters=total_iterations
        # )   

        self.value_model = self.value_model_fn(self.nS)
        self.value_optimizer = self.value_optimizer_fn(self.value_model, self.value_optimizer_lr)
        # self.value_scheduler = LinearLR(
        #     self.value_optimizer,
        #     start_factor=start_factor,
        #     end_factor=end_factor,
        #     total_iters=total_iterations
        # )

        end_factor = 0.5
        initial_entropy_weight = self.entropy_loss_weight

        checkpoint_path = self.find_model_file_path('checkpoint_')
        if checkpoint_path is not None:
            checkpoint = torch.load(checkpoint_path)
            logger.info("Loading model states from checkpoint.")
            self.policy_model.load_state_dict(checkpoint['policy_model_state_dict'])
            self.value_model.load_state_dict(checkpoint['value_model_state_dict'])
            if self.load_optimizer:
                logger.info("Loading optimizer states from checkpoint.")
                self.policy_optimizer.load_state_dict(checkpoint['policy_optimizer_state_dict'])
                self.value_optimizer.load_state_dict(checkpoint['value_optimizer_state_dict'])
            else:
                logger.info("Using a fresh optimizer for fine-tuning.")

            self.policy_model.train()
            self.value_model.train()

        
        statistics.write_hyperparameters(
                        self.working_dir,
                        hyper_params,
                        levelBase64,
                    )

        self.episode_buffer:EpisodeBuffer = self.episode_buffer_fn(self.nS, self.gamma, self.tau,
                                                     self.n_workers, 
                                                     self.max_buffer_episodes,
                                                     self.max_buffer_episode_steps)

        training_time = 0
        episode = 0
        evaluation_count = 0

        try:
            while True:
                try:
                    start_time = time.time()
                    episode_timestep, episode_reward, episode_exploration, \
                    episode_seconds, gaes_mean = self.episode_buffer.fill(
                        envs, self.policy_model, self.value_model, episode, 
                        levelBase64, 
                        self.max_buffer_episodes,
                        self.max_buffer_episode_steps,
                        visual=self.visual_train)
                    end_time = time.time()
                    duration = end_time - start_time
                    logger.info(f'filling buffer finished {duration:.2f} seconds')
                except Exception as e:
                     logger.error(f"{traceback.format_exc()}")
                     if evaluation_count == 0:
                        shutil.rmtree(self.working_dir)
                
                n_ep_batch = len(episode_timestep)
                policy_losses, value_losses, entropy_losses, entropies, values, kls, mses = self.optimize_model()
                #self.policy_scheduler.step()
                #self.value_scheduler.step()

                # decay_factor = evaluation_count / total_iterations
                # end_value = initial_entropy_weight * end_factor
                # self.entropy_loss_weight = initial_entropy_weight - (initial_entropy_weight - end_value) * min(1.0, decay_factor)

                # logger.info(f'policy LR: {self.policy_scheduler.get_last_lr()[0]}')
                # logger.info(f'value LR: {self.value_scheduler.get_last_lr()[0]}')
                # logger.info(f'entropy weight: {self.entropy_loss_weight}')

                

                # stats
                evaluation_count +=1
                # Evaluate over multiple episodes for stability; also get success rate (scale-invariant)
                eval_eps = 20
                if self.parallel_eval:
                    evaluation_score, success_rate, action_list = self.evaluate_parallel(
                        evaluation_count, self.policy_model, levelBase64, n_episodes=eval_eps, visual=False
                    )
                else:
                    evaluation_score, success_rate, action_list = self.evaluate(
                        evaluation_count, self.policy_model, env, levelBase64, n_episodes=eval_eps, visual=False
                    )
                logger.info('evaluation {} mean_return {} success_rate {}% values {} value losses {} entropy {}'.format(
                    evaluation_count, np.round(evaluation_score, 2), np.round(success_rate*100, 1), np.round(values, 3) ,np.round(value_losses, 3) , np.round(entropies, 3)))

                action_list_sums = [sum(ep_action) for ep_action in action_list]
                logger.info(f'action list sum {action_list_sums}')
                training_time += episode_seconds.sum()
                wallclock_time = time.time() - training_start

                stats_to_write = {
                    "episode_timestep.txt": episode_timestep,
                    "episode_reward.txt": np.round(episode_reward, 2),
                    "episode_exploration.txt": np.round(episode_exploration, 2),
                    "episode_seconds.txt": np.round(episode_seconds, 2),
                    "evaluation_score.txt": evaluation_score,
                    "evaluation_success_rate.txt": success_rate,
                    "training_time.txt": training_time,
                    "wallclock_time.txt": wallclock_time,
                    "policy_losses.txt": policy_losses,
                    "value_losses.txt": value_losses,
                    "entropy_losses.txt": entropy_losses,
                    "entropy.txt": entropies,
                    "values.txt": values,
                    "kls.txt": kls,
                    "mses.txt": mses,
                    "gaes.txt": gaes_mean,
                    #"policy_lr.txt": self.policy_scheduler.get_last_lr()[0],
                    #"value_lr.txt": self.value_scheduler.get_last_lr()[0],
                }

                for filename, value in stats_to_write.items():
                    self.write_info(self.working_dir, filename, f"{value}\n")
                
                for ep_action in action_list:
                    self.write_info(self.working_dir, "action_list.txt", f"{ep_action}\n")

               
                if evaluation_count % 50 == 0:
                    self.save_checkpoint(evaluation_count)
                
                episode += n_ep_batch
                if not self.received_data_queue.empty():
                    try:
                        addr, value = self.received_data_queue.get_nowait()
                        command = value.get('command', '')
                        if command == 'shutdown':
                            logger.warning(f'shutdown receive stop training ')
                            break
                        if command == 'save_model':
                            logger.warning('saving checkpoint {}'.format(evaluation_count))
                            self.save_checkpoint(evaluation_count)
                        elif command == 'visual_train':
                            visual_train:bool = bool(value.get('value', False))
                            self.visual_train = visual_train
                            logger.warning('visual_train set to {}'.format(self.visual_train))
                        elif command == 'update_hyperparameters':
                            parameterName = value.get('name', '')
                            parameter_name = hyper_params_mapper.get(parameterName)
                            new_value = value.get('value')
                            if parameter_name is not None and hasattr(self, parameter_name) and new_value is not None:
                                if isinstance(getattr(self, parameter_name), float):
                                    new_value = float(new_value)
                                elif isinstance(getattr(self, parameter_name), int):
                                    new_value = int(new_value)
                                setattr(self, parameter_name, new_value)
                                logger.warning(f"updated {parameter_name} to {new_value}")
                                self.write_info(self.working_dir, 'update_hyperparameters.txt', f"{parameter_name} {new_value} {evaluation_count}\n")
                            else:
                                logger.warning(f"error update variable get {parameterName} to {new_value}")
                    except queue.Empty:
                        pass
        
        except (Exception, KeyboardInterrupt) as e:
            logger.error("!An error occurred or Ctrl+C was detected! Saving progress before exiting...")
            logger.error(f"Error Message: {e}")
            logger.error(f"{traceback.format_exc()}")
        finally:
            if 'env' in locals():
                env.close()
                del env
            if 'envs' in locals():
                envs.close()
                del envs
            # Close persistent evaluation envs if created
            if self._eval_envs is not None:
                self._eval_envs.close()
                self._eval_envs = None
            if evaluation_count > 0:
                logger.info('saving checkpoint {}'.format(evaluation_count))
                self.save_checkpoint(evaluation_count)

    def handle_client(self, conn:socket.socket, addr):
        try:
            while True:
                data = conn.recv(512)
                if not data:
                    logger.info(f"Client {addr} disconnected.")
                    break
                decoded_data = data.decode('utf-8')
                json_data = json.loads(decoded_data)
                self.received_data_queue.put((addr, json_data))

        except Exception as e:
            logger.error(f"Error handling client {addr}: {e}")
        finally:
            conn.close()
            logger.info(f"Connection handler for {addr} closed.")

    def socket_server(self,host, port):
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            server_socket.bind((host, port))
            server_socket.listen(5)
            logger.info(f"Socket server listening on {host}:{port}")

            while True:
                conn, addr = server_socket.accept()
                logger.info(f"Accepted connection from {addr}")
                client_handler_thread = threading.Thread(target=self.handle_client, args=(conn, addr))
                client_handler_thread.daemon = True
                client_handler_thread.start()

        except Exception as e:
            logger.error(f"Socket server (accept loop) error: {e}")
        finally:
            server_socket.close()
            logger.info("Main socket server listener closed.")
    
    def find_model_file_path(self, start_with):
        current_dir = os.getcwd()
        for f in os.listdir(current_dir):
            if f.startswith(start_with) and f.endswith(".tar"):
                return os.path.join(current_dir, f)
        return None
    
    def create_dir(self):
        numeric_folders = sorted([int(f) for f in os.listdir('C:/thesis_data/training/') if f.isdigit()])
        if len(numeric_folders) != 0:
            latest = numeric_folders[-1]
        else:
            latest = 0
        
        working_dir = f'C:/thesis_data/training/{latest + 1}'
        if not os.path.exists(working_dir):
            logger.info('create training directory')
            os.makedirs(working_dir)
            
        self.working_dir = working_dir

    def evaluate(self, evaluation_count, eval_model:CNNActor, eval_env, level:str, n_episodes=1, greedy=True, visual=True, playMode=False) -> tuple[float, float, list]:
        rs = []
        successes = 0
        action_list = []
        for i in range(n_episodes):
            try:
                unique_episode_number = (evaluation_count - 1) * n_episodes + i + 1
                info = {"episode" : unique_episode_number, "evaluation" : True, "visual":visual, "level" : level}
                s, _  = eval_env.reset(options=info)
                d = False
                rs.append(0.0)
                action_hist = np.zeros(eval_env.action_space.n, dtype=int)
                for _ in count():
                    a = eval_model.select_greedy_action(s)
                    s, r, d, t, info = eval_env.step(a)
                    action_hist[a] += 1
                    rs[-1] += r
                    if d or t:
                        if info.get("success", False):
                            successes += 1
                        break
                action_list.append(action_hist.tolist())
            except KeyboardInterrupt:
                pass

        logger.info(f"[EVAL] action histogram: {action_hist.tolist()}")
        mean_return = float(np.mean(rs)) if len(rs) > 0 else 0.0
        success_rate = float(successes) / float(len(rs)) if len(rs) > 0 else 0.0
        logger.info(f"evaluation mean return: {mean_return:.3f}, success rate: {success_rate*100:.1f}% over {len(rs)} episodes")
        # Keep signature compatible with existing call sites; the second value is success rate
        return mean_return, success_rate, action_list

    def evaluate_parallel(self, evaluation_count:int, eval_model:CNNActor, level:str, n_episodes:int=10, visual:bool=False) -> tuple[float, float, list]:
        """Evaluate policy over n_episodes running up to n_workers concurrently.

        Episodes are launched on available workers; when a worker finishes an episode
        it is immediately reset (if more remain). This drastically reduces wall-clock
        evaluation time compared with sequential evaluation.
        """
        num_envs = min(self.n_workers, max(1, n_episodes))
        # Create persistent envs once (reuse across evaluations to keep connections open)
        if self._eval_envs is None or self._eval_envs_workers != num_envs:
            # Close existing if worker count mismatch
            if self._eval_envs is not None:
                self._eval_envs.close()
            self._eval_envs = self.make_envs_fn(self.make_env_fn, num_envs, self.working_dir)
            self._eval_envs_workers = num_envs
        eval_envs = self._eval_envs

        episode_returns = []
        action_list = []
        successes = 0

        # Per-worker trackers
        worker_returns = np.zeros(num_envs, dtype=np.float32)
        worker_action_hists = [np.zeros(eval_envs.make_env_fn().action_space.n, dtype=int) for _ in range(num_envs)]
        worker_active = [True]*num_envs

        # Unique episode numbering same as sequential version
        next_episode_number = (evaluation_count - 1) * n_episodes + 1

        # Initial reset for all workers
        obs_batch = eval_envs.reset(episodeStart=next_episode_number, ranks=range(num_envs), visual=visual,
                                    levels=[level]*num_envs, evaluation=True)

        episodes_completed = 0
        while episodes_completed < n_episodes:
                # Build action list for currently active workers
                actions = []
                for w in range(num_envs):
                    if not worker_active[w]:
                        actions.append(0)  # Dummy action (won't be used because env will be immediately reset)
                        continue
                    single_state = {k: obs_batch[k][w] for k in obs_batch}
                    a = eval_model.select_greedy_action(single_state)
                    actions.append(a)
                # Step all workers
                obs_batch, rewards, terminateds, truncateds, infos = eval_envs.step(actions)
                for w in range(num_envs):
                    if not worker_active[w]:
                        continue
                    a = actions[w]
                    worker_action_hists[w][a] += 1
                    worker_returns[w] += rewards[w]
                    done = terminateds[w] or truncateds[w]
                    if done:
                        info = infos[w] if isinstance(infos, (list, tuple)) else {}
                        if info.get("success", False):
                            successes += 1
                        # Store finished episode data
                        episode_returns.append(float(worker_returns[w]))
                        action_list.append(worker_action_hists[w].tolist())
                        episodes_completed += 1
                        # Prepare for possible next episode on this worker
                        worker_returns[w] = 0.0
                        worker_action_hists[w] = np.zeros_like(worker_action_hists[w])
                        if episodes_completed < n_episodes:
                            next_episode_number += 1
                            # Reset only this worker; compute episodeStart so resulting episode matches desired number
                            episode_start_for_reset = next_episode_number - w
                            single_obs = eval_envs.reset(episodeStart=episode_start_for_reset, ranks=[w], visual=visual,
                                                         levels=[level], evaluation=True)
                            # Inject new obs into batch
                            for k in obs_batch:
                                obs_batch[k][w] = single_obs[k][0]
                        else:
                            worker_active[w] = False
        mean_return = float(np.mean(episode_returns)) if episode_returns else 0.0
        success_rate = float(successes) / float(len(episode_returns)) if episode_returns else 0.0
        logger.info(f"[EVAL PARALLEL] mean return {mean_return:.3f} success {success_rate*100:.1f}% episodes {len(episode_returns)}")
        return mean_return, success_rate, action_list
            
    def save_checkpoint(self, evaluation_idx: int):
        checkpoint_path = os.path.join(self.working_dir, f'checkpoint_{evaluation_idx}.tar')
        torch.save({
            'policy_model_state_dict': self.policy_model.state_dict(),
            'value_model_state_dict': self.value_model.state_dict(),
            'policy_optimizer_state_dict': self.policy_optimizer.state_dict(),
            'value_optimizer_state_dict': self.value_optimizer.state_dict(),
        }, checkpoint_path)

    def play(self, make_env_fn, policy_model_fn, level):
            env = make_env_fn()
            policy_model = policy_model_fn(env.observation_space, env.action_space.n)
            checkpoint_path = self.find_model_file_path('checkpoint_')
            checkpoint_path = 'C:/thesis_data/training/18/checkpoint_100.tar'
            if checkpoint_path is not None:
                checkpoint = torch.load(checkpoint_path)
                logger.info("Loading model states from checkpoint.")
                policy_model.load_state_dict(checkpoint['policy_model_state_dict'])
                policy_model.eval()
                
            final_eval_score, score_std, _ = self.evaluate(1, policy_model, env, level, n_episodes=10000, visual=True, playMode=True)

    def write_info(self, working_dir, filename, value):
        with open(os.path.join(working_dir, filename), "a") as file:
                    file.write(value)