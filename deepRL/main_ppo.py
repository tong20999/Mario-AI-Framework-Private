from marioGame import MarioGame
from ppo.episodebuffer import EpisodeBuffer
from ppo.fcca import FCCA
from ppo.fcv import FCV
from ppo.multiprocessenv import MultiprocessEnv
import torch.optim as optim
import torch.multiprocessing as mp
from ppo.ppo import PPO

# ✅ Define the function at the top level
def make_env_fn():
  return MarioGame()

# (Optional but recommended) Also make the lambda a top-level function
def make_envs_fn(mef, n):
  return MultiprocessEnv(mef, n)

# ===================================================================
#  The main execution block MUST be inside this conditional
# ===================================================================
if __name__ == '__main__':
    # The freeze_support() line is good practice for Windows, especially if you plan to package your app.
    mp.freeze_support() 

    environment_settings = {
        'env_name': 'LunarLander-v2',
        'gamma': 0.99,
        'max_minutes': 20,
        'max_episodes': 10000,
        'goal_mean_100_reward': 250
    }

    policy_model_fn = lambda nS, nA: FCCA(nS, nA, hidden_dims=(256,256))
    policy_model_max_grad_norm = float('inf')
    policy_optimizer_fn = lambda net, lr: optim.Adam(net.parameters(), lr=lr)
    policy_optimizer_lr = 0.00005
    policy_optimization_epochs = 80
    policy_sample_ratio = 0.8
    policy_clip_range = 0.1
    policy_stopping_kl = 0.02

    value_model_fn = lambda nS: FCV(nS, hidden_dims=(256,256))
    value_model_max_grad_norm = float('inf')
    value_optimizer_fn = lambda net, lr: optim.Adam(net.parameters(), lr=lr)
    value_optimizer_lr = 0.00005
    value_optimization_epochs = 80
    value_sample_ratio = 0.8
    value_clip_range = float('inf')
    value_stopping_mse = 25

    episode_buffer_fn = lambda sd, g, t, nw, me, mes: EpisodeBuffer(sd, g, t, nw, me, mes)
    max_buffer_episodes = 16
    max_buffer_episode_steps = 1000

    entropy_loss_weight = 0.01
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
                episode_buffer_fn,
                max_buffer_episodes,
                max_buffer_episode_steps,
                entropy_loss_weight,
                tau,
                n_workers)

    # make_envs_fn = lambda mef, n: MultiprocessEnv(mef, n)
    # make_env_fn = get_make_env_fn()
    result, final_eval_score, training_time, wallclock_time = agent.train(make_envs_fn,
                                                                            make_env_fn,
                                                                            gamma,
                                                                            max_minutes,
                                                                            max_episodes,
                                                                            goal_mean_100_reward)