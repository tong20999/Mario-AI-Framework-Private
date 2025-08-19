import gymnasium as gym
from collections import deque
import numpy as np

class MultiGridStack(gym.Wrapper):
    def __init__(self, env, num_stack: int):
        super(MultiGridStack, self).__init__(env)
        self.num_stack = num_stack
        self.keys = list(env.observation_space.spaces.keys())
        self.frames = {key: deque(maxlen=num_stack) for key in self.keys}

        new_obs_spaces = self.env.observation_space.spaces.copy()
        
        for key in self.keys:
            space = new_obs_spaces[key]
            stack_axis = 0 if len(space.shape) > 1 else 0
            new_shape = list(space.shape)
            new_shape[stack_axis] *= num_stack
            
            new_obs_spaces[key] = gym.spaces.Box(
                low=np.min(space.low),
                high=np.max(space.high),
                shape=tuple(new_shape),
                dtype=space.dtype
            )
        
        self.observation_space = gym.spaces.Dict(new_obs_spaces)
        self.last_obs = None

    def _get_stacked_obs(self):
        new_obs = self.last_obs.copy()
        for key in self.keys:
            axis = 0
            new_obs[key] = np.concatenate(list(self.frames[key]), axis=axis)
        return new_obs

    def reset(self, **kwargs):
        obs, info = self.env.reset(**kwargs)
        self.last_obs = obs
        
        for key in self.keys:
            self.frames[key].clear()
            for _ in range(self.num_stack):
                self.frames[key].append(obs[key])
            
        return self._get_stacked_obs(), info

    def step(self, action):
        obs, reward, terminated, truncated, info = self.env.step(action)
        self.last_obs = obs
        
        for key in self.keys:
            self.frames[key].append(obs[key])
        
        return self._get_stacked_obs(), reward, terminated, truncated, info
