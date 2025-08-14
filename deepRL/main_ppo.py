import logging
import os, sys, base64, json
from marioGame import MarioGame
from multigridstack import MultiGridStack
from ppo.cnn import CNNActor, CNNCritic
from ppo.episodebuffer import EpisodeBuffer
from ppo.ewc import EWC
from ppo.multiprocessenv import MultiprocessEnv
import torch.optim as optim
import torch.multiprocessing as mp
from ppo.ppo import PPO
from logger import setup_logging

logger = logging.getLogger('Agent:Main')

def make_env_fn():
  # Wrap the base environment with our new frame stacker
  env = MarioGame()
  env = MultiGridStack(env, num_stack=4, grid_keys=['gridScene', 'gridEnemies'])
  return env


def make_envs_fn(mef, n):
  return MultiprocessEnv(mef, n)

if __name__ == '__main__':
  setup_logging(logging.INFO)
  logger.info('start agent')
  mp.freeze_support() 

  environment_settings = {
      'env_name': 'LunarLander-v2',
      'gamma': 0.997,
      'max_minutes': 6000,
      'max_episodes': 100000,
      'goal_mean_100_reward': 25000
  }

  default_pcg = "blocks=1,random_block_height=true,enemies=0,pits=0,pipes=0,width_min=20,width_max=25,timer_min=20,timer_max=25" 
  pcg = sys.argv[1] if len(sys.argv) > 1 else default_pcg

  default_hyper_params = '''{
  "policyOptimizerLr": 0.0001,
  "policyOptimizationEpochs": 3,
  "policyClipRange": 0.1,
  "valueOptimizerLr": 0.0001,
  "valueOptimizationEpochs": 3,
  "ewcLambda": 0,
  "maxBufferEpisodes": 128,
  "maxBufferEpisodeSteps": 1000,
  "entropyLossWeight": 0.01,
  "nWorkers": 2,
  "batchSize": 1024
  }'''
  hyperParams = json.loads(base64.b64decode(sys.argv[2]).decode("utf-8")) if len(sys.argv) > 2 else json.loads(default_hyper_params)


  policy_model_fn = lambda nS, nA: CNNActor(nS, nA, hidden_dims=(256,256))
  policy_model_max_grad_norm = 0.5
  policy_optimizer_fn = lambda net, lr: optim.Adam(net.parameters(), lr=lr)
  policy_optimizer_lr = hyperParams.get('policyOptimizerLr', 0.0005)
  policy_optimization_epochs = hyperParams.get('policyOptimizationEpochs', 5)
  policy_sample_ratio = 0.8
  policy_clip_range = hyperParams.get('policyClipRange', 0.1)
  policy_stopping_kl = 0.02

  value_model_fn = lambda nS: CNNCritic(nS, hidden_dims=(256,256))
  value_model_max_grad_norm = 0.5
  value_optimizer_fn = lambda net, lr: optim.Adam(net.parameters(), lr=lr)
  value_optimizer_lr = hyperParams.get('valueOptimizerLr', 0.0005)
  value_optimization_epochs = hyperParams.get('valueOptimizationEpochs', 5)
  value_sample_ratio = 0.8
  value_clip_range = float('inf')
  value_stopping_mse = float('inf')

  ewc_fn = lambda policy_model, ewc_lambda: EWC(policy_model, ewc_lambda)
  ewc_lambda = hyperParams.get('ewcLambda', 10000.0)

  episode_buffer_fn = lambda sd, g, t, nw, me, mes: EpisodeBuffer(sd, g, t, nw, me, mes)
  max_buffer_episodes = hyperParams.get('maxBufferEpisodes', 60)
  max_buffer_episode_steps = hyperParams.get('maxBufferEpisodeSteps', 3000)

  entropy_loss_weight = hyperParams.get('entropyLossWeight', 0.01)
  tau = 0.97
  n_workers = hyperParams.get('nWorkers', 2)
  batch_size = hyperParams.get('batchSize', 64)

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
              n_workers,
              batch_size)

  # level_pool = all_abs_paths
  level_pool = [pcg]
  
  rehearsal_level_tasks = [
    #["blocks=1,enemies=0,pits=1,pipes=0,width_min=40,width_max=40"],
    #["blocks=0,enemies=1,pits=1,pipes=0,width_min=50,width_max=50"],
    # ["block0-enemy1-pit1-pipe0"],
  ]

  #evaluation_levels = ["file=./levels/evaluation/lvl-1.txt"]
  evaluation_levels = [pcg]
  # agent.play(make_env_fn, policy_model_fn, "file=./levels/evaluation/lvl-1.txt,fps=50")
  # agent.play(make_env_fn, policy_model_fn, "blocks=1,random_block_height=true,enemies=0,pits=0,pipes=0,width_min=15,width_max=20,fps=50")
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