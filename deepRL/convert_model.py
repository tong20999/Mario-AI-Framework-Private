import numpy as np
import torch
import os
from ppo.cnn import CNNActor, CNNCritic
from gymnasium.spaces import Box, Dict

# --- Configuration: You may need to adjust these paths ---
OLD_MODEL_PATH = "C:/thesis_data/model.value.357.tar"  # IMPORTANT: Rename your best old model file to this
NEW_MODEL_PATH = "C:/thesis_data/model.value.converted.tar" # The output file

# Define the NEW observation space with the 15-feature vector
# This must match the new structure in your MarioGame environment
new_observation_space = Dict({
    'grid': Box(low=0, high=1, shape=(4, 16, 16), dtype=np.uint8),
    'vector': Box(low=0, high=1, shape=(15,), dtype=np.uint8) # The new 15-feature size
})
# The number of possible actions
action_dim = 7
# The hidden dimensions of your models
hidden_dims = (256, 256)

def convert_model_weights(old_model_path, new_model_path, new_obs_space, action_dim, hidden_dims):
    new_model = CNNCritic(new_obs_space, hidden_dims)
    saved_state_dict = torch.load(old_model_path, map_location='cpu') # Load to CPU for safety
    new_model_state_dict = new_model.state_dict()
    
    transferable_weights = {k: v for k, v in saved_state_dict.items() if k in new_model_state_dict and v.shape == new_model_state_dict[k].shape}
    
    new_model_state_dict.update(transferable_weights)
    new_model.load_state_dict(new_model_state_dict)
    torch.save(new_model.state_dict(), new_model_path)
    print(f"{len(transferable_weights)} out of {len(new_model_state_dict)} layers were transferred.")

# --- Main execution block ---
if __name__ == '__main__':
    # You can run this script directly to perform the conversion
    convert_model_weights(
        old_model_path=OLD_MODEL_PATH,
        new_model_path=NEW_MODEL_PATH,
        new_obs_space=new_observation_space,
        action_dim=action_dim,
        hidden_dims=hidden_dims
    )