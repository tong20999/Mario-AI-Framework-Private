import struct
from typing import Any, SupportsFloat, Optional
import gymnasium as gym
import numpy as np

# Assuming socketEnv.py is in the same directory.
from socketEnv import SocketEnv

payload_size = 1024

all_possible_input:list[list[bool]] = [
    # [LEFT, RIGHT , DOWN, SPEED, JUMP]
    [False, False, False, False, False], # Do nothing
    [False, True, False, False, False],  # move right
    [False, True, False, False, True], # move right and jump
    [False, True, False, True, False], # move right and speed
    [False, True, False, True, True],  # move right and speed and jump
    [False, False, False, False, True], # Jump only
    [True, False, False, False, False], # move left
    [True, False, False, False, True], # move left and jump
    [True, False, False, True, False],  # move left and speed
    [True, False, False, True, True],  # move left and speed and jump
]

class MarioGame(SocketEnv):
    def __init__(self, fps: int = 10):
        super(MarioGame, self).__init__()
        self.fps = fps
        
        self.channel_count = 1
        self.grid_h = 16
        self.grid_w = 16
        
        # Vector
        self.vector_transfer_byte_len = 34 + 150
        self.vector_size = 13 + 150
        
        self._init_spaces()

    def _init_spaces(self):
        self.observation_space = gym.spaces.Dict({
            'grid': gym.spaces.Box(
                low=0, high=1,
                # The shape is updated to 22, adding one plane for the visit heat map
                shape=(22, self.grid_h, self.grid_w),
                dtype=np.uint8
            ),
            'vector': gym.spaces.Box(
                low=-np.inf, high=np.inf,
                shape=(self.vector_size,), dtype=np.float32
            )
        })
        self.action_space = gym.spaces.Discrete(len(all_possible_input))

    def _parse_observation(self, payload: bytes) -> dict:
        grid_size = self.grid_h * self.grid_w
        
        # Grid 1: Obstacles, enemies, etc.
        grid1_flat = np.frombuffer(payload[:grid_size], dtype=np.uint8, count=grid_size)
        grid1 = grid1_flat.reshape(self.grid_h, self.grid_w)  # shape (16,16)

        # Grid 2: Visit heat map. This is a separate grid.
        grid2_flat = np.frombuffer(payload[grid_size : grid_size * 2], dtype=np.uint8, count=grid_size)
        grid2 = grid2_flat.reshape(self.grid_h, self.grid_w)

        # --- One-hot encode the first grid into 21 binary planes ---
        one_hot_grid = np.zeros((21, self.grid_h, self.grid_w), dtype=np.uint8)
        for obj_id in range(21):
            one_hot_grid[obj_id] = (grid1 == obj_id).astype(np.uint8)

        # Add the visit heat map as a new, 22nd plane to the one-hot grid.
        full_grid = np.concatenate((one_hot_grid, np.expand_dims(grid2, axis=0)), axis=0)

        # Vector data starts after both grids.
        vector_bytes = payload[grid_size * 2 : grid_size * 2 + self.vector_transfer_byte_len]
        format_string = '>6B7f50b50b50b'
        unpacked_values = struct.unpack(format_string, vector_bytes)
        vector_part = np.array(unpacked_values, dtype=np.float32)

        return {
            'grid': full_grid,
            'vector': vector_part
        }

    def __getstate__(self):
        state = self.__dict__.copy()
        state.pop('client_socket', None)
        state.pop('observation_space', None)
        state.pop('action_space', None)
        return state

    def __setstate__(self, state):
        self.__dict__.update(state)
        self.client_socket = None
        self._init_spaces()

    def _map_action(self, action:int) -> bytes:
        select_action = all_possible_input[action]
        return bytes(select_action)

    def _get_obs_shape(self) -> int:
        return (self.grid_h * self.grid_w * 2) + self.vector_transfer_byte_len

    def _receive_reset(self):
        data = self._receive_fixed(payload_size)
        op_code = data[:2].decode('utf-8')
        assert op_code == '01'
        
        obs_shape = self._get_obs_shape()
        payload = data[2:2 + obs_shape]
        
        return self._parse_observation(payload)
    
    def _receive_step(self) -> tuple[Any, SupportsFloat, bool, bool, dict[str, Any]]:
        data = self._receive_fixed(payload_size)
        op_code = data[:2].decode('utf-8')
        assert op_code == '02'

        payload = data[2:]
        reward_byte = payload[0:4]
        reward:float = struct.unpack('>f', reward_byte)[0]
        terminated = bool(payload[4])
        truncated  = bool(payload[5])
        
        obs_shape = self._get_obs_shape()
        obs_bytes = payload[6: 6 + obs_shape]
        
        observation = self._parse_observation(obs_bytes)
        return observation, reward, terminated, truncated, {}
        
    def reset(self, seed: Optional[int] = None, options: Optional[dict] = None) -> tuple[dict, dict]:
        episode = options.get("episode") if options else 0
        evaluation = options.get("evaluation") if options else False
        visual = options.get("visual") if options else False
        level = options.get("level") if options else ""

        level_bytes = level.encode('utf-8')
        level_length = len(level_bytes)

        payload = struct.pack(f'>i??I{level_length}s', episode, evaluation, visual, level_length, level_bytes)
        self._send_operation('01', payload)
        observation = self._receive_reset()
        return observation, {}
    
    def step(self, action:int | np.int64) -> tuple[dict, SupportsFloat, bool, bool, dict[str, Any]]:
        value = action if isinstance(action, int) else np.int64(action).item()
        payload = self._map_action(value)
        self._send_operation('02', payload)
        new_state, reward, terminated, truncated, info = self._receive_step()
        return new_state, reward, terminated, truncated, info