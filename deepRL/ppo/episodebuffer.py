import random
import numpy as np
import torch
import time
import gc
from scipy.signal import lfilter

from ppo.cnn import CNNActor, CNNCritic
from ppo.multiprocessenv import MultiprocessEnv

class EpisodeBuffer():
    def __init__(self,
                 state_dim,
                 gamma,
                 tau,
                 n_workers,
                 max_episodes,
                 max_episode_steps):
        
        assert max_episodes >= n_workers

        self.state_space = state_dim
        self.gamma = gamma
        self.tau = tau
        self.n_workers = n_workers
        self.max_episodes = max_episodes
        self.max_episode_steps = max_episode_steps


        self.clear()

    def clear(self):
        scene_shape = self.state_space['gridScene'].shape
        enemy_shape = self.state_space['gridEnemies'].shape
        vec_shape = self.state_space['vector'].shape

        self.grid_scene_states_mem = np.empty(
            shape=(self.max_episodes, self.max_episode_steps, *scene_shape), dtype=np.uint8)
        self.grid_enemies_states_mem = np.empty(
            shape=(self.max_episodes, self.max_episode_steps, *enemy_shape), dtype=np.uint8)
        self.vector_states_mem = np.empty(
            shape=(self.max_episodes, self.max_episode_steps, *vec_shape), dtype=np.uint8)

        self.actions_mem = np.empty(shape=(self.max_episodes, self.max_episode_steps), dtype=np.uint8)
        self.values_mem = np.empty(shape=(self.max_episodes, self.max_episode_steps), dtype=np.float32)
        self.returns_mem = np.empty(shape=(self.max_episodes,self.max_episode_steps), dtype=np.float32)
        self.gaes_mem = np.empty(shape=(self.max_episodes, self.max_episode_steps), dtype=np.float32)
        self.logpas_mem = np.empty(shape=(self.max_episodes, self.max_episode_steps), dtype=np.float32)
        self.episode_steps = np.zeros(shape=(self.max_episodes), dtype=np.uint16)
        self.episode_reward = np.zeros(shape=(self.max_episodes), dtype=np.float32)
        self.episode_exploration = np.zeros(shape=(self.max_episodes), dtype=np.float32)
        self.episode_seconds = np.zeros(shape=(self.max_episodes), dtype=np.float64)

        self.current_ep_idxs = np.arange(self.n_workers, dtype=np.uint16)
        gc.collect()

    def fill(self, envs:MultiprocessEnv, policy_model:CNNActor, value_model:CNNCritic, episodeStart:int,
             level_pool: list[str], 
             rehearsal_level_tasks: list[list[str]],
             mode:str = 'train',
             visual: bool = True):
        
        rehearsal_value =  self.n_workers // 3
        
        workers = self.n_workers - rehearsal_value
        rehearsal_workers = rehearsal_value

        if mode == 'ewc' or len(rehearsal_level_tasks) == 0:
            rehearsal_workers = 0
            workers = self.n_workers
            
        levels_to_assign = []
        for _ in range(rehearsal_workers):
            task = random.choice(rehearsal_level_tasks)
            rehearsal_level = random.choice(task)
            levels_to_assign.append(rehearsal_level)
        
        for _ in range(workers):
            levels_to_assign.append(random.choice(level_pool))
        random.shuffle(levels_to_assign)
        states = envs.reset(episodeStart, ranks=None, visual=visual, levels=levels_to_assign)

        worker_rewards = np.zeros(shape=(self.n_workers, self.max_episode_steps), dtype=np.float32)
        worker_exploratory = np.zeros(shape=(self.n_workers, self.max_episode_steps), dtype=np.bool_)
        worker_steps = np.zeros(shape=(self.n_workers), dtype=np.uint16)
        worker_seconds = np.array([time.time(),] * self.n_workers, dtype=np.float64)

        buffer_full = False
        length = len(np.where(self.episode_steps > 0)[0])
        
        while not buffer_full and length < self.max_episodes:
            with torch.no_grad():
                actions, logpas, are_exploratory = policy_model.np_pass(states)
                values:torch.Tensor = value_model(states)

            next_states, rewards, terminals, truncateds, _ = envs.step(actions)
            
            self.values_mem[self.current_ep_idxs, worker_steps] = values.cpu().numpy()
            self.grid_scene_states_mem[self.current_ep_idxs, worker_steps] = states['gridScene']
            self.grid_enemies_states_mem[self.current_ep_idxs, worker_steps] = states['gridEnemies']
            self.vector_states_mem[self.current_ep_idxs, worker_steps] = states['vector']
            self.actions_mem[self.current_ep_idxs, worker_steps] = actions
            self.logpas_mem[self.current_ep_idxs, worker_steps] = logpas
            worker_exploratory[np.arange(self.n_workers), worker_steps] = are_exploratory
            worker_rewards[np.arange(self.n_workers), worker_steps] = rewards

            for w_idx in range(self.n_workers):
                if worker_steps[w_idx] + 1 == self.max_episode_steps:
                    terminals[w_idx] = 1
                    truncateds[w_idx] = 1
            
            states = next_states
            worker_steps += 1

            dones = terminals | truncateds

            if dones.sum() > 0:
                with torch.no_grad():
                    next_values = np.zeros(self.n_workers)
                    idx_truncated = np.flatnonzero(truncateds)
                    if len(idx_truncated) > 0:
                        truncated_states = {key: val[idx_truncated] for key, val in next_states.items()}
                        next_values[idx_truncated] = value_model(truncated_states).cpu().numpy()

                idx_dones = np.flatnonzero(dones)
                reset_levels = [random.choice(level_pool) for _ in idx_dones]
                episodeStart += dones.sum()
                new_states = envs.reset(episodeStart, ranks=idx_dones, levels=reset_levels, visual=visual)
                
                for key in states:
                    states[key][idx_dones] = new_states[key]

                for w_idx in idx_dones:
                    e_idx = self.current_ep_idxs[w_idx]
                    T = worker_steps[w_idx]
                    
                    self.episode_steps[e_idx] = T
                    self.episode_reward[e_idx] = worker_rewards[w_idx, :T].sum()
                    self.episode_exploration[e_idx] = worker_exploratory[w_idx, :T].mean()
                    self.episode_seconds[e_idx] = time.time() - worker_seconds[w_idx]
                    
                    ep_rewards = worker_rewards[w_idx, :T]
                    ep_values = self.values_mem[e_idx, :T]
                    v_t_plus_1 = np.append(ep_values[1:], next_values[w_idx])
                    
                    deltas = ep_rewards + self.gamma * v_t_plus_1 - ep_values
                    
                    discount_factor = self.gamma * self.tau
                    gaes = lfilter([1], [1, -discount_factor], deltas[::-1], axis=0)[::-1]
                    returns = gaes + ep_values

                    self.gaes_mem[e_idx, :T] = gaes.copy()
                    self.returns_mem[e_idx, :T] = returns.copy()

                    worker_exploratory[w_idx, :] = False
                    worker_rewards[w_idx, :] = 0
                    worker_steps[w_idx] = 0
                    worker_seconds[w_idx] = time.time()

                    length += 1
                    new_ep_id = length - 1 + self.n_workers
                    if new_ep_id >= self.max_episodes:
                        buffer_full = True
                        break 
                    self.current_ep_idxs[w_idx] = new_ep_id

        ep_idxs = self.episode_steps > 0
        ep_t = self.episode_steps[ep_idxs]

        scene_mem = [row[:ep_t[i]] for i, row in enumerate(self.grid_scene_states_mem[ep_idxs])]
        self.grid_scene_states_mem = np.concatenate(scene_mem)

        enemy_mem = [row[:ep_t[i]] for i, row in enumerate(self.grid_enemies_states_mem[ep_idxs])]
        self.grid_enemies_states_mem = np.concatenate(enemy_mem)

        vector_mem = [row[:ep_t[i]] for i, row in enumerate(self.vector_states_mem[ep_idxs])]
        self.vector_states_mem = np.concatenate(vector_mem)
        
        self.actions_mem = np.concatenate([row[:ep_t[i]] for i, row in enumerate(self.actions_mem[ep_idxs])])
        self.returns_mem = np.concatenate([row[:ep_t[i]] for i, row in enumerate(self.returns_mem[ep_idxs])])
        self.gaes_mem = np.concatenate([row[:ep_t[i]] for i, row in enumerate(self.gaes_mem[ep_idxs])])
        self.logpas_mem = np.concatenate([row[:ep_t[i]] for i, row in enumerate(self.logpas_mem[ep_idxs])])

        ep_r = self.episode_reward[ep_idxs]
        ep_x = self.episode_exploration[ep_idxs]
        ep_s = self.episode_seconds[ep_idxs]
        return ep_t, ep_r, ep_x, ep_s

    def get_data(self):
        return (
           self.grid_scene_states_mem,
           self.grid_enemies_states_mem,
           self.vector_states_mem,
           self.actions_mem,
           self.returns_mem,
           self.gaes_mem,
           self.logpas_mem,
       )

    def __len__(self):
        return self.episode_steps[self.episode_steps > 0].sum()