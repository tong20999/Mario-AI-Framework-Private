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
        
        # --- This part correctly rebuilds the observation space ---
        new_obs_spaces = self.env.observation_space.spaces.copy()
        grid_space = new_obs_spaces[self.grid_key]
        
        # Correctly creates the new shape e.g., (4, 20, 16, 16)
        new_shape = (num_stack, *grid_space.shape) 
        
        new_obs_spaces[self.grid_key] = gym.spaces.Box(
            low=0,
            high=1, # The one-hot grid only contains 0s and 1s.
            shape=new_shape,
            dtype=grid_space.dtype
        )
        
        self.observation_space = gym.spaces.Dict(new_obs_spaces)

    def _get_stacked_obs(self, last_vector_obs):
        # --- This correctly assembles the final observation dict ---
        return {
            self.grid_key: np.array(self.grid_frames, dtype=self.observation_space[self.grid_key].dtype),
            self.vector_key: last_vector_obs
        }

    def reset(self, **kwargs):
        obs, info = self.env.reset(**kwargs)
        grid_obs = obs[self.grid_key]
        vector_obs = obs[self.vector_key]
        
        # --- Correctly initializes the stack by repeating the first frame ---
        self.grid_frames.clear()
        for _ in range(self.num_stack):
            self.grid_frames.append(grid_obs)
            
        return self._get_stacked_obs(vector_obs), info

    def step(self, action):
        obs, reward, terminated, truncated, info = self.env.step(action)
        grid_obs = obs[self.grid_key]
        vector_obs = obs[self.vector_key]
        
        # --- Correctly adds the latest frame to the stack ---
        self.grid_frames.append(grid_obs)
        
        return self._get_stacked_obs(vector_obs), reward, terminated, truncated, info