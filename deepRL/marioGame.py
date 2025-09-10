import struct
from typing import Any, SupportsFloat, Optional
import gymnasium as gym
import numpy as np

# Assuming socketEnv.py is in the same directory.
from socketEnv import SocketEnv

payload_size = 2048 * 3

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
        
        # Grid channels, all equal to 1
        self.channel_count = 1
        self.grid_h = 16
        self.grid_w = 16
        
        # Vector
        self.vector_transfer_byte_len = 72
        self.vector_size = 27
        
        self._init_spaces()

    def _init_spaces(self):
        self.observation_space = gym.spaces.Dict({
            'gridSolid': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridBlocks': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridCoins': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridGoomba': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridGoombaWing': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridGreenKoompa': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridGreenKoompaWing': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridRedKoompa': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridRedKoompaWing': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridSpiky': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridSpikyWing': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridEnemyFlower': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridShell': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridBulletBill': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridMushroom': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridFirepower': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridLifeMushroom': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridBrick': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridSemiSolid': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridFlags': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'gridFireball': gym.spaces.Box(low=0, high=255, shape=(self.channel_count, self.grid_h, self.grid_w), dtype=np.uint8),
            'vector': gym.spaces.Box(low=-1, high=1, shape=(self.vector_size,), dtype=np.float32)
        })
        self.action_space = gym.spaces.Discrete(len(all_possible_input))

    def _parse_observation(self, payload: bytes) -> dict:
            grid_size = self.grid_h * self.grid_w
            
            # Grids
            solid_flat = np.frombuffer(payload[0:grid_size], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)
            
            start = grid_size
            end = start + grid_size
            blocks_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            coins_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            goomba_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)
            
            start = end
            end += grid_size
            goomba_wing_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            green_koompa_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)
            
            start = end
            end += grid_size
            green_koompa_wing_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            red_koompa_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            red_koompa_wing_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            spiky_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            spiky_wing_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            enemy_flower_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            shell_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)
            
            start = end
            end += grid_size
            bullet_bill_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            mushroom_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            firepower_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            life_mushroom_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            brick_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)
            
            start = end
            end += grid_size
            semi_solid_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            flags_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            start = end
            end += grid_size
            fireball_flat = np.frombuffer(payload[start:end], dtype=np.uint8, count=grid_size).reshape(self.channel_count, self.grid_h, self.grid_w)

            # Vector data
            vector_start = end
            vector_end = vector_start + self.vector_transfer_byte_len
            a = vector_end - vector_start   
            vector_bytes = payload[0:72]
            
            # Structure format string matching State.java's toByte()
            format_string = '>BBBBBBffffBffBffBffffBfBfBf'
            unpacked_values = struct.unpack(format_string, vector_bytes)
            vector_part = np.array(unpacked_values, dtype=np.float32)

            return {
                'gridSolid': solid_flat,
                'gridBlocks': blocks_flat,
                'gridCoins': coins_flat,
                'gridGoomba': goomba_flat,
                'gridGoombaWing': goomba_wing_flat,
                'gridGreenKoompa': green_koompa_flat,
                'gridGreenKoompaWing': green_koompa_wing_flat,
                'gridRedKoompa': red_koompa_flat,
                'gridRedKoompaWing': red_koompa_wing_flat,
                'gridSpiky': spiky_flat,
                'gridSpikyWing': spiky_wing_flat,
                'gridEnemyFlower': enemy_flower_flat,
                'gridShell': shell_flat,
                'gridBulletBill': bullet_bill_flat,
                'gridMushroom': mushroom_flat,
                'gridFirepower': firepower_flat,
                'gridLifeMushroom': life_mushroom_flat,
                'gridBrick': brick_flat,
                'gridSemiSolid': semi_solid_flat,
                'gridFlags': flags_flat,
                'gridFireball': fireball_flat,
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
        num_grids = 21 # Total number of grid types
        return (num_grids * self.grid_h * self.grid_w) + self.vector_transfer_byte_len

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
        playMode = options.get("playMode") if options else False
        level = options.get("level") if options else ""

        level_bytes = level.encode('utf-8')
        level_length = len(level_bytes)

        payload = struct.pack(f'>i???I{level_length}s', episode, evaluation, visual, playMode, level_length, level_bytes)
        self._send_operation('01', payload)
        observation = self._receive_reset()
        return observation, {}
    
    def step(self, action:int | np.int64) -> tuple[dict, SupportsFloat, bool, bool, dict[str, Any]]:
        value = action if isinstance(action, int) else np.int64(action).item()
        payload = self._map_action(value)
        self._send_operation('02', payload)
        new_state, reward, terminated, truncated, info = self._receive_step()
        return new_state, reward, terminated, truncated, info