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
    [False, False, True, True, True], # move left
    [True, False, False, False, True], # move left and jump
    # [True, False, False, True, False], # move left and speed
    # [True, False, False, True, True],  # move left and speed and jump
]

class MarioGame(SocketEnv):
    def __init__(self, fps: int = 10):
        super(MarioGame, self).__init__()
        self.fps = fps
        # Observations are dictionaries with the agent's and the target's location.
        # Each location is encoded as an element of {0, ..., `size`-1}^2
        self.observation_space = gym.spaces.MultiDiscrete([ 
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256, 256,
                ])

        actions:list[int] = []
        for i in range(len(all_possible_input)):
            actions.append(2)
        self.action_space = gym.spaces.MultiDiscrete(actions)

    def _map_action(self, action:int) -> bytes:
        select_action = all_possible_input[action]
        return bytes(select_action)

    def _receive_reset(self):
        # Read full 1024 bytes
        data = self._receive_fixed(1024)

        op_code = data[:2].decode('utf-8')
        payload = data[2:2 + 256]

        assert op_code == '01'
        return [x for x in payload]
    
    def _receive_step(self) -> tuple[Any, SupportsFloat, bool, bool, dict[str, Any]]:
        # Read full 1024 bytes
        data = self._receive_fixed(1024)

        op_code = data[:2].decode('utf-8')
        payload = data[2:]
        assert op_code == '02'
        rewardByte = payload[0:4]
        reward:float = struct.unpack('>f', rewardByte)[0]
        
        terminated = True if payload[4] else False
        obs_bytes = payload[5: 5 + 256]
        observation = [x for x in obs_bytes]
        return observation, reward, terminated, False, {}
        
    def reset(self, seed: Optional[int] = None, options: Optional[dict] = None) -> list[int]:
        episode = options.get("episode") if options else 0
        evaluation = options.get("evaluation") if options else False
        payload = struct.pack('>Ib', episode, evaluation)
        self._send_operation('01', payload)
        observation = self._receive_reset()
        return observation
    
    def step(self, action:int | np.int64) -> tuple[list[int], SupportsFloat, bool, bool, dict[str, Any]]:
        if type(action) == int:
            value = action
        else:
            value = np.int64(action).item()
        
        payload = self._map_action(value)
        self._send_operation('02', payload)
        new_state, reward, terminated, truncated, info = self._receive_step()
        return new_state, reward, terminated, truncated, info