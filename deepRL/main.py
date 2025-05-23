from ddqn import DDQN, FCQ, EGreedyExpStrategy, GreedyStrategy, ReplayBuffer
import torch.optim as optim
from snankGame import SnakeGame

all_possible_input:list[list[bool]] = [
    # [LEFT, RIGHT , DOWN, SPEED, JUMP]
    [False, False, False, False, False], # Do nothing (reset jump)
    [False, True, False, False, False],  # move right
    [False, True, False, False, True], # move right and jump
    [False, True, False, True, False], # move right and speed
    [False, True, False, True, True],  # move right and speed and jump
     
    # [False, False, False, False, False],
    # [False, False, False, False, True], # Jump only
    # [False, False, False, True, False], # fire flower only
    # [False, False, False, True, True],
    # [False, False, True, False, False],  # Duck only
    # [False, False, True, False, True], # Duck and Jump
    # [False, False, True, True, False],
    # [False, False, True, True, True],
    # [True, False, False, False, False], # move left
    # [True, False, False, False, True], # move left and jump
    # [True, False, False, True, False], # move left and speed
    # [True, False, False, True, True],  # move left and speed and jump
    
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

state_len = 8 + 63 + 4
#state_len = 63 + 4
# state_len = 256 + 4
# state_len = 8 + 9 + 3 + 3 + 4
# state_len = 9 + 4 + 3 + 4

environment_settings = {
        'env_name': 'CartPole-v1',
        'gamma': 1.00,
        'max_minutes': 20,
        'max_episodes': 10000,
        'goal_mean_100_reward': 475
    }

value_model_fn = lambda nS, nA: FCQ(nS, nA, hidden_dims=(512,128))
value_optimizer_fn = lambda net, lr: optim.RMSprop(net.parameters(), lr=lr)
value_optimizer_lr = 0.0005
max_gradient_norm = float('inf')

training_strategy_fn = lambda: EGreedyExpStrategy(init_epsilon=1.0,  
                                                    min_epsilon=0.3, 
                                                    decay_steps=20000)
evaluation_strategy_fn = lambda: GreedyStrategy()

replay_buffer_fn = lambda: ReplayBuffer(max_size=50000, batch_size=64)
n_warmup_batches = 5
update_target_every_steps = 10

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

env = SnakeGame()
result, final_eval_score, training_time, wallclock_time = agent.train(
     env, gamma, max_minutes, max_episodes, goal_mean_100_reward)