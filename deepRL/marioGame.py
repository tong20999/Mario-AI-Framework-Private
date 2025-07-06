import struct
from typing import Any, SupportsFloat
import gymnasium as gym
from typing import Optional
import numpy as np

# Assuming socketEnv.py is in the same directory.
from socketEnv import SocketEnv

all_possible_input:list[list[bool]] = [
    # [LEFT, RIGHT , DOWN, SPEED, JUMP]
    [False, False, False, False, False], # Do nothing (reset jump)
    [False, True, False, False, False],  # move right
    [False, True, False, False, True], # move right and jump
    [False, True, False, True, False], # move right and speed
    [False, True, False, True, True],  # move right and speed and jump
    # [False, False, False, False, True], # Jump only
    [True, False, False, False, False], # move left
    [True, False, False, True, False],  # move left and speed
    [True, False, False, False, True], # move left and jump
    [True, False, False, True, True],  # move left and speed and jump
]

class MarioGame(SocketEnv):
    def __init__(self, fps: int = 10):
        # Call parent initializer
        super(MarioGame, self).__init__()
        self.fps = fps
        
        # Initialize the spaces. These will be handled by our custom pickling methods.
        self._init_spaces()

    def _init_spaces(self):
        # NEW: Define the space as a Dictionary
        self.observation_space = gym.spaces.Dict({
            # CNN part: 1 channel, 16x16 grid. Values are binary (0 or 1).
            'grid': gym.spaces.Box(low=0, high=1, shape=(1, 16, 16), dtype=np.uint8),
            # Vector part: 14 features. Values are binary.
            'vector': gym.spaces.Box(low=0, high=14, shape=(14,), dtype=np.uint8) # Adjust high based on actual max values
        })
        
        # The action space should be Discrete for FCCA's Categorical output
        self.action_space = gym.spaces.Discrete(len(all_possible_input))

    def _parse_observation(self, payload: bytes) -> dict:
        """Helper to parse a flat byte payload into a dictionary observation."""
        grid_size = 16 * 16
        grid_part = np.array(list(payload[:grid_size]), dtype=np.uint8).reshape(1, 16, 16)
        vector_part = np.array(list(payload[grid_size:]), dtype=np.uint8)
        return {'grid': grid_part, 'vector': vector_part}

    def __getstate__(self):
        """
        Prepare the entire object for pickling.
        This method is now solely responsible for the state.
        """
        # Start with a copy of the object's full dictionary.
        state = self.__dict__.copy()

        # Remove ALL known unpickleable attributes from both parent and child.
        # Using .pop() with a default is safer than 'del'
        state.pop('client_socket', None)
        state.pop('observation_space', None)
        state.pop('action_space', None)
        
        return state

    def __setstate__(self, state):
        """
        Restore the object in the new process.
        """
        # Restore the pickleable attributes.
        self.__dict__.update(state)
        
        # Now, explicitly re-initialize ALL unpickleable attributes we removed.
        self.client_socket = None
        self._init_spaces()

    def _map_action(self, action:int) -> bytes:
        select_action = all_possible_input[action]
        return bytes(select_action)

    def _receive_reset(self):
        data = self._receive_fixed(1024)
        op_code = data[:2].decode('utf-8')
        # The total shape is now the sum of the sizes of the spaces
        obs_shape = np.prod(self.observation_space['grid'].shape) + np.prod(self.observation_space['vector'].shape)
        payload = data[2:2 + obs_shape]
        assert op_code == '01'
        # NEW: Parse the observation into a dictionary
        return self._parse_observation(payload)
    
    def _receive_step(self) -> tuple[Any, SupportsFloat, bool, bool, dict[str, Any]]:
        data = self._receive_fixed(1024)
        op_code = data[:2].decode('utf-8')
        payload = data[2:]
        assert op_code == '02'
        rewardByte = payload[0:4]
        reward:float = struct.unpack('>f', rewardByte)[0]
        terminated = True if payload[4] else False
        obs_shape = np.prod(self.observation_space['grid'].shape) + np.prod(self.observation_space['vector'].shape)
        obs_bytes = payload[5: 5 + obs_shape]
        # NEW: Parse the observation into a dictionary
        observation = self._parse_observation(obs_bytes)
        return observation, reward, terminated, False, {}
        
    def reset(self, seed: Optional[int] = None, options: Optional[dict] = None) -> list[int]:
        episode = options.get("episode") if options else 0
        evaluation = options.get("evaluation") if options else False
        visual = options.get("visual") if options else False
        level = options.get("level") if options else ""

         # Encode the level string to bytes
        level_bytes = level.encode('utf-8')
        # Get the length of the encoded string
        level_length = len(level_bytes)

        payload = struct.pack(f'>i??I{level_length}s', episode, evaluation, visual, level_length, level_bytes)
        self._send_operation('01', payload)
        observation = self._receive_reset()
        return observation, {}
    
    def step(self, action:int | np.int64) -> tuple[list[int], SupportsFloat, bool, bool, dict[str, Any]]:
        value = action if isinstance(action, int) else np.int64(action).item()
        payload = self._map_action(value)
        self._send_operation('02', payload)
        new_state, reward, terminated, truncated, info = self._receive_step()
        return new_state, reward, terminated, truncated, info

