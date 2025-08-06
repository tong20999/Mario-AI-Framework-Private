import struct
from typing import Any, SupportsFloat, Optional
import gymnasium as gym
import numpy as np

# Assuming socketEnv.py is in the same directory.
from socketEnv import SocketEnv

all_possible_input:list[list[bool]] = [
    # [LEFT, RIGHT , DOWN, SPEED, JUMP]
    [False, False, False, False, False], # Do nothing
    [False, True, False, False, False],  # move right
    [False, True, False, False, True], # move right and jump
    [False, True, False, True, False], # move right and speed
    [False, True, False, True, True],  # move right and speed and jump
    [False, False, False, False, True], # Jump only
    [True, False, False, False, False], # move left
    [True, False, False, True, False],  # move left and speed
    [True, False, False, False, True], # move left and jump
    [True, False, False, True, True],  # move left and speed and jump
]

class MarioGame(SocketEnv):
    def __init__(self, fps: int = 10):
        super(MarioGame, self).__init__()
        self.fps = fps
        self.vector_transfer_byte_len = 65
        self.vector_size = 23
        self._init_spaces()

    def _init_spaces(self):
        self.observation_space = gym.spaces.Dict({
            'gridScene': gym.spaces.Box(low=0, high=255, shape=(1, 16, 16), dtype=np.uint8),
            'gridEnemies': gym.spaces.Box(low=0, high=255, shape=(1, 16, 16), dtype=np.uint8),
            'vector': gym.spaces.Box(low=0, high=255, shape=(self.vector_size,), dtype=np.uint8)
        })
        self.action_space = gym.spaces.Discrete(len(all_possible_input))

    def _parse_observation(self, payload: bytes) -> dict:
        grid_size = 16 * 16
        grid_scene_end = grid_size
        grid_enemies_end = grid_size * 2

        grid_scene_part = np.array(list(payload[:grid_scene_end]), dtype=np.uint8).reshape(1, 16, 16)
        grid_enemies_part = np.array(list(payload[grid_scene_end:grid_enemies_end]), dtype=np.uint8).reshape(1, 16, 16)
        # vector_part = np.array(list(payload[grid_enemies_end:]), dtype=np.uint8)
        vector_bytes = payload[grid_enemies_end:]
        format_string  = '>bbbbbbbbbffffffffffffff'
        unpacked_values = struct.unpack(format_string, vector_bytes)
        vector_part = np.array(unpacked_values, dtype=np.float32)
        
        return {'gridScene': grid_scene_part, 'gridEnemies': grid_enemies_part, 'vector': vector_part}

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
        grid_scene_size = np.prod(self.observation_space['gridScene'].shape)
        grid_enemies_size = np.prod(self.observation_space['gridEnemies'].shape)
        return grid_scene_size + grid_enemies_size + self.vector_transfer_byte_len

    def _receive_reset(self):
        data = self._receive_fixed(1024)
        op_code = data[:2].decode('utf-8')
        assert op_code == '01'
        
        obs_shape = self._get_obs_shape()
        payload = data[2:2 + obs_shape]
        
        return self._parse_observation(payload)
    
    def _receive_step(self) -> tuple[Any, SupportsFloat, bool, bool, dict[str, Any]]:
        data = self._receive_fixed(1024)
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

