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
    [False, False, False, False, True], # Jump only
    [True, False, False, False, False], # move left
]

class MarioGame(SocketEnv):
    def __init__(self, fps: int = 10):
        # Call parent initializer
        super(MarioGame, self).__init__()
        self.fps = fps
        
        # Initialize the spaces. These will be handled by our custom pickling methods.
        self._init_spaces()

    def _init_spaces(self):
        """Helper method to create the gym spaces."""
        self.observation_space = gym.spaces.MultiDiscrete([ 
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, # time remaining
                1, 1, 1, 1, # mario mode, is mario on ground, can jump higher, facing
                1, # velocity x sign
                1, # velocity y sign
                1, 1, 1, 1, # velocity x
                1, 1, 1, 1, # velocity y
                1 # game status
                ])
        self.action_space = gym.spaces.MultiDiscrete([2] * len(all_possible_input))

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
        # Use self.observation_space.shape which is guaranteed to exist after __setstate__
        payload = data[2:2 + self.observation_space.shape[0]]
        assert op_code == '01'
        return [x for x in payload]
    
    def _receive_step(self) -> tuple[Any, SupportsFloat, bool, bool, dict[str, Any]]:
        data = self._receive_fixed(1024)
        op_code = data[:2].decode('utf-8')
        payload = data[2:]
        assert op_code == '02'
        rewardByte = payload[0:4]
        reward:float = struct.unpack('>f', rewardByte)[0]
        terminated = True if payload[4] else False
        obs_bytes = payload[5: 5 + self.observation_space.shape[0]]
        observation = [x for x in obs_bytes]
        return observation, reward, terminated, False, {}
        
    def reset(self, seed: Optional[int] = None, options: Optional[dict] = None) -> list[int]:
        episode = options.get("episode") if options else 0
        evaluation = options.get("evaluation") if options else False
        epsilon = options.get("epsilon") if options else 0
        payload = struct.pack('>if?', episode, epsilon, evaluation)
        self._send_operation('01', payload)
        observation = self._receive_reset()
        return observation
    
    def step(self, action:int | np.int64) -> tuple[list[int], SupportsFloat, bool, bool, dict[str, Any]]:
        value = action if isinstance(action, int) else np.int64(action).item()
        payload = self._map_action(value)
        self._send_operation('02', payload)
        new_state, reward, terminated, truncated, info = self._receive_step()
        return new_state, reward, terminated, truncated, info

