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
LR = 0.001
DISCOUNT = 0.9
record = 0

class MarioAgent:

    def __init__(self, gateway, agent) -> None:
        self.gateway = gateway
        self.agent = agent
        self.n_games = 0
        self.epsilon = 0 # randomness
        self.memory = deque(maxlen=MAX_MEMORY) # popleft()
        self.agent.registerListener(self)
        self.model = Linear_QNet(256, 256, 5)
        self.trainer = QTrainer(self.model, LR, DISCOUNT)

        self.model.load()

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

    def get_state(self):
        state = self.agent.getState().getScreenCompleteObservation()
        return np.array(state, dtype=int)
    
    def get_action(self, state):
        
        self.epsilon = 80 - self.n_games
        final_move = [False, False, False, False, False]
        if random.randint(0, 200) < self.epsilon:
            move = random.randint(0, 4)
            final_move[move] = True
        else:
            state0 = torch.tensor(state, dtype=torch.float)
            prediction = self.model(state0)
            indiceMax = torch.argmax(prediction, dim=1)
            move = torch.argmax(prediction[indiceMax]).item()
            final_move[move] = True
        return final_move
    
    def update(self, play_step_result, old_state, final_move, next_state, timer):
        
        reward = play_step_result.getReward()
        done = play_step_result.isDone()
        score = play_step_result.getScore()
        o = old_state.getMarioCompleteObservation()
        n = next_state.getMarioCompleteObservation()
        self.train_short_memory(o, final_move, reward, n, done)
        
        # # # remember
        self.remember(o, final_move, reward, n, done)

    def getTrainingActions(self, state):
        # random moves: tradeoff exploration / exploitation

        self.epsilon = 80 - self.n_games
        final_move = [False, False, False, False, False]
        if random.randint(0, 200) < self.epsilon:
            move = random.randint(0, 4)
            final_move[move] = True
        else:
            state0 = torch.tensor(state, dtype=torch.float)
            flat = torch.flatten(state0)
            prediction = self.model(flat)
            s = flat.size()
            p = prediction.size()
            # indiceMax = torch.argmax(prediction, dim=1)
            # move = torch.argmax(prediction[indiceMax]).item()
            move = torch.argmax(prediction).item()
            try:
                final_move[move] = True
            except:
                sys.breakpointhook()
                print(move)

        java_list = ListConverter().convert(final_move, self.gateway._gateway_client)
        return java_list
    
    def getActions(self, state):
        final_move = [False, False, False, False, False]
        state0 = torch.tensor(state, dtype=torch.float)
        prediction = self.model(state0)
        indiceMax = torch.argmax(prediction, dim=1)
        move = torch.argmax(prediction[indiceMax]).item()
        try:
            final_move[move] = True
        except:
            print(move)

        java_list = ListConverter().convert(final_move, self.gateway._gateway_client)
        return java_list

    class Java:
        implements = ["agents.myAgentMachineLearning.AgentListener"]