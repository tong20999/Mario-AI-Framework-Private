import os
from dictgridstack import DictGridStack
from marioGame import MarioGame
from ppo.cnn import CNNActor, CNNCritic
from ppo.episodebuffer import EpisodeBuffer
from ppo.ewc import EWC
from ppo.fcca import FCCA
from ppo.fcv import FCV
from ppo.multiprocessenv import MultiprocessEnv
import torch.optim as optim
import torch.multiprocessing as mp
from ppo.ppo import PPO

def make_env_fn():
  # Wrap the base environment with our new frame stacker
  env = MarioGame()
  env = DictGridStack(env, num_stack=4)
  return env


def make_envs_fn(mef, n):
  return MultiprocessEnv(mef, n)

if __name__ == '__main__':
  mp.freeze_support() 

  environment_settings = {
      'env_name': 'LunarLander-v2',
      'gamma': 0.99,
      'max_minutes': 6000,
      'max_episodes': 100000,
      'goal_mean_100_reward': 25000
  }

  policy_model_fn = lambda nS, nA: CNNActor(nS, nA, hidden_dims=(256,256))
  policy_model_max_grad_norm = float('inf')
  policy_optimizer_fn = lambda net, lr: optim.Adam(net.parameters(), lr=lr)
  policy_optimizer_lr = 0.00001
  policy_optimization_epochs = 3
  policy_sample_ratio = 0.8
  policy_clip_range = 0.1
  policy_stopping_kl = 0.02

  value_model_fn = lambda nS: CNNCritic(nS, hidden_dims=(256,256))
  value_model_max_grad_norm = float('inf')
  value_optimizer_fn = lambda net, lr: optim.Adam(net.parameters(), lr=lr)
  value_optimizer_lr = 0.00001
  value_optimization_epochs = 3
  value_sample_ratio = 0.8
  value_clip_range = float('inf')
  value_stopping_mse = 25

  ewc_fn = lambda policy_model, ewc_lambda: EWC(policy_model, ewc_lambda)
  ewc_lambda = 10000.0

  episode_buffer_fn = lambda sd, g, t, nw, me, mes: EpisodeBuffer(sd, g, t, nw, me, mes)
  max_buffer_episodes = 48
  max_buffer_episode_steps = 10000

  entropy_loss_weight = 0.001
  tau = 0.97
  n_workers = 8

  env_name, gamma, max_minutes, \
  max_episodes, goal_mean_100_reward = environment_settings.values()
  agent:PPO = PPO(policy_model_fn, 
              policy_model_max_grad_norm,
              policy_optimizer_fn,
              policy_optimizer_lr,
              policy_optimization_epochs,
              policy_sample_ratio,
              policy_clip_range,
              policy_stopping_kl,
              value_model_fn, 
              value_model_max_grad_norm,
              value_optimizer_fn,
              value_optimizer_lr,
              value_optimization_epochs,
              value_sample_ratio,
              value_clip_range,
              value_stopping_mse,
              ewc_fn,
              ewc_lambda,
              episode_buffer_fn,
              max_buffer_episodes,
              max_buffer_episode_steps,
              entropy_loss_weight,
              tau,
              n_workers)
  
  lose_path = 'C:/thesis_data/zpcg/LOSE'
  timeout_path = 'C:/thesis_data/zpcg/TIME_OUT'
  lose_abs_paths = [os.path.join(lose_path, f) for f in os.listdir(lose_path)]
  timeout_abs_paths = [os.path.join(timeout_path, f) for f in os.listdir(timeout_path)]
  all_abs_paths = lose_abs_paths + timeout_abs_paths


  level_pool = all_abs_paths
  # level_pool = ["block1-enemy1-pit1-pipe1"]
  
  rehearsal_level_tasks = [
    ["block1-enemy1-pit1-pipe0"],
    # ["block1-enemy0-pit1-pipe0"],
    # ["block0-enemy1-pit1-pipe0"],
  ]

  evaluation_levels = level_pool

  with open("C:/thesis_data/hyperparameters.txt", "a") as file:
                    file.write("policy_optimizer_lr {}\n".format(policy_optimizer_lr))
                    file.write("policy_optimization_epochs {}\n".format(policy_optimization_epochs))
                    file.write("policy_sample_ratio {}\n".format(policy_sample_ratio))
                    file.write("policy_clip_range {}\n".format(policy_clip_range))
                    file.write("policy_stopping_kl {}\n".format(policy_stopping_kl))

                    file.write("value_optimizer_lr {}\n".format(value_optimizer_lr))
                    file.write("value_optimization_epochs {}\n".format(value_optimization_epochs))
                    file.write("value_clip_range {}\n".format(value_clip_range))
                    file.write("value_optimizer_lr {}\n".format(value_optimizer_lr))
                    file.write("value_stopping_mse {}\n".format(value_stopping_mse))

                    file.write("ewc_lambda {}\n".format(ewc_lambda))

                    file.write("max_buffer_episodes {}\n".format(max_buffer_episodes))
                    file.write("max_buffer_episode_steps {}\n".format(max_buffer_episode_steps))

                    file.write("entropy_loss_weight {}\n".format(entropy_loss_weight))
                    file.write("tau {}\n".format(tau))
                    file.write("n_workers {}\n".format(n_workers))
                    file.write("levels\n")
                    for level in level_pool:
                          file.write("{}\n".format(level))
                    file.write("evaluation_levels\n")
                    for evaluation_level in evaluation_levels:
                          file.write("{}\n".format(evaluation_level))
                    file.write("rehearsal_level_tasks\n")
                    for rehearsal_level_task in rehearsal_level_tasks:
                          file.write("task\n")
                          for rehearsal_level in rehearsal_level_task:
                               file.write("{}\n".format(rehearsal_level))

  # agent.play(make_env_fn, policy_model_fn, "block0-enemy1-pit0-pipe0")

  agent.train(make_envs_fn,
              make_env_fn,
              gamma,
              max_minutes,
              max_episodes,
              goal_mean_100_reward,
              level_pool,
              rehearsal_level_tasks,
              evaluation_levels)



                    
  
  #agent.save_ewc(level_pool)