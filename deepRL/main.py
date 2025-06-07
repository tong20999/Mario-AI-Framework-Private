from ddqn import DDQN, FCQ, EGreedyExpStrategy, GreedyStrategy, ReplayBuffer
import torch.optim as optim
from marioGame import MarioGame

environment_settings = {
        'env_name': 'CartPole-v1',
        'gamma': 0.99,
        'max_minutes': 60,
        'max_episodes': 500,
        'goal_mean_100_reward': 475
    }

value_model_fn = lambda nS, nA: FCQ(nS, nA, hidden_dims=(512,128))
value_optimizer_fn = lambda net, lr: optim.RMSprop(net.parameters(), lr=lr)
value_optimizer_lr = 0.0001
max_gradient_norm = float('inf')

training_strategy_fn = lambda: EGreedyExpStrategy(init_epsilon=0.10,  
                                                    min_epsilon=0.10, 
                                                    decay_steps=10000)
evaluation_strategy_fn = lambda: GreedyStrategy()

replay_buffer_fn = lambda: ReplayBuffer(max_size=50000, batch_size=64)
n_warmup_batches = 5
update_target_every_steps = 1000

env_name, gamma, max_minutes, \
max_episodes, goal_mean_100_reward = environment_settings.values()
agent = DDQN(replay_buffer_fn, 
                value_model_fn, 
                value_optimizer_fn, 
                value_optimizer_lr,
                max_gradient_norm,
                training_strategy_fn,
                evaluation_strategy_fn,
                n_warmup_batches,
                update_target_every_steps)

env = MarioGame()
result, final_eval_score, training_time, wallclock_time = agent.train(
     env, gamma, max_minutes, max_episodes, goal_mean_100_reward)