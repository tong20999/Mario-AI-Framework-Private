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
  env = MultiGridStack(env, num_stack=4)
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

  default_pcg = '''{
    "WidthMin": 20,
    "WidthMax": 21,
    "TimerMin": 10,
    "TimerMax": 15,
    "Blocks": 1,
    "BlocksHeightOrigin": 10,
    "BlocksHeightBound": 9,
    "Coins": 1,
    "CoinsHeightOrigin": 10,
    "CoinsHeightBound": 9,
    "Enemies": 1,
    "EnemiesHeightOrigin": 5,
    "EnemiesHeightBound": 14,
    "Pits": 0,
    "PitsMinWidth": 2,
    "PitsMaxWidth": 5,
    "Pipes": 0,
    "Ramp": 0
}'''
  pcgBase64_string = sys.argv[1] if len(sys.argv) > 1 else base64.b64encode(default_pcg.encode('utf-8')).decode('utf-8')

  default_hyper_params = '''{
  "PolicyOptimizerLr": 0.0001,
  "PolicyOptimizationEpochs": 5,
  "PolicyClipRange": 0.2,
  "ValueOptimizerLr": 0.0001,
  "ValueOptimizationEpochs": 3,
  "ValueClipRange" : 0.2,
  "EwcLambda": 0,
  "MaxBufferEpisodes": 2,
  "MaxBufferEpisodeSteps": 70,
  "EntropyLossWeight": 0.01,
  "NWorkers": 2,
  "BatchSize": 1024,
  "LoadOptimizer": true,
  "PolicyStoppingKl" : 0.02
}'''

  hyperParamsString = base64.b64decode(sys.argv[2]).decode("utf-8") if len(sys.argv) > 2 else default_hyper_params
  hyperParams = json.loads(hyperParamsString)

  policy_model_fn = lambda nS, nA: CNNActor(nS, nA, hidden_dims=(256,256))
  policy_model_max_grad_norm = 0.5
  policy_optimizer_fn = lambda net, lr: optim.Adam(net.parameters(), lr=lr)
  policy_optimizer_lr = hyperParams.get('PolicyOptimizerLr')
  policy_optimization_epochs = hyperParams.get('PolicyOptimizationEpochs')
  policy_sample_ratio = 0.8
  policy_clip_range = hyperParams.get('PolicyClipRange')
  policy_stopping_kl = hyperParams.get('PolicyStoppingKl')

  value_model_fn = lambda nS: CNNCritic(nS, hidden_dims=(256,256))
  value_model_max_grad_norm = 0.5
  value_optimizer_fn = lambda net, lr: optim.Adam(net.parameters(), lr=lr)
  value_optimizer_lr = hyperParams.get('ValueOptimizerLr')
  value_optimization_epochs = hyperParams.get('ValueOptimizationEpochs')
  value_sample_ratio = 0.8
  value_clip_range = hyperParams.get('ValueClipRange')
  value_stopping_mse = float('inf')

  ewc_fn = lambda policy_model, ewc_lambda: EWC(policy_model, ewc_lambda)
  ewc_lambda = hyperParams.get('EwcLambda')

  episode_buffer_fn = lambda sd, g, t, nw, me, mes: EpisodeBuffer(sd, g, t, nw, me, mes)
  max_buffer_episodes = hyperParams.get('MaxBufferEpisodes')
  max_buffer_episode_steps = hyperParams.get('MaxBufferEpisodeSteps')

  entropy_loss_weight = hyperParams.get('EntropyLossWeight')
  tau = 0.99
  n_workers = hyperParams.get('NWorkers')
  batch_size = hyperParams.get('BatchSize')

  load_optimizer = hyperParams.get('LoadOptimizer')

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
              batch_size,
              load_optimizer)

  rehearsal_level_tasks = [
    #["blocks=1,enemies=0,pits=1,pipes=0,width_min=40,width_max=40"],
    #["blocks=0,enemies=1,pits=1,pipes=0,width_min=50,width_max=50"],
    # ["block0-enemy1-pit1-pipe0"],
  ]

  #evaluation_levels = ["file=./levels/evaluation/lvl-1.txt"]
  # agent.play(make_env_fn, policy_model_fn, "file=./levels/evaluation/lvl-1.txt,fps=50")
  # agent.play(make_env_fn, policy_model_fn, "blocks=1,random_block_height=true,enemies=0,pits=0,pipes=0,width_min=15,width_max=20,fps=50")
  agent.train(make_envs_fn,
              make_env_fn,
              gamma,
              max_minutes,
              max_episodes,
              goal_mean_100_reward,
              pcgBase64_string,
              hyperParamsString,
              rehearsal_level_tasks)



                    
  
  #agent.save_ewc(level_pool)