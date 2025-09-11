import gymnasium as gym
from collections import deque
import numpy as np

class MultiGridStack(gym.Wrapper):
    def __init__(self, env, num_stack: int):
        super(MultiGridStack, self).__init__(env)
        self.num_stack = num_stack
        self.grid_key = 'grid'
        self.vector_key = 'vector'
        
        self.grid_frames = deque(maxlen=num_stack)
        new_obs_spaces = self.env.observation_space.spaces.copy()
        
        grid_space = new_obs_spaces[self.grid_key]

        new_shape = (num_stack, *grid_space.shape) # Assumes grid shape is (C, H, W)
        
        new_obs_spaces[self.grid_key] = gym.spaces.Box(
            low=0,
            high=255, # A safe upper bound for uint8
            shape=new_shape,
            dtype=grid_space.dtype
        )
        
        self.observation_space = gym.spaces.Dict(new_obs_spaces)

    def _get_stacked_obs(self, last_vector_obs):
        return {
            self.grid_key: np.array(self.grid_frames, dtype=self.observation_space[self.grid_key].dtype),
            self.vector_key: last_vector_obs
        }

    def reset(self, **kwargs):
        obs, info = self.env.reset(**kwargs)
        grid_obs = obs[self.grid_key]
        vector_obs = obs[self.vector_key]
        
        self.grid_frames.clear()
        for _ in range(self.num_stack):
            self.grid_frames.append(grid_obs)
            
        return self._get_stacked_obs(vector_obs), info

    def step(self, action):
        obs, reward, terminated, truncated, info = self.env.step(action)
        grid_obs = obs[self.grid_key]
        vector_obs = obs[self.vector_key]
        self.grid_frames.append(grid_obs)
        
        return self._get_stacked_obs(vector_obs), reward, terminated, truncated, info