import gymnasium as gym
from collections import deque
import numpy as np

class DictGridStack(gym.Wrapper):
    def __init__(self, env, num_stack):
        super(DictGridStack, self).__init__(env)
        self.num_stack = num_stack
        self.frames = deque(maxlen=num_stack)

        grid_space = self.env.observation_space['grid']
        
        stacked_shape = (num_stack * grid_space.shape[0],) + grid_space.shape[1:]

        self.observation_space = gym.spaces.Dict({
            'grid': gym.spaces.Box(
                low=np.min(grid_space.low),
                high=np.max(grid_space.high),
                shape=stacked_shape,
                dtype=grid_space.dtype
            ),
            'vector': self.env.observation_space['vector']
        })


    def _get_stacked_obs(self):
        new_obs = self.last_obs.copy()
        new_obs['grid'] = np.concatenate(list(self.frames), axis=0)
        return new_obs

    def reset(self, **kwargs):
        obs, info = self.env.reset(**kwargs)
        self.last_obs = obs
        self.frames.clear()
        for _ in range(self.num_stack):
            self.frames.append(obs['grid'])
            
        return self._get_stacked_obs(), info

    def step(self, action):
        obs, reward, terminated, truncated, info = self.env.step(action)
        self.last_obs = obs
        self.frames.append(obs['grid'])
        
        return self._get_stacked_obs(), reward, terminated, truncated, info