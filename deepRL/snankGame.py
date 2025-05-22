from typing import Any, SupportsFloat
import gymnasium as gym
from typing import Optional
import socket
import numpy as np
import asyncio

from socketEnv import SocketEnv

class SnakeGame(SocketEnv):
    def __init__(self, fps: int = 10):
        super(SnakeGame, self).__init__()
        self.fps = fps
        # Observations are dictionaries with the agent's and the target's location.
        # Each location is encoded as an element of {0, ..., `size`-1}^2
        self.observation_space = gym.spaces.Discrete(10)

        # We have 4 actions, corresponding to "right", "up", "left", "down"
        self.action_space = gym.spaces.Discrete(4)
        # Dictionary maps the abstract actions to the directions on the grid
        self._action_to_direction = [0,1,2,3]

    def _receive_operation(self):
        # Read full 1024 bytes
        data = self._receive_fixed(1024)

        op_code = data[:2].decode('utf-8')
        payload = data[2:].rstrip(b'\x00')  # Remove padding if any

        if op_code == '01':
            return self._byte_to_state(payload)
        elif op_code == '02':
            return self._byte_to_state(payload)
        elif op_code == '03':
            self._handle_step(payload)
        else:
            print(f"Unknown operation code: {op_code}")

    def _byte_to_state(self, payload):
        return np.ndarray(payload)

    # def _get_obs(self, payload):
    #     self._send_operation('02')
    #     observation = self._receive_operation()
    #     return observation
        
    def reset(self, seed: Optional[int] = None, options: Optional[dict] = None):
        self._send_operation('01')
        observation = self._receive_operation()
        return observation, 0
    
    def _unpack(self, result):
        return observation, reward, terminated, truncated, info
    
    def step(self, action):
        payload: bytes = action
        self._send_operation('02', payload)
        result = self._receive_operation()
        observation, reward, terminated, truncated, info = self._unpack(result)
        return observation, reward, terminated, truncated, info
                                           

    
g = SnakeGame(10)
state, _ = g.reset()
a = 5
# import random
# from agent import DDQN, FCQ, EGreedyExpStrategy, GreedyStrategy, ReplayBuffer
# import torch.optim as optim
# from py4j.java_gateway import JavaGateway, CallbackServerParameters

# from game import Game

# value_model_fn = lambda nS, nA: FCQ(nS, nA, hidden_dims=(512,128))
# value_optimizer_fn = lambda net, lr: optim.RMSprop(net.parameters(), lr=lr)
# value_optimizer_lr = 0.0005

# training_strategy_fn = lambda: EGreedyExpStrategy(init_epsilon=1.0,  
#                                                     min_epsilon=0.3, 
#                                                     decay_steps=20000)
# evaluation_strategy_fn = lambda: GreedyStrategy()

# replay_buffer_fn = lambda: ReplayBuffer(max_size=50000, batch_size=128)
# n_warmup_batches = 5
# update_target_every_steps = 10

# environment_settings = {
#     'gamma': 0.99,
#     'max_minutes': 20,
#     'max_episodes': 10000,
#     'goal_mean_100_reward': 475
# }
# max_gradient_norm = float('inf')    
# gamma, max_minutes, max_episodes, goal_mean_100_reward = environment_settings.values()
# agent = DDQN(replay_buffer_fn,
#             value_model_fn,
#             value_optimizer_fn,
#             value_optimizer_lr,
#             max_gradient_norm,
#             training_strategy_fn,
#             evaluation_strategy_fn,
#             n_warmup_batches,
#             update_target_every_steps)

# gateway = JavaGateway(callback_server_parameters=CallbackServerParameters())
# javaGame = gateway.entry_point.getTraining() # type: ignore
# javaAgent = gateway.entry_point.getAgent() # type: ignore
# game:Game = Game(javaGame)

# all_possible_input:list[list[bool]] = [
#     [True, False, False, False],
#     [False, True, False, False],
#     [False, False, True, False],
#     [False, False, False, True]
# ]
# result, final_eval_score, training_time, wallclock_time = agent.train(
#     gamma, max_minutes, max_episodes, goal_mean_100_reward, 
#     game, javaAgent, "", 10, all_possible_input, 100)