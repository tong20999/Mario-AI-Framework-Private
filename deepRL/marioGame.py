import struct
from typing import Any, SupportsFloat
import gymnasium as gym
from typing import Optional
import numpy as np

from socketEnv import SocketEnv

all_possible_input:list[list[bool]] = [
    # [LEFT, RIGHT , DOWN, SPEED, JUMP]
    [False, False, False, False, False], # Do nothing (reset jump)
    [False, True, False, False, False],  # move right
    [False, True, False, False, True], # move right and jump
    [False, True, False, True, False], # move right and speed
    [False, True, False, True, True],  # move right and speed and jump
     
    # [False, False, False, False, False],
    [False, False, False, False, True], # Jump only
    # [False, False, False, True, False], # fire flower only
    # [False, False, False, True, True],
    # [False, False, True, False, False],  # Duck only
    # [False, False, True, False, True], # Duck and Jump
    # [False, False, True, True, False],
    [True, False, False, False, False], # move left
    # [True, False, False, False, True], # move left and jump
    # [True, False, False, True, False], # move left and speed
    # [True, False, False, True, True],  # move left and speed and jump
]

class MarioGame(SocketEnv):
    def __init__(self, fps: int = 10):
        super(MarioGame, self).__init__()
        self.fps = fps

        # Let's use the PyTorch convention (Channels, Height, Width)
        self.grid_shape = (1, 16, 16)  # <-- CORRECT SHAPE
        self.grid_size = 1 * 16 * 16    # 256
        self.vector_size = 19

        self.observation_space = gym.spaces.Dict({
            'grid': gym.spaces.Box(low=0, high=255, shape=self.grid_shape, dtype=np.uint8),
            'vector': gym.spaces.Box(low=0, high=255, shape=(self.vector_size,), dtype=np.uint8)
        })

        self.action_space = gym.spaces.Discrete(len(all_possible_input))

    def _parse_observation(self, obs_bytes: bytes) -> dict:
        """Helper function to parse the flat byte array into a structured dict."""
        grid_bytes = obs_bytes[:self.grid_size]
        vector_bytes = obs_bytes[self.grid_size:]

        # Reshape grid directly to the correct (C, H, W) shape
        grid = np.frombuffer(grid_bytes, dtype=np.uint8).reshape(self.grid_shape)
        vector = np.frombuffer(vector_bytes, dtype=np.uint8)

        return {'grid': grid, 'vector': vector}

    def _map_action(self, action:int) -> bytes:
        select_action = all_possible_input[action]
        return bytes(select_action)

    def _receive_reset(self) -> dict:
        data = self._receive_fixed(1024)
        op_code = data[:2].decode('utf-8')
        # The total observation size is the grid size + vector size
        payload = data[2:2 + self.grid_size + self.vector_size]
        assert op_code == '01'
        return self._parse_observation(payload)
    
    def _receive_step(self) -> tuple[dict, SupportsFloat, bool, bool, dict[str, Any]]: # <-- Return type is now dict
        data = self._receive_fixed(1024)
        op_code = data[:2].decode('utf-8')
        payload = data[2:]
        assert op_code == '02'
        
        reward_byte = payload[0:4]
        reward: float = struct.unpack('>f', reward_byte)[0]
        terminated = True if payload[4] else False
        
        obs_bytes = payload[5:5 + self.grid_size + self.vector_size]
        observation = self._parse_observation(obs_bytes)
        
        return observation, reward, terminated, False, {}
        
    def reset(self, seed: Optional[int] = None, options: Optional[dict] = None) -> dict: # <-- Return type is now dict
        episode = options.get("episode") if options else 0
        evaluation = options.get("evaluation") if options else False
        epsilon = options.get("epsilon") if options else 0
        payload = struct.pack('>if?', episode, epsilon, evaluation)
        self._send_operation('01', payload)
        observation = self._receive_reset()
        return observation
    
    def step(self, action:int | np.int64) -> tuple[dict, SupportsFloat, bool, bool, dict[str, Any]]:
        if type(action) == int:
            value = action
        else:
            value = np.int64(action).item()
        
        payload = self._map_action(value)
        self._send_operation('02', payload)
        new_state, reward, terminated, truncated, info = self._receive_step()
        return new_state, reward, terminated, truncated, info