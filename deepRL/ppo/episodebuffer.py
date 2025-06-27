import random
import numpy as np
import torch
import time
import gc

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

        self._truncated_fn = np.vectorize(lambda x: 'TimeLimit.truncated' in x and x['TimeLimit.truncated'])
        self.discounts = np.logspace(
            0, max_episode_steps+1, num=max_episode_steps+1, base=gamma, endpoint=False, dtype=np.float64)
        self.tau_discounts = np.logspace(
            0, max_episode_steps+1, num=max_episode_steps+1, base=gamma*tau, endpoint=False, dtype=np.float64)

        device = 'cpu'
        if torch.cuda.is_available():
            device = 'cuda:0'
        self.device = torch.device(device)

        self.clear()

    def clear(self):
        grid_shape = self.state_space['grid'].shape
        vec_shape = self.state_space['vector'].shape
        self.grid_states_mem = np.empty(
            shape=(self.max_episodes, self.max_episode_steps, *grid_shape), dtype=np.uint8)
        self.vector_states_mem = np.empty(
            shape=(self.max_episodes, self.max_episode_steps, *vec_shape), dtype=np.uint8)

        self.actions_mem = np.empty(shape=(self.max_episodes, self.max_episode_steps), dtype=np.uint8)
        self.actions_mem[:] = 0

        self.returns_mem = np.empty(shape=(self.max_episodes,self.max_episode_steps), dtype=np.float32)
        self.returns_mem[:] = np.nan

        self.gaes_mem = np.empty(shape=(self.max_episodes, self.max_episode_steps), dtype=np.float32)
        self.gaes_mem[:] = np.nan

        self.logpas_mem = np.empty(shape=(self.max_episodes, self.max_episode_steps), dtype=np.float32)
        self.logpas_mem[:] = np.nan

        self.episode_steps = np.zeros(shape=(self.max_episodes), dtype=np.uint16)
        self.episode_reward = np.zeros(shape=(self.max_episodes), dtype=np.float32)
        self.episode_exploration = np.zeros(shape=(self.max_episodes), dtype=np.float32)
        self.episode_seconds = np.zeros(shape=(self.max_episodes), dtype=np.float64)

        self.current_ep_idxs = np.arange(self.n_workers, dtype=np.uint16)
        gc.collect()

    # def split_ratio(self, denominator: int):
    #     first = round(denominator * 2 / 3)   # approx 66.67%
    #     second = denominator - first         # remainder (~33.33%)
    #     return first, second

    def assign_levels(self, current_level, level_pool, n_new_level_workers, n_rehearsal_workers):
        # Create the list of levels to assign.
        levels_to_assign = []

        # 1. Add the new level for the primary workers.
        levels_to_assign.extend([current_level] * n_new_level_workers)

        # 2. Add random old levels for the rehearsal workers.
        for _ in range(n_rehearsal_workers):
            if not level_pool:
                rehearsal_level = current_level
            else:
                rehearsal_level = random.choice(level_pool)
            levels_to_assign.append(rehearsal_level)

        # Shuffle so worker order is randomized.
        random.shuffle(levels_to_assign)

        return levels_to_assign

    def fill(self, envs:MultiprocessEnv, policy_model, value_model, episodeStart,
             level_pool: list, visual: bool = True):
        
        levels_to_assign = []
        for _ in range(self.n_workers):
            levels_to_assign.append(random.choice(level_pool))
        random.shuffle(levels_to_assign)
        states = envs.reset(ranks=None, episodeStart=episodeStart, visual=visual, levels=levels_to_assign)

        worker_rewards = np.zeros(shape=(self.n_workers, self.max_episode_steps), dtype=np.float32)
        worker_exploratory = np.zeros(shape=(self.n_workers, self.max_episode_steps), dtype=np.bool_)
        worker_steps = np.zeros(shape=(self.n_workers), dtype=np.uint16)
        worker_seconds = np.array([time.time(),] * self.n_workers, dtype=np.float64)

        buffer_full = False
        # Correctly get the number of episodes already completed in the buffer
        length = len(np.where(self.episode_steps > 0)[0])
        
        while not buffer_full and length < self.max_episodes: # Simplified condition
            with torch.no_grad():
                actions, logpas, are_exploratory = policy_model.np_pass(states)
                # The model's forward pass now correctly handles the numpy dict
                values = value_model(states)

            next_states, rewards, terminals, truncated, infos = envs.step(actions)
            
            # Store the current step's data
            self.grid_states_mem[self.current_ep_idxs, worker_steps] = states['grid']
            self.vector_states_mem[self.current_ep_idxs, worker_steps] = states['vector']
            self.actions_mem[self.current_ep_idxs, worker_steps] = actions
            self.logpas_mem[self.current_ep_idxs, worker_steps] = logpas
            worker_exploratory[np.arange(self.n_workers), worker_steps] = are_exploratory
            worker_rewards[np.arange(self.n_workers), worker_steps] = rewards

            for w_idx in range(self.n_workers):
                if worker_steps[w_idx] + 1 == self.max_episode_steps:
                    terminals[w_idx] = 1
                    # Gymnasium standard is to use the info dict for this
                    infos[w_idx]['TimeLimit.truncated'] = True

            if terminals.sum() > 0:
                idx_terminals = np.flatnonzero(terminals)
                next_values = np.zeros(shape=(self.n_workers))
                
                # Check for truncated episodes to bootstrap value
                is_truncated = np.array([info.get('TimeLimit.truncated', False) for info in infos])
                if is_truncated.sum() > 0:
                    idx_truncated = np.flatnonzero(is_truncated)
                    with torch.no_grad():
                        ## FIX 1: Create a dictionary slice for the truncated states before passing to model
                        truncated_states = {key: val[idx_truncated] for key, val in next_states.items()}
                        next_values[idx_truncated] = value_model(truncated_states).cpu().numpy()

            states = next_states
            worker_steps += 1

            if terminals.sum() > 0:
                idx_terminals = np.flatnonzero(terminals)

                reset_levels = [random.choice(level_pool) for _ in idx_terminals]
                
                # envs.reset returns a dict for the new states
                new_states = envs.reset(ranks=idx_terminals, episodeStart=episodeStart, levels=reset_levels)
                
                ## FIX 2: Correctly update the states dictionary for the reset workers
                for key in states:
                    states[key][idx_terminals] = new_states[key]

                for w_idx in idx_terminals:
                    e_idx = self.current_ep_idxs[w_idx]
                    T = worker_steps[w_idx]
                    
                    self.episode_steps[e_idx] = T
                    self.episode_reward[e_idx] = worker_rewards[w_idx, :T].sum()
                    self.episode_exploration[e_idx] = worker_exploratory[w_idx, :T].mean()
                    self.episode_seconds[e_idx] = time.time() - worker_seconds[w_idx]

                    ep_rewards = np.concatenate((worker_rewards[w_idx, :T], [next_values[w_idx]]))
                    ep_discounts = self.discounts[:T+1]
                    ep_returns = np.array([np.sum(ep_discounts[:T+1-t] * ep_rewards[t:]) for t in range(T)])
                    self.returns_mem[e_idx, :T] = ep_returns

                    ## FIX 3: Retrieve both parts of the state for the completed episode
                    ep_states = {
                        'grid': self.grid_states_mem[e_idx, :T],
                        'vector': self.vector_states_mem[e_idx, :T]
                    }
                    
                    with torch.no_grad():
                        ep_values_tensors = value_model(ep_states)
                        ep_values = torch.cat((ep_values_tensors,
                                               torch.tensor([next_values[w_idx]],
                                                            device=value_model.device,
                                                            dtype=torch.float32)))
                    
                    np_ep_values = ep_values.view(-1).cpu().numpy()
                    deltas = ep_rewards[:-1] + self.gamma * np_ep_values[1:] - np_ep_values[:-1]
                    gaes = np.array([np.sum(self.tau_discounts[:T-t] * deltas[t:]) for t in range(T)])
                    self.gaes_mem[e_idx, :T] = gaes
                    
                    # Reset worker-local data
                    worker_exploratory[w_idx, :] = False
                    worker_rewards[w_idx, :] = 0
                    worker_steps[w_idx] = 0
                    worker_seconds[w_idx] = time.time()

                    # Check if buffer is full and get a new episode index
                    length += 1 # A new episode is complete
                    new_ep_id = length - 1 + self.n_workers
                    if new_ep_id >= self.max_episodes:
                        buffer_full = True
                        break 
                    self.current_ep_idxs[w_idx] = new_ep_id

        # --- Final data processing at the end of the method ---
        # (This part was correct in the previous step but is included for completeness)
        ep_idxs = self.episode_steps > 0
        ep_t = self.episode_steps[ep_idxs]

        grid_mem = [row[:ep_t[i]] for i, row in enumerate(self.grid_states_mem[ep_idxs])]
        self.grid_states_mem = np.concatenate(grid_mem)

        vector_mem = [row[:ep_t[i]] for i, row in enumerate(self.vector_states_mem[ep_idxs])]
        self.vector_states_mem = np.concatenate(vector_mem)
        
        self.actions_mem = np.concatenate([row[:ep_t[i]] for i, row in enumerate(self.actions_mem[ep_idxs])])
        self.returns_mem = torch.tensor(np.concatenate([row[:ep_t[i]] for i, row in enumerate(self.returns_mem[ep_idxs])]), device=value_model.device)
        self.gaes_mem = torch.tensor(np.concatenate([row[:ep_t[i]] for i, row in enumerate(self.gaes_mem[ep_idxs])]), device=value_model.device)
        self.logpas_mem = torch.tensor(np.concatenate([row[:ep_t[i]] for i, row in enumerate(self.logpas_mem[ep_idxs])]), device=value_model.device)

        ep_r = self.episode_reward[ep_idxs]
        ep_x = self.episode_exploration[ep_idxs]
        ep_s = self.episode_seconds[ep_idxs]
        return ep_t, ep_r, ep_x, ep_s

    def get_stacks(self):
        states_dict = {
            'grid': torch.tensor(self.grid_states_mem, device=self.device, dtype=torch.float32),
            'vector': torch.tensor(self.vector_states_mem, device=self.device, dtype=torch.float32)
        }
        return (states_dict, self.actions_mem, 
                self.returns_mem, self.gaes_mem, self.logpas_mem)

    def __len__(self):
        return self.episode_steps[self.episode_steps > 0].sum()