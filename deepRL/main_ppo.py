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
  policy_optimizer_lr = 0.000025
  policy_optimization_epochs = 80
  policy_sample_ratio = 0.8
  policy_clip_range = 0.1
  policy_stopping_kl = 0.02

  value_model_fn = lambda nS: CNNCritic(nS, hidden_dims=(256,256))
  value_model_max_grad_norm = float('inf')
  value_optimizer_fn = lambda net, lr: optim.Adam(net.parameters(), lr=lr)
  value_optimizer_lr = 0.000025
  value_optimization_epochs = 80
  value_sample_ratio = 0.8
  value_clip_range = float('inf')
  value_stopping_mse = 25

  ewc_fn = lambda policy_model, ewc_lambda: EWC(policy_model, ewc_lambda)
  ewc_lambda = 3000.0

  episode_buffer_fn = lambda sd, g, t, nw, me, mes: EpisodeBuffer(sd, g, t, nw, me, mes)
  max_buffer_episodes = 16
  max_buffer_episode_steps = 1000

  entropy_loss_weight = 0.02
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
  
  # level_pool = [
  #   "training/100-basic/100-basic-movement/lvl-1.txt", 
  #   "training/100-basic/100-basic-movement/lvl-2.txt", 
  #   "training/100-basic/100-basic-movement/lvl-3.txt",
  #   # "training/100-basic/101-basic-jump/lvl-4.txt",
  #   # "training/100-basic/101-basic-jump/lvl-5.txt",
  #   # "training/100-basic/101-basic-jump/lvl-6.txt",
  #   # "training/100-basic/101-basic-jump/lvl-7.txt",
  #   # "training/100-basic/101-basic-jump/lvl-8.txt",
  #   # "training/100-basic/101-basic-jump/lvl-9.txt",
  #               ]

  level_pool = [
    "training/100-basic/103-basic-obstacle/lvl-1.txt", 
    "training/100-basic/103-basic-obstacle/lvl-2.txt", 
    "training/100-basic/103-basic-obstacle/lvl-3.txt",
    "training/100-basic/103-basic-obstacle/lvl-4.txt",
    "training/100-basic/103-basic-obstacle/lvl-5.txt",
    "training/100-basic/103-basic-obstacle/lvl-6.txt",
    "training/100-basic/103-basic-obstacle/lvl-7.txt",
    "training/100-basic/103-basic-obstacle/lvl-8.txt",
    "training/100-basic/103-basic-obstacle/lvl-9.txt",
                ]


  agent.train(make_envs_fn,
              make_env_fn,
              gamma,
              max_minutes,
              max_episodes,
              goal_mean_100_reward,
              level_pool)

  
  agent.save_ewc(level_pool)