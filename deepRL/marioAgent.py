import math
import pdb
import sys
import time
from py4j.java_collections import ListConverter
from collections import deque
import torch
import random
import numpy as np

from model import Linear_QNet, QTrainer

MAX_MEMORY = 100_000
BATCH_SIZE = 1000
LR = 0.0001
DISCOUNT = 0.9

EPS_START = 0.9
EPS_END = 0.05
EPS_DECAY = 1000

all_input = [
    0x00000,
    0x00001,
    0x00010,
    0x00011,
    0x00100,
    0x00101,
    0x00110,
    0x00111,
    0x01000,
    0x01001,
    0x01010,
    0x01011,
    0x01100,
    0x01101,
    0x01110,
    0x01111,
    0x10000,
    0x10001,
    0x10010,
    0x10011,
    0x10100,
    0x10101,
    0x10110,
    0x10111,
    0x11000,
    0x11001,
    0x11010,
    0x11011,
    0x11100,
    0x11101,
    0x11110,
    0x11111,
]


all_possible_input:list[list[bool]] = [
    # [RIGHT, LEFT , DOWN, SPEED, JUMP]
    
    # [False, False, False, False, False],
    # [False, False, False, False, True], # Jump only
    # [False, False, False, True, False], # fire flower only
    # [False, False, False, True, True],
    # [False, False, True, False, False],  # Duck only
    # [False, False, True, False, True], # Duck and Jump
    # [False, False, True, True, False],
    # [False, False, True, True, True],
    # [True, False, False, False, False], # move right
    # [True, False, False, False, True], # move right and jump
    [True, False, False, True, False], # move right and speed
    [True, False, False, True, True],  # move right and speed and jump
    # [False, True, False, False, False],  # move left
    # [False, True, False, False, True], # move left and jump
    [False, True, False, True, False], # move left and speed
    [False, True, False, True, True],  # move left and speed and jump
    # [False, True, True, False, False],
    # [False, True, True, False, True],
    # [False, True, True, True, False],
    # [False, True, True, True, True],
    
    # [True, False, True, False, False],
    # [True, False, True, False, True],
    # [True, False, True, True, False],
    # [True, False, True, True, True],
    # [True, True, False, False, False],
    # [True, True, False, False, True],
    # [True, True, False, True, False],
    # [True, True, False, True, True],
    # [True, True, True, False, False],
    # [True, True, True, False, True],
    # [True, True, True, True, False],
    # [True, True, True, True, True],
]



class MarioAgent:

    def __init__(self, gateway, agent) -> None:
        self.sum_reward = 0
        self.gateway = gateway
        self.agent = agent
        self.steps_done = 0
        self.n_games = 0
        self.epsilon = 0 # randomness
        self.memory = deque(maxlen=MAX_MEMORY) # popleft()
        self.agent.registerListener(self)
        self.model = Linear_QNet(279, 166 , len(all_possible_input))
        self.model.to('cuda')
        self.trainer = QTrainer(self.model, LR, DISCOUNT)

        # self.model.load()

    def remember(self, state, action, reward, next_state, done):
        self.memory.append((state, action, reward, next_state, done))

    def train_long_memory(self):
        if len(self.memory) > BATCH_SIZE:
            mini_sample = random.sample(self.memory, BATCH_SIZE) # list of tuples
        else:
            mini_sample = self.memory

        states, actions, rewards, next_states, dones = zip(*mini_sample)
        self.trainer.train_step(states, actions, rewards, next_states, dones)
    
    def train_short_memory(self, state, action, reward, next_state, done):
        self.trainer.train_step(state, action, reward, next_state, done)
    
    def update(self, output:bytes):
        reward = int.from_bytes([output[0], output[1], output[2], output[3]], byteorder='big', signed=True)
        done = True if output[4] else False
        final_move = [x for x in output[5: 5 + 5]]
        final_move = [True if x == 1 else False for x in final_move]
        self.sum_reward += reward
        if(done):
            print(self.sum_reward)
            self.sum_reward = 0
            
        old_state = [x for x in output[5 + 5: 5 + 5 + 279]]
        next_state = [x for x in output[5 + 5 + 279:5 + 5 + 279 + 279]]
        self.train_short_memory(old_state, final_move, reward, next_state, done)
        
        # remember
        self.remember(old_state, final_move, reward, next_state, done)

    def getTrainingActions(self, state:bytes):
        # random moves: tradeoff exploration / exploitation
        # state is bytes array

        states = [x for x in state]

        self.epsilon = 20 - self.n_games

        sample = random.random()
        eps_threshold = EPS_END + (EPS_START - EPS_END) * math.exp(-1. * self.steps_done / EPS_DECAY)
        self.steps_done += 1

        # if random.randint(0, 200) < self.epsilon:
        if sample > eps_threshold:
        # if False:
            move = random.randint(0, len(all_possible_input) - 1)
            final_move = all_possible_input[int(move)]
        else:
            # TODO flat or not to flat
            state0 = torch.tensor(states, dtype=torch.float)
            prediction = self.model(state0.to().cuda())
            # indiceMax = torch.argmax(prediction, dim=1)
            # move = torch.argmax(prediction[indiceMax]).item()
            move = torch.argmax(prediction).item()
            try:
                final_move = all_possible_input[int(move)]
            except:
                sys.breakpointhook()
                print(move)

        return bytes(final_move)

    class Java:
        implements = ["agents.myAgentMachineLearning.AgentListener"]