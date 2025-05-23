import struct
from typing import Any, SupportsFloat
import gymnasium as gym
from typing import Optional
import numpy as np

from socketEnv import SocketEnv

class SnakeGame(SocketEnv):
    def __init__(self, fps: int = 10):
        super(SnakeGame, self).__init__()
        self.fps = fps
        # Observations are dictionaries with the agent's and the target's location.
        # Each location is encoded as an element of {0, ..., `size`-1}^2
        self.observation_space = gym.spaces.MultiDiscrete([ 2, 2, 2, 2, 2, 4, 2, 2, 2, 2])

        # We have 4 actions, corresponding to "right", "up", "left", "down"
        self.action_space = gym.spaces.Discrete(4)

    def _receive_reset(self):
        # Read full 1024 bytes
        data = self._receive_fixed(1024)

        op_code = data[:2].decode('utf-8')
        payload = data[2:2 + 10]

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
        obs_bytes = payload[5: 5 + 10]
        observation = [x for x in obs_bytes]
        return observation, reward, terminated, False, {}
        
    def reset(self, seed: Optional[int] = None, options: Optional[dict] = None) -> list[int]:
        self._send_operation('01')
        observation = self._receive_reset()
        return observation
    
    def step(self, action:int | np.int64) -> tuple[list[int], SupportsFloat, bool, bool, dict[str, Any]]:
        
        if type(action) == int:
            value = action
        else:
            value = np.int64(action).item()
        payload = value.to_bytes(1, 'big')
        self._send_operation('02', payload)
        new_state, reward, terminated, truncated, info = self._receive_step()
        return new_state, reward, terminated, truncated, info