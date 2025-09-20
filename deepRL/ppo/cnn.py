import torch
import torch.nn as nn
import numpy as np
from gymnasium.spaces import Dict

class CNNBase(nn.Module):
    def __init__(self, observation_space: Dict, num_stack: int, num_object_types: int = 21,
                 hidden_dims=(256, 128)): # Reduced hidden dimensions
        super(CNNBase, self).__init__()

        cnn_input_channels = num_object_types * num_stack

        # --- Path 1: A simpler CNN for Grid Processing ---
        self.cnn = nn.Sequential(
            # Two simpler Conv layers are often enough for semantic grids
            nn.Conv2d(cnn_input_channels, out_channels=32, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.Conv2d(in_channels=32, out_channels=64, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.Flatten()
        )

        # Calculate the flattened feature size from the CNN
        with torch.no_grad():
            dummy_input = torch.zeros(1, cnn_input_channels, 16, 16)
            cnn_feature_size = self.cnn(dummy_input).shape[1]

        # --- Path 2: A simpler MLP for Vector Processing ---
        vector_shape = observation_space['vector'].shape
        self.vector_mlp = nn.Sequential(
            nn.Linear(vector_shape[0], 128), # Reduced size
            nn.ReLU(),
        )

        # --- Early Fusion Head ---
        # Combine the features from CNN and vector MLP much earlier
        combined_input_size = cnn_feature_size + 128

        self.final_mlp = nn.Sequential(
            nn.Linear(combined_input_size, hidden_dims[0]),
            nn.ReLU(),
            # No need for the second hidden layer if the first is small enough
            # nn.Linear(hidden_dims[0], hidden_dims[1]),
            # nn.ReLU(),
        )

        self.feature_dim = hidden_dims[0] # The final feature dimension

    def forward(self, states: dict):
        grid_obs = states['grid']
        batch_size, num_stack, num_planes, height, width = grid_obs.shape
        cnn_input = grid_obs.view(batch_size, num_stack * num_planes, height, width)
        grid_features = self.cnn(cnn_input)

        vector_features = self.vector_mlp(states['vector'])

        combined_features = torch.cat([grid_features, vector_features], dim=1)

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
            'grid': torch.tensor(obs['grid'], dtype=torch.float32, device=self.device),
            'vector': torch.tensor(obs['vector'], dtype=torch.float32, device=self.device)
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
            'grid': torch.tensor(obs['grid'], dtype=torch.float32, device=self.device),
            'vector': torch.tensor(obs['vector'], dtype=torch.float32, device=self.device)
        }

    def forward(self, states):
        if not isinstance(states['grid'], torch.Tensor):
            states = self._format_obs(states)
            
        features = super().forward(states)
        values = self.critic_head(features)
        return values.squeeze(-1)