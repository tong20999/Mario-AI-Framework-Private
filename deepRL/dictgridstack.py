import gymnasium as gym
from collections import deque
import numpy as np

class DictGridStack(gym.Wrapper):
    def __init__(self, env, num_stack):
        super(DictGridStack, self).__init__(env)
        self.num_stack = num_stack
        self.frames = deque(maxlen=num_stack)

        # The observation space for the grid is now stacked
        grid_space = self.env.observation_space['grid']
        
        # The original shape is (1, H, W). The new shape is (num_stack, H, W).
        # We achieve this by concatenating on the first axis.
        stacked_shape = (num_stack * grid_space.shape[0],) + grid_space.shape[1:]

        # Create the new dictionary of spaces
        self.observation_space = gym.spaces.Dict({
            'grid': gym.spaces.Box(
                low=np.min(grid_space.low),
                high=np.max(grid_space.high),
                shape=stacked_shape,
                dtype=grid_space.dtype
            ),
            # The vector space remains unchanged
            'vector': self.env.observation_space['vector']
        })


    def _get_stacked_obs(self):
        """
        Takes the most recent observation dictionary and replaces its 'grid'
        value with the stacked frames.
        """
        # It's crucial that self.last_obs is the most recent, unstacked observation
        # so we can get the most recent vector data.
        new_obs = self.last_obs.copy()
        new_obs['grid'] = np.concatenate(list(self.frames), axis=0)
        return new_obs

    def reset(self, **kwargs):
        # self.env.reset() returns a tuple (obs_dict, info_dict)
        obs, info = self.env.reset(**kwargs)
        
        # Store the very first observation dictionary
        self.last_obs = obs

        # Clear the frame deque and fill it with the first frame
        self.frames.clear()
        for _ in range(self.num_stack):
            # The grid part has a channel dimension, e.g. (1, 16, 16)
            self.frames.append(obs['grid'])
            
        return self._get_stacked_obs(), info

    def step(self, action):
        # self.env.step() returns (obs_dict, reward, term, trunc, info_dict)
        obs, reward, terminated, truncated, info = self.env.step(action)
        
        # Store the latest observation before modifying it
        self.last_obs = obs
        
        # Add the new grid frame to the stack
        self.frames.append(obs['grid'])
        
        return self._get_stacked_obs(), reward, terminated, truncated, info