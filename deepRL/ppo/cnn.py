import torch
import torch.nn as nn
import torch.nn.functional as F
import numpy as np
from gymnasium.spaces import Dict

class CNNBase(nn.Module):
    def __init__(self, observation_space: Dict, num_stack: int, hidden_dims=(256, 256)):
        super(CNNBase, self).__init__()

        self.grid_keys = [
            'gridSolid', 'gridBlocks', 'gridCoins', 'gridGoomba', 'gridGoombaWing',
            'gridGreenKoompa', 'gridGreenKoompaWing', 'gridRedKoompa', 'gridRedKoompaWing',
            'gridSpiky', 'gridSpikyWing', 'gridEnemyFlower', 'gridShell', 'gridBulletBill',
            'gridMushroom', 'gridFirepower', 'gridLifeMushroom', 'gridBrick',
            'gridSemiSolid', 'gridFlags', 'gridFireball'
        ]
        
        total_grid_channels = len(self.grid_keys) * num_stack
        self.cnn = nn.Sequential(
            nn.Conv2d(total_grid_channels, 32, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.Conv2d(32, 64, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(kernel_size=2, stride=2),
            nn.Conv2d(64, 64, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(kernel_size=2, stride=2),
            nn.Flatten(),
        )
        cnn_feature_size = 64 * 4 * 4
        vector_shape = observation_space['vector'].shape
        self.mlp = nn.Sequential(
            nn.Linear(vector_shape[0], 64),
            nn.ReLU()
        )
        self.combined_mlp = nn.Sequential(
            nn.Linear(cnn_feature_size + 64, hidden_dims[0]),
            nn.ReLU(),
            nn.Linear(hidden_dims[0], hidden_dims[1]),
            nn.ReLU(),
        )
        self.feature_dim = hidden_dims[1]

    def forward(self, states: dict):
        combined_grid = torch.cat([states[key].float() for key in self.grid_keys], dim=1)
        cnn_out = self.cnn(combined_grid)
        mlp_out = self.mlp(states['vector'])
        combined_features = torch.cat([cnn_out, mlp_out], dim=1)
        final_features = self.combined_mlp(combined_features)
        return final_features

class CNNActor(CNNBase):
    def __init__(self, observation_space, output_dim, num_stack, hidden_dims=(256, 256)):
        super(CNNActor, self).__init__(observation_space, num_stack, hidden_dims)
        self.actor_head = nn.Linear(self.feature_dim, output_dim)
        device = "cpu"
        if torch.cuda.is_available():
            device = "cuda:0"
        self.device = torch.device(device)
        self.to(self.device)

    def _format_obs(self, obs: dict):
        return {
            key: torch.tensor(val, dtype=torch.float32, device=self.device)
            for key, val in obs.items()
        }

    def _format_single_obs(self, obs: dict):
        return {
            key: torch.tensor(val, dtype=torch.float32, device=self.device).unsqueeze(0)
            for key, val in obs.items()
        }

    def forward(self, states, is_batched=False):
        if not is_batched:
            states = self._format_single_obs(states)
        elif not isinstance(states['gridSolid'], torch.Tensor):
            states = self._format_obs(states)
        features = super().forward(states)
        logits = self.actor_head(features)
        return logits

    def np_pass(self, states):
        logits = self.forward(states, is_batched=True)
        dist = torch.distributions.Categorical(logits=logits)
        actions = dist.sample()
        np_actions = actions.detach().cpu().numpy()
        logpas = dist.log_prob(actions)
        np_logpas = logpas.detach().cpu().numpy()
        np_logits = logits.detach().cpu().numpy()
        is_exploratory = np_actions != np.argmax(np_logits, axis=1)
        return np_actions, np_logpas, is_exploratory
    
    def get_predictions(self, states, actions):
        if not isinstance(actions, torch.Tensor):
            actions = torch.tensor(actions, device=self.device)
        logits = self.forward(states, is_batched=True)
        dist = torch.distributions.Categorical(logits=logits)
        logpas = dist.log_prob(actions.squeeze())
        entropies = dist.entropy()
        return logpas, entropies
    
    def select_action(self, obs: dict):
        logits = self.forward(obs, is_batched=False)
        dist = torch.distributions.Categorical(logits=logits)
        action = dist.sample()
        return action.item()

    def select_greedy_action(self, obs: dict):
        logits = self.forward(obs, is_batched=False)
        action = np.argmax(logits.detach().cpu().numpy()[0])
        return action

class CNNCritic(CNNBase):
    def __init__(self, observation_space, num_stack, hidden_dims=(256, 256)):
        super(CNNCritic, self).__init__(observation_space, num_stack, hidden_dims)
        self.critic_head = nn.Linear(self.feature_dim, 1)
        device = "cpu"
        if torch.cuda.is_available():
            device = "cuda:0"
        self.device = torch.device(device)
        self.to(self.device)

    def forward(self, states):
        if not isinstance(states['gridSolid'], torch.Tensor):
            states = {
                key: torch.tensor(val, dtype=torch.float32, device=self.device)
                for key, val in states.items()
            }
        features = super().forward(states)
        values = self.critic_head(features)
        return values.squeeze(-1)