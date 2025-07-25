import os, sys, base64, json
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
import rlstatistics as statistics

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

  pcg = sys.argv[1] if len(sys.argv) > 1 else "blocks=2,enemies=2,pits=1,pipes=1,width_min=40,width_max=50"
  json_params = sys.argv[2] if len(sys.argv) > 2 else '''{"policyOptimizerLr":0.00001,"policyOptimizationEpochs":3,"policyClipRange":0.1,"valueOptimizerLr":0.00001,"valueOptimizationEpochs":3,"ewcLambda":10000,"maxBufferEpisodes":96,"maxBufferEpisodeSteps":3000,"entropyLossWeight":0.001,"nWorkers":10}'''
  hyperParams = json.loads(base64.b64decode(json_params).decode("utf-8"))

  policy_model_fn = lambda nS, nA: CNNActor(nS, nA, hidden_dims=(256,256))
  policy_model_max_grad_norm = float('inf')
  policy_optimizer_fn = lambda net, lr: optim.Adam(net.parameters(), lr=lr)
  policy_optimizer_lr = hyperParams.get('policyOptimizerLr', 0.00001)
  policy_optimization_epochs = hyperParams.get('policyOptimizationEpochs', 3)
  policy_sample_ratio = 0.8
  policy_clip_range = hyperParams.get('policyClipRange', 0.1)
  policy_stopping_kl = 0.02

  value_model_fn = lambda nS: CNNCritic(nS, hidden_dims=(256,256))
  value_model_max_grad_norm = float('inf')
  value_optimizer_fn = lambda net, lr: optim.Adam(net.parameters(), lr=lr)
  value_optimizer_lr = hyperParams.get('valueOptimizerLr', 0.00001)
  value_optimization_epochs = hyperParams.get('valueOptimizationEpochs', 3)
  value_sample_ratio = 0.8
  value_clip_range = float('inf')
  value_stopping_mse = 25

  ewc_fn = lambda policy_model, ewc_lambda: EWC(policy_model, ewc_lambda)
  ewc_lambda = hyperParams.get('ewcLambda', 10000.0)

  episode_buffer_fn = lambda sd, g, t, nw, me, mes: EpisodeBuffer(sd, g, t, nw, me, mes)
  max_buffer_episodes = hyperParams.get('maxBufferEpisodes', 96)
  max_buffer_episode_steps = hyperParams.get('maxBufferEpisodeSteps', 3000)

  entropy_loss_weight = hyperParams.get('entropyLossWeight', 0.001)
  tau = 0.97
  n_workers = hyperParams.get('nWorkers', 10)

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
  
  # lose_path = 'C:/thesis_data/zpcg/LOSE'
  # timeout_path = 'C:/thesis_data/zpcg/TIME_OUT'
  # lose_abs_paths = [os.path.join(lose_path, f) for f in os.listdir(lose_path)]
  # timeout_abs_paths = [os.path.join(timeout_path, f) for f in os.listdir(timeout_path)]
  # all_abs_paths = lose_abs_paths + timeout_abs_paths
 

  root_dir = 'C:/thesis_data/{}'.format(pcg)
  if not os.path.exists(root_dir):
      os.makedirs(root_dir)
      
  subfolders = [name for name in os.listdir(root_dir) if os.path.isdir(os.path.join(root_dir, name))]
  numbers = [int(name) for name in subfolders if name.isdigit()]
  latest = -1
  if numbers:
    latest = max(numbers)
    working_dir = os.path.join(root_dir, str(latest + 1))
  else:
    working_dir = os.path.join(root_dir, '1')

  os.makedirs(working_dir)

  # level_pool = all_abs_paths
  level_pool = [pcg]
  
  rehearsal_level_tasks = [
    #["blocks=1,enemies=0,pits=1,pipes=0,width_min=40,width_max=40"],
    #["blocks=0,enemies=1,pits=1,pipes=0,width_min=50,width_max=50"],
    # ["block0-enemy1-pit1-pipe0"],
  ]

  evaluation_levels = level_pool

  # agent.play(make_env_fn, policy_model_fn, "blocks=15,enemies=13,pits=3,pipes=3,width_min=100,width_max=100,fps=45")
  statistics.write_hyperparameters(
       working_dir,
              policy_optimizer_lr,
              policy_optimization_epochs,
              policy_sample_ratio,
              policy_clip_range,
              policy_stopping_kl,
              value_optimizer_lr,
              value_optimization_epochs,
              value_clip_range,
              value_stopping_mse,
              ewc_lambda,
              max_buffer_episodes,
              max_buffer_episode_steps,
              entropy_loss_weight,
              tau,
              n_workers,
              level_pool,
              evaluation_levels,
              rehearsal_level_tasks
  )
  agent.train(make_envs_fn,
              make_env_fn,
              gamma,
              max_minutes,
              max_episodes,
              goal_mean_100_reward,
              level_pool,
              rehearsal_level_tasks,
              evaluation_levels,
              working_dir)



                    
  
  #agent.save_ewc(level_pool)