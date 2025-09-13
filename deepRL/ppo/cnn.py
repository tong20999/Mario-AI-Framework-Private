import torch
import torch.nn as nn
import numpy as np
from gymnasium.spaces import Dict

class CNNBase(nn.Module):
    def __init__(self, observation_space: Dict, num_stack: int, num_object_types: int = 20, 
                 hidden_dims=(512, 512)):
        super(CNNBase, self).__init__()

        cnn_input_channels = num_object_types * num_stack
        
        # --- Path 1: CNN for Grid Processing ---
        self.cnn = nn.Sequential(
            nn.Conv2d(cnn_input_channels, 64, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.Conv2d(64, 128, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.Conv2d(128, 128, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.Flatten()
        )
        
        # This MLP processes the CNN's output
        cnn_feature_size = 128 * 16 * 16
        self.grid_mlp = nn.Sequential(
            nn.Linear(cnn_feature_size, hidden_dims[0]),
            nn.ReLU()
        )

        # --- Path 2: MLP for Vector Processing ---
        vector_shape = observation_space['vector'].shape
        self.vector_mlp = nn.Sequential(
            nn.Linear(vector_shape[0], 256),
            nn.ReLU(),
            # Added a second layer to make the vector path deeper
            nn.Linear(256, hidden_dims[0]),
            nn.ReLU()
        )

        # --- Late Fusion Head ---
        # Combine the outputs of the two deep paths
        combined_input_size = hidden_dims[0] + hidden_dims[0] # From grid_mlp and vector_mlp

        self.final_mlp = nn.Sequential(
            nn.Linear(combined_input_size, hidden_dims[1]),
            nn.ReLU(),
        )

        self.feature_dim = hidden_dims[1]

    def forward(self, states: dict):
        # --- Process Grid and Vector in Separate, Deeper Streams ---
        
        # 1. Grid Path
        grid_obs = states['grid']
        batch_size, num_stack, num_planes, height, width = grid_obs.shape
        cnn_input = grid_obs.view(batch_size, num_stack * num_planes, height, width)
        grid_features_raw = self.cnn(cnn_input)
        grid_summary = self.grid_mlp(grid_features_raw)
        
        # 2. Vector Path
        vector_summary = self.vector_mlp(states['vector'])
        
        # --- Fuse the high-level summaries LATE in the process ---
        combined_features = torch.cat([grid_summary, vector_summary], dim=1)
        
        final_output = self.final_mlp(combined_features)
        
        return final_output

class CNNActor(CNNBase):
    """
    The Actor network. Inherits the updated CNNBase.
    """
    def __init__(self, observation_space, output_dim, num_stack, **kwargs):
        super().__init__(observation_space, num_stack, hidden_dims=(512, 512), **kwargs)
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

    def forward(self, states, is_batched=False):
        if not is_batched:
            states = self._format_single_obs(states)
        elif not isinstance(states['grid'], torch.Tensor):
            states = self._format_obs(states)
            
        features = super().forward(states)
        logits = self.actor_head(features)
        return logits

    def np_pass(self, states):
        logits = self.forward(states, is_batched=True)
        dist = torch.distributions.Categorical(logits=logits)
        actions = dist.sample()
        logpas = dist.log_prob(actions)
        
        np_actions = actions.detach().cpu().numpy()
        np_logpas = logpas.detach().cpu().numpy()
        np_logits = logits.detach().cpu().numpy()
        
        is_exploratory = np_actions != np.argmax(np_logits, axis=1)
        
        return np_actions, np_logpas, is_exploratory

    def select_action(self, obs: dict):
        logits = self.forward(obs, is_batched=False)
        dist = torch.distributions.Categorical(logits=logits)
        action = dist.sample()
        return action.item()

    def select_greedy_action(self, obs: dict):
        logits = self.forward(obs, is_batched=False)
        action = torch.argmax(logits, dim=-1)
        return action.item()

    def get_predictions(self, states, actions):
        if not isinstance(actions, torch.Tensor):
            actions = torch.tensor(actions, device=self.device)
        
        logits = self.forward(states, is_batched=True)
        dist = torch.distributions.Categorical(logits=logits)
        logpas = dist.log_prob(actions.squeeze())
        entropies = dist.entropy()
        return logpas, entropies

class CNNCritic(CNNBase):
    """
    The Critic network. Inherits the updated CNNBase.
    """
    def __init__(self, observation_space, num_stack, **kwargs):
        super().__init__(observation_space, num_stack, hidden_dims=(512, 512), **kwargs)
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