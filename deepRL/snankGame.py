import random
from agent import DDQN, FCQ, EGreedyExpStrategy, GreedyStrategy, ReplayBuffer
import torch.optim as optim
from py4j.java_gateway import JavaGateway, CallbackServerParameters

from game import Game

value_model_fn = lambda nS, nA: FCQ(nS, nA, hidden_dims=(512,128))
value_optimizer_fn = lambda net, lr: optim.RMSprop(net.parameters(), lr=lr)
value_optimizer_lr = 0.0005

training_strategy_fn = lambda: EGreedyExpStrategy(init_epsilon=1.0,  
                                                    min_epsilon=0.3, 
                                                    decay_steps=20000)
evaluation_strategy_fn = lambda: GreedyStrategy()

replay_buffer_fn = lambda: ReplayBuffer(max_size=50000, batch_size=128)
n_warmup_batches = 5
update_target_every_steps = 10

environment_settings = {
    'gamma': 0.99,
    'max_minutes': 20,
    'max_episodes': 10000,
    'goal_mean_100_reward': 475
}
max_gradient_norm = float('inf')    
gamma, max_minutes, max_episodes, goal_mean_100_reward = environment_settings.values()
agent = DDQN(replay_buffer_fn,
            value_model_fn,
            value_optimizer_fn,
            value_optimizer_lr,
            max_gradient_norm,
            training_strategy_fn,
            evaluation_strategy_fn,
            n_warmup_batches,
            update_target_every_steps)

gateway = JavaGateway(callback_server_parameters=CallbackServerParameters())
javaGame = gateway.entry_point.getTraining() # type: ignore
javaAgent = gateway.entry_point.getAgent() # type: ignore
game:Game = Game(javaGame)

all_possible_input:list[list[bool]] = [
    [True, False, False, False],
    [False, True, False, False],
    [False, False, True, False],
    [False, False, False, True]
]
result, final_eval_score, training_time, wallclock_time = agent.train(
    gamma, max_minutes, max_episodes, goal_mean_100_reward, 
    game, javaAgent, "", 10, all_possible_input, 100)