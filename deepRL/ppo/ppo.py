import logging
import random
import matplotlib
import rlstatistics as statistics
from logger import setup_logging
matplotlib.use('TkAgg')
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
import traceback
import shutil
from IPython import display
from torch.utils.data import TensorDataset

from ppo.cnn import CNNActor
from ppo.custom_dataset import CustomDictDataset
from ppo.episodebuffer import EpisodeBuffer
from ppo.ewc import EWC
import socket
import threading
import time
import queue

logger = logging.getLogger('Agent:PPO')

LEAVE_PRINT_EVERY_N_SECS = 300
ERASE_LINE = '\x1b[2K'
EPS = 1e-6

fig, ax = plt.subplots()

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
        
        self.evaluatationCount = 0

        self.ewc_fn = ewc_fn
        self.ewc_lambda = ewc_lambda

        self.episode_buffer_fn = episode_buffer_fn
        self.max_buffer_episodes = max_buffer_episodes
        self.max_buffer_episode_steps = max_buffer_episode_steps

        self.entropy_loss_weight = entropy_loss_weight
        self.tau = tau
        self.n_workers = n_workers
        self.received_data_queue = queue.Queue()

    def optimize_model(self):
        policy_losses = []
        value_losses = []
        entropy_losses = []
        values_ = []
        entropies = []
        kls = []
        mses = []
        states, actions_np, returns, gaes, logpas = self.episode_buffer.get_stacks()
        device = self.device
        actions = torch.from_numpy(actions_np).to(device)
        with torch.no_grad():
            values = self.value_model(states).detach()
        gaes = (gaes - gaes.mean()) / (gaes.std() + EPS)
        n_samples = len(actions)
        policy_optimize_samples = int(n_samples * self.policy_sample_ratio)
        value_optimize_samples = int(n_samples * self.value_sample_ratio)
        logger.info(f'start optimize model samples {policy_optimize_samples},{value_optimize_samples} batch size {self.batch_size}')
        start_optimize_time = time.time()
        logger.info(f'start optimize policy model')
        start_time = time.time()
        for _ in range(self.policy_optimization_epochs):
            early_stop = False
            indices = torch.randperm(policy_optimize_samples, device=actions.device)

            for i in range(0, policy_optimize_samples, self.batch_size):
                if not self.received_data_queue.empty():
                    try:
                        addr, value = self.received_data_queue.get_nowait()
                        raise Exception('signal stop receive')
                    except queue.Empty:
                        pass

                batch_idxs = indices[i:i + self.batch_size]
                states_batch = {key: val[batch_idxs] for key, val in states.items()}
                actions_batch = actions[batch_idxs]
                gaes_batch = gaes[batch_idxs]
                logpas_batch = logpas[batch_idxs]
                logpas_pred, entropies_pred = self.policy_model.get_predictions(states_batch, actions_batch)
                entropies.append(entropies_pred.mean().item())

                ratios = (logpas_pred - logpas_batch).exp()
                pi_obj = gaes_batch * ratios
                pi_obj_clipped = gaes_batch * ratios.clamp(1.0 - self.policy_clip_range,
                                                        1.0 + self.policy_clip_range)
                policy_loss = -torch.min(pi_obj, pi_obj_clipped).mean()
                entropy_loss = -entropies_pred.mean() * self.entropy_loss_weight
                
                policy_losses.append(policy_loss.item())
                entropy_losses.append(entropy_loss.item())

                ewc_penalty = self.ewc.penalty()

                self.policy_optimizer.zero_grad()
                total_policy_loss = policy_loss + entropy_loss + ewc_penalty
                total_policy_loss.backward()
                torch.nn.utils.clip_grad_norm_(self.policy_model.parameters(), 
                                            self.policy_model_max_grad_norm)
                self.policy_optimizer.step()

                with torch.no_grad():
                    kl = (logpas_batch - logpas_pred).mean()
                    kls.append(kl.item())
                    if kl.item() > self.policy_stopping_kl:
                        early_stop = True
                        break
            
            end_time = time.time()

            if early_stop:
                break
        duration = end_time - start_time
        logger.info(f'optimize policy model finished {duration:.2f} seconds')
        logger.info(f'start optimize value model')
        start_time = time.time()
        for _ in range(self.value_optimization_epochs):
            early_stop = False
            indices = torch.randperm(value_optimize_samples, device=actions.device)

            for i in range(0, value_optimize_samples, self.batch_size):
                if not self.received_data_queue.empty():
                    try:
                        addr, value = self.received_data_queue.get_nowait()
                        raise Exception('signal stop receive')
                    except queue.Empty:
                        pass

                batch_idxs = indices[i:i + self.batch_size]
                states_batch = {key: val[batch_idxs] for key, val in states.items()}
                returns_batch = returns[batch_idxs]
                values_batch = values[batch_idxs]

                values_pred = self.value_model(states_batch)
                values_.append(values_pred.mean().item())
                
                values_pred_clipped = values_batch + (values_pred - values_batch).clamp(
                    -self.value_clip_range, self.value_clip_range
                )
                v_loss = (returns_batch - values_pred).pow(2)
                v_loss_clipped = (returns_batch - values_pred_clipped).pow(2)
                value_loss = torch.max(v_loss, v_loss_clipped).mul(0.5).mean()
                value_losses.append(value_loss.item())
                
                self.value_optimizer.zero_grad()
                value_loss.backward()
                torch.nn.utils.clip_grad_norm_(self.value_model.parameters(),
                                            self.value_model_max_grad_norm)
                self.value_optimizer.step()

                with torch.no_grad():
                    mse = (values_batch - values_pred).pow(2).mul(0.5).mean()
                    mses.append(mse.item())
                    if hasattr(self, 'value_stopping_mse') and mse.item() > self.value_stopping_mse:
                        early_stop = True
                        break
            
            end_time = time.time()
           
            
            if early_stop:
                break
        duration = end_time - start_time
        logger.info(f'optimize value model finish {duration:.2f} seconds')
        duration_optinize = end_time - start_optimize_time       
        logger.info(f'optimize model finish {duration_optinize:.2f} seconds')
        return np.mean(policy_losses), np.mean(value_losses), np.mean(entropy_losses), np.mean(entropies), np.mean(values_), np.mean(kls), np.mean(mses)

    def train(self, make_envs_fn:Callable, make_env_fn:Callable, gamma, 
              max_minutes, max_episodes, goal_mean_100_reward, 
              level_pool:list, rehearsal_level_tasks:list[list],
              evaluation_levels:list[str]):
        training_start, last_debug_time = time.time(), float('-inf')
        self.batch_size = 128
        self.make_envs_fn = make_envs_fn
        self.make_env_fn = make_env_fn
        self.gamma = gamma

        SERVER_HOST = '0.0.0.0'
        SERVER_PORT = 6100

        server_listener_thread = threading.Thread(target=self.socket_server, args=(SERVER_HOST, SERVER_PORT))
        server_listener_thread.daemon = True
        server_listener_thread.start()

        env = self.make_env_fn()
        envs = self.make_envs_fn(make_env_fn, self.n_workers)

        SEEDS = (12, 34, 56, 78, 90)
        seed = random.choice(SEEDS)
        torch.manual_seed(seed) ; np.random.seed(seed) ; random.seed(seed)
    
        self.nS, nA = env.observation_space, env.action_space.n

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
            ewc_state = torch.load(ewc_state_path, map_location=self.policy_model.device, weights_only=True)
            self.ewc.fisher_matrix = ewc_state.get('fisher', self.ewc.create_empty_clone())
            self.ewc.optimal_params = ewc_state.get('params', {}) # Params can start as empty dict

        self.episode_buffer:EpisodeBuffer = self.episode_buffer_fn(self.nS, self.gamma, self.tau,
                                                     self.n_workers, 
                                                     self.max_buffer_episodes,
                                                     self.max_buffer_episode_steps)

        training_time = 0
        episode = 0
        evaluation_count = 0
        self.create_dir(level_pool)
        self.write_statistic(level_pool, rehearsal_level_tasks, evaluation_levels)
        try:
            while True:
                try:
                    logger.info('start filling buffer')
                    start_time = time.time()
                    episode_timestep, episode_reward, episode_exploration, \
                    episode_seconds = self.episode_buffer.fill(
                        envs, self.policy_model, self.value_model, episode, 
                        level_pool, 
                        rehearsal_level_tasks,
                        visual=False)
                    end_time = time.time()
                    duration = end_time - start_time
                    logger.info(f'filling buffer finished {duration:.2f} seconds')
                except Exception as e:
                     logger.error(f"{traceback.format_exc()}")
                     if evaluation_count == 0:
                        shutil.rmtree(self.working_dir)
                
                n_ep_batch = len(episode_timestep)
                policy_losses, value_losses, entropy_losses, entropies, values, kls, mses = self.optimize_model()
                self.episode_buffer.clear()
                torch.cuda.empty_cache()

                # stats
                evaluation_score, _ = self.evaluate(self.policy_model, env, random.choice(evaluation_levels))
                evaluation_count +=1
                logger.info('evaluation {} score {} value losses {}'.format(evaluation_count, np.round(evaluation_score, 2), np.round(value_losses, 2)))
                
                    
                training_time += episode_seconds.sum()
                wallclock_time = time.time() - training_start

                stats_to_write = {
                    "episode_timestep.txt": episode_timestep,
                    "episode_reward.txt": np.round(episode_reward, 2),
                    "episode_exploration.txt": np.round(episode_exploration, 2),
                    "episode_seconds.txt": np.round(episode_seconds, 2),
                    "evaluation_score.txt": evaluation_score,
                    "training_time.txt": training_time,
                    "wallclock_time.txt": wallclock_time,
                    "policy_losses.txt": policy_losses,
                    "value_losses.txt": value_losses,
                    "entropy_losses.txt": entropy_losses,
                    "entropy.txt": entropies,
                    "values.txt": values,
                    "kls.txt": kls,
                    "mses.txt": mses
                }

                for filename, value in stats_to_write.items():
                    self.write_info(self.working_dir, filename, f"{value}\n")

               
                if evaluation_count % 1000 == 0:
                    self.save_checkpoint(evaluation_count, self.policy_model, 'policy')
                    self.save_checkpoint(evaluation_count, self.value_model, 'value')
                
                episode += n_ep_batch
                if not self.received_data_queue.empty():
                    try:
                        addr, value = self.received_data_queue.get_nowait()
                        break
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
            if evaluation_count > 0:
                logger.info('saving policy model {}'.format(evaluation_count))
                self.save_checkpoint(evaluation_count, self.policy_model, 'policy')
                logger.info('saving value model {}'.format(evaluation_count))
                self.save_checkpoint(evaluation_count, self.value_model, 'value')
                logger.info('saving ewc model {}'.format(evaluation_count))
                self.save_ewc(level_pool, rehearsal_level_tasks)

    def handle_client(self, conn:socket.socket, addr):
        try:
            while True:
                data = conn.recv(64)
                if not data:
                    logger.info(f"Client {addr} disconnected.")
                    break
                decoded_data = data.decode('utf-8')
                logger.info(f"Received from {addr}: {decoded_data}")
                self.received_data_queue.put((addr, decoded_data)) # Put data (with client address) into the queue

        except Exception as e:
            logger.error(f"Error handling client {addr}: {e}")
        finally:
            conn.close() # Ensure the client socket is closed
            logger.info(f"Connection handler for {addr} closed.")

    def socket_server(self,host, port):
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1) # Allow re-use of address
        try:
            server_socket.bind((host, port))
            server_socket.listen(5) # Max 5 queued connections
            logger.info(f"Socket server listening on {host}:{port}")

            while True:
                conn, addr = server_socket.accept() # This blocks until a new client connects
                logger.info(f"Accepted connection from {addr}")
                # Start a new thread to handle this client
                client_handler_thread = threading.Thread(target=self.handle_client, args=(conn, addr))
                client_handler_thread.daemon = True # Allows main program to exit even if client threads are running
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
    
    def create_dir(self, level_pool):
        root_dir = 'C:/thesis_data/{}'.format(level_pool[0])
        if not os.path.exists(root_dir):
            logger.info('create training directory')
            os.makedirs(root_dir)
            
        subfolders = [name for name in os.listdir(root_dir) if os.path.isdir(os.path.join(root_dir, name))]
        numbers = [int(name) for name in subfolders if name.isdigit()]
        latest = -1
        if numbers:
            latest = max(numbers)
            working_dir = os.path.join(root_dir, str(latest + 1))
        else:
            working_dir = os.path.join(root_dir, '1')

        if not os.path.exists(working_dir):
            logger.info('create training number directory')
            os.makedirs(working_dir)
        self.working_dir = working_dir

    def write_statistic(self, level_pool, rehearsal_level_tasks, evaluation_levels):
        statistics.write_hyperparameters(
                        self.working_dir,
                                self.policy_optimizer_lr,
                                self.policy_optimization_epochs,
                                self.policy_sample_ratio,
                                self.policy_clip_range,
                                self.policy_stopping_kl,
                                self.value_optimizer_lr,
                                self.value_optimization_epochs,
                                self.value_clip_range,
                                self.value_stopping_mse,
                                self.ewc_lambda,
                                self.max_buffer_episodes,
                                self.max_buffer_episode_steps,
                                self.entropy_loss_weight,
                                self.tau,
                                self.n_workers,
                                level_pool,
                                evaluation_levels,
                                rehearsal_level_tasks
                    )

    def evaluate(self, eval_model:CNNActor, eval_env, level:str, n_episodes=1, greedy=True, visual=True):
        rs = []
        for _ in range(n_episodes):
            try:
                info = {"episode" : self.evaluatationCount, "evaluation" : True, "visual":visual, "level" : level}
                self.evaluatationCount += 1
                s, _  = eval_env.reset(options=info)
                d = False
                rs.append(0)
                for _ in count():
                    if greedy:
                        a = eval_model.select_greedy_action(s)
                    else: 
                        a = eval_model.select_action(s)
                    s, r, d, t, _ = eval_env.step(a)
                    rs[-1] += r
                    if d or t: break
            except KeyboardInterrupt:
                pass
        return np.mean(rs), np.std(rs)

    def finish_task(self, level_pool: list, rehearsal_level_tasks: list[list]):
        temp_buffer:EpisodeBuffer = self.episode_buffer_fn(
            self.nS,
            self.gamma,
            self.tau,
            self.n_workers,
            self.max_buffer_episodes,
            self.max_buffer_episode_steps
        )
        
        envs = self.make_envs_fn(self.make_env_fn, self.n_workers)
        temp_buffer.fill(envs, self.policy_model, self.value_model, 
                        episodeStart=0,
                        level_pool=level_pool,
                        rehearsal_level_tasks=rehearsal_level_tasks,
                        mode='ewc',
                        visual=False)
        envs.close()
        
        states, actions, _, _, _ = temp_buffer.get_stacks()
        
        dataset = CustomDictDataset(
            states_dict=states, 
            actions_tensor=actions
        )
        
        self.ewc.register_task(dataset)

    def save_checkpoint(self, evaluation_idx, model, suffix):
            torch.save(model.state_dict(), 
                            os.path.join(self.working_dir, 'model.{}.{}.tar'.format(suffix, evaluation_idx)))
            
    def save_ewc(self, level_pool, rehearsal_level_tasks):
        self.finish_task(level_pool=level_pool, rehearsal_level_tasks=rehearsal_level_tasks)

        ewc_state = {
            'fisher': self.ewc.fisher_matrix,
            'params': self.ewc.optimal_params
        }
        
        save_path = os.path.join(self.working_dir, 'model.ewc_state.tar')
        torch.save(ewc_state, save_path)

    def play(self, make_env_fn, policy_model_fn, level):
            env = make_env_fn()
            policy_model = policy_model_fn(env.observation_space, env.action_space.n)
            policy_model_state = self.find_model_file_path('model.policy')
            if policy_model_state is not None:
                policy_model.load_state_dict(torch.load(policy_model_state, weights_only=True))
                policy_model.eval()
                
            final_eval_score, score_std = self.evaluate(policy_model, env, level, n_episodes=100, visual=True)

    def write_info(self, working_dir, filename, value):
        with open(os.path.join(working_dir, filename), "a") as file:
                    file.write(value)