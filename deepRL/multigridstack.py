import gymnasium as gym
from collections import deque
import numpy as np

class MultiGridStack(gym.Wrapper):
    def __init__(self, env, num_stack: int, grid_keys: list[str]):
        super(MultiGridStack, self).__init__(env)
        self.num_stack = num_stack
        self.grid_keys = grid_keys
        self.frames = {key: deque(maxlen=num_stack) for key in grid_keys}

        new_obs_spaces = self.env.observation_space.spaces.copy()
        for key in self.grid_keys:
            grid_space = new_obs_spaces[key]
            stacked_shape = (num_stack * grid_space.shape[0],) + grid_space.shape[1:]
            new_obs_spaces[key] = gym.spaces.Box(
                low=np.min(grid_space.low),
                high=np.max(grid_space.high),
                shape=stacked_shape,
                dtype=grid_space.dtype
            )
        
        self.observation_space = gym.spaces.Dict(new_obs_spaces)
        self.last_obs = None

    def _get_stacked_obs(self):
        new_obs = self.last_obs.copy()
        for key in self.grid_keys:
            new_obs[key] = np.concatenate(list(self.frames[key]), axis=0)
        return new_obs

    def reset(self, **kwargs):
        obs, info = self.env.reset(**kwargs)
        self.last_obs = obs
        
        for key in self.grid_keys:
            self.frames[key].clear()
            for _ in range(self.num_stack):
                self.frames[key].append(obs[key])
            
        return self._get_stacked_obs(), info

    def step(self, action):
        obs, reward, terminated, truncated, info = self.env.step(action)
        self.last_obs = obs
        
        for key in self.grid_keys:
            self.frames[key].append(obs[key])
        
        return self._get_stacked_obs(), reward, terminated, truncated, info
