from py4j.java_collections import ListConverter
from collections import deque
import torch
import random
import numpy as np

from model import Linear_QNet, QTrainer

MAX_MEMORY = 100_000
BATCH_SIZE = 1000
LR = 0.001
DISCOUNT = 0.9

class A:
    def getValue():
        return "A"


class MarioAgent:

    def __init__(self, gateway, agent) -> None:
        self.gateway = gateway
        self.agent = agent
        self.n_games = 0
        self.epsilon = 0 # randomness
        self.memory = deque(maxlen=MAX_MEMORY) # popleft()
        self.agent.registerListener(self)
        self.model = Linear_QNet(16, 256, 5)
        # self.model.load()
        self.trainer = QTrainer(self.model, LR, DISCOUNT)

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

    def __get_state(self, state):
        s = state.getScreenCompleteObservation()

    def getActions(self, state):
        s = state.getScreenCompleteObservation()
        # s0:list[int] = s[0]
        # marioMode = state.getMarioMode()
        # coin = state.getNumCollectedCoins()
        # random moves: tradeoff exploration / exploitation

        self.epsilon = 80 - self.n_games
        final_move = [False, False, False, False, False]
        if random.randint(0, 200) < self.epsilon:
            move = random.randint(0, 4)
            print(move)
            final_move[move] = True
        else:
            state0 = torch.tensor(s, dtype=torch.float)
            flat = torch.flatten(state0)
            prediction = self.model(flat)
            move = torch.argmax(prediction).item()
            print(move)
            final_move[move] = True

        java_list = ListConverter().convert(final_move, self.gateway._gateway_client)
        return java_list

    class Java:
        implements = ["agents.chuiploy.AgentListener"]