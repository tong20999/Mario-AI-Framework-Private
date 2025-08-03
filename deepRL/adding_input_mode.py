import torch
import gymnasium as gym
import numpy as np
from ppo.cnn import CNNActor, CNNCritic

old_input = 16
new_input = 19
output_dim = 10
old_actor_path = 'C:/thesis_data/model.policy.52.tar'
old_critic_path = 'C:/thesis_data/model.value.52.tar'
new_actor_path = 'C:/thesis_data/model.policy.tar'
new_critic_path = 'C:/thesis_data/model.value.tar'

new_obs_space = gym.spaces.Dict({
    'gridScene': gym.spaces.Box(low=0, high=255, shape=(4, 16, 16), dtype=np.uint8),
    'gridEnemies': gym.spaces.Box(low=0, high=255, shape=(4, 16, 16), dtype=np.uint8),
    'vector': gym.spaces.Box(low=0, high=255, shape=(new_input,), dtype=np.uint8)
})

def transfer_weights(model, old_state_dict_path):
    old_state_dict = torch.load(old_state_dict_path, weights_only=True)
    new_state_dict = model.state_dict()

    for key, new_param in new_state_dict.items():
        if key in old_state_dict:
            old_param = old_state_dict[key]
            if old_param.shape != new_param.shape:
                if 'mlp.0.weight' in key:
                    new_param.data[:, :old_input] = old_param.data
                elif 'embedding.weight' in key:
                    new_param.data[:, :10] = old_param.data
                elif 'cnn.0.weight' in key:
                    new_param.data[:, :10, :, :] = old_param.data
                elif "bias" in key:
                    new_param.data = old_param.data
            else:
                new_param.data.copy_(old_param.data)

    model.load_state_dict(new_state_dict)
    return model

new_actor = CNNActor(new_obs_space, output_dim=output_dim)
new_critic = CNNCritic(new_obs_space)

new_actor = transfer_weights(new_actor, old_actor_path)
new_critic = transfer_weights(new_critic, old_critic_path)

torch.save(new_actor.state_dict(), new_actor_path)
torch.save(new_critic.state_dict(), new_critic_path)