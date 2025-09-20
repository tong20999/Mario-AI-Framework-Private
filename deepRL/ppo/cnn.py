import torch
import torch.nn as nn
import numpy as np
from gymnasium.spaces import Dict

import torch
import torch.nn as nn
import torch.nn.functional as F
from gymnasium.spaces import Dict

class ResidualBlock(nn.Module):
    def __init__(self, in_channels, out_channels, stride=1):
        super(ResidualBlock, self).__init__()
        self.conv1 = nn.Conv2d(in_channels, out_channels, kernel_size=3, stride=stride, padding=1, bias=False)
        self.bn1 = nn.BatchNorm2d(out_channels)
        self.conv2 = nn.Conv2d(out_channels, out_channels, kernel_size=3, stride=1, padding=1, bias=False)
        self.bn2 = nn.BatchNorm2d(out_channels)

        self.shortcut = nn.Sequential()
        if stride != 1 or in_channels != out_channels:
            self.shortcut = nn.Sequential(
                nn.Conv2d(in_channels, out_channels, kernel_size=1, stride=stride, bias=False),
                nn.BatchNorm2d(out_channels)
            )

    def forward(self, x):
        out = F.relu(self.bn1(self.conv1(x)))
        out = self.bn2(self.conv2(out))
        out += self.shortcut(x) # The "residual" connection!
        out = F.relu(out)
        return out

class CNNBase(nn.Module):
    def __init__(self, observation_space: Dict, num_stack: int, num_object_types: int = 21):
        super(CNNBase, self).__init__()

        # --- 1. Upgraded CNN Path with Residual Blocks ---
        cnn_input_channels = num_object_types * num_stack
        self.cnn_pre_layer = nn.Conv2d(cnn_input_channels, 64, kernel_size=3, stride=1, padding=1)
        
        self.res_stack = nn.Sequential(
            ResidualBlock(64, 64),
            ResidualBlock(64, 128, stride=2), # Downsamples from 16x16 to 8x8
            ResidualBlock(128, 128),
            ResidualBlock(128, 256, stride=2), # Downsamples from 8x8 to 4x4
            ResidualBlock(256, 256),
            nn.AdaptiveAvgPool2d((1, 1)), # Global average pooling
            nn.Flatten()
        )
        grid_feature_dim = 256

        # --- 2. Refined Vector Path with Separate Streams ---
        # NOTE: You'd need to calculate these exact sizes from marioGame.py
        mario_phys_dim = 13 # 6 bools + 7 floats
        objective_dim = 150 # 3x 50-byte objectives
        
        self.mario_mlp = nn.Sequential(nn.Linear(mario_phys_dim, 64), nn.ReLU())
        self.objective_mlp = nn.Sequential(nn.Linear(objective_dim, 128), nn.ReLU())
        vector_feature_dim = 64 + 128

        # --- 3. Fusion Head ---
        combined_dim = grid_feature_dim + vector_feature_dim
        self.final_mlp = nn.Sequential(
            nn.Linear(combined_dim, 512),
            nn.ReLU(),
            nn.Linear(512, 256),
            nn.ReLU()
        )
        self.feature_dim = 256

    def forward(self, states: dict):
        # --- CNN Path ---
        grid_obs = states['grid']
        batch_size, num_stack, num_planes, height, width = grid_obs.shape
        cnn_input = grid_obs.view(batch_size, num_stack * num_planes, height, width)
        
        x = self.cnn_pre_layer(cnn_input)
        grid_features = self.res_stack(x)

        # --- Vector Path ---
        vector_obs = states['vector']
        # Split the vector observation into its semantic parts
        # IMPORTANT: Slicing indices are illustrative and must be set correctly!
        mario_phys_vec = vector_obs[:, :13]
        objective_vec = vector_obs[:, 13:]

        mario_features = self.mario_mlp(mario_phys_vec)
        objective_features = self.objective_mlp(objective_vec)
        
        # --- Fusion ---
        combined_features = torch.cat([grid_features, mario_features, objective_features], dim=1)
        
        final_output = self.final_mlp(combined_features)
        return final_output

class CNNActor(CNNBase):
    """
    The Actor network. Inherits the updated CNNBase.
    """
    def __init__(self, observation_space, output_dim, num_stack, **kwargs):
        super().__init__(observation_space, num_stack, **kwargs)
        self.actor_head = nn.Linear(self.feature_dim, output_dim)
        
        device = "cuda:0" if torch.cuda.is_available() else "cpu"
        self.device = torch.device(device)
        self.to(self.device)

    def _format_obs(self, obs: dict):
        return {
            'grid': torch.tensor(np.array(obs['grid']), dtype=torch.float32, device=self.device),
            'vector': torch.tensor(np.array(obs['vector']), dtype=torch.float32, device=self.device)
        }

    def _format_single_obs(self, obs: dict):
        return {
            'grid': torch.tensor(obs['grid'], dtype=torch.float32, device=self.device).unsqueeze(0),
            'vector': torch.tensor(obs['vector'], dtype=torch.float32, device=self.device).unsqueeze(0)
        }

    def forward(self, states: dict):
        features = super().forward(states)
        logits = self.actor_head(features)
        return logits

    def np_pass(self, states):
        formatted_states = self._format_obs(states)
        logits = self.forward(formatted_states)
        
        dist = torch.distributions.Categorical(logits=logits)
        actions = dist.sample()
        logpas = dist.log_prob(actions)
        
        np_actions = actions.detach().cpu().numpy()
        np_logpas = logpas.detach().cpu().numpy()
        np_logits = logits.detach().cpu().numpy()
        
        is_exploratory = np_actions != np.argmax(np_logits, axis=1)
        
        return np_actions, np_logpas, is_exploratory

    def select_action(self, obs: dict):
        states = self._format_single_obs(obs)
        logits = self.forward(states)
        dist = torch.distributions.Categorical(logits=logits)
        action = dist.sample()
        return action.item()

    def select_greedy_action(self, obs: dict):
        states = self._format_single_obs(obs)
        logits = self.forward(states)
        action = torch.argmax(logits, dim=-1)
        return action.item()

    def get_predictions(self, states, actions):
        if not isinstance(states['grid'], torch.Tensor):
            states = self._format_obs(states)
        
        if not isinstance(actions, torch.Tensor):
            actions = torch.tensor(actions, device=self.device)
        logits = self.forward(states)
        
        dist = torch.distributions.Categorical(logits=logits)
        logpas = dist.log_prob(actions.squeeze())
        entropies = dist.entropy()
        return logpas, entropies

class CNNCritic(CNNBase):
    def __init__(self, observation_space, num_stack, **kwargs):
        super().__init__(observation_space, num_stack, **kwargs)
        self.critic_head = nn.Linear(self.feature_dim, 1)
        
        device = "cuda:0" if torch.cuda.is_available() else "cpu"
        self.device = torch.device(device)
        self.to(self.device)

    def _format_obs(self, obs: dict):
        return {
            'grid': torch.tensor(np.array(obs['grid']), dtype=torch.float32, device=self.device),
            'vector': torch.tensor(np.array(obs['vector']), dtype=torch.float32, device=self.device)
        }

    def forward(self, states):
        if not isinstance(states['grid'], torch.Tensor):
            states = self._format_obs(states)
            
        features = super().forward(states)
        values = self.critic_head(features)
        return values.squeeze(-1)