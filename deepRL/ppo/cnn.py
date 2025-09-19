import torch
import torch.nn as nn
import numpy as np
from gymnasium.spaces import Dict

class ResidualBlock(nn.Module):
    """
    A simple residual block with two convolutional layers.
    The input to the block is added to its output, creating a skip connection.
    """
    def __init__(self, channels):
        super(ResidualBlock, self).__init__()
        self.conv0 = nn.Conv2d(in_channels=channels, out_channels=channels, kernel_size=3, padding=1)
        self.relu = nn.ReLU(inplace=True)
        self.conv1 = nn.Conv2d(in_channels=channels, out_channels=channels, kernel_size=3, padding=1)

    def forward(self, x):
        identity = x  # Save the input for the skip connection
        out = self.relu(self.conv0(x))
        out = self.conv1(out)
        # Add the original input (identity) to the output of the convolutions
        out = self.relu(out + identity)
        return out

# --- NEW, MORE POWERFUL (but simplified) CNNBase ---
class CNNBase(nn.Module):
    def __init__(self, observation_space: Dict, num_stack: int = 3, num_object_types: int = 24,
                 embedding_dim: int = 16, hidden_sizes=(256, 128)): # Increased defaults
        super().__init__()

        # --- 1. Enhanced Grid (Vision) Processing Stream ---
        # Increased embedding_dim for richer object representation
        self.embedding = nn.Embedding(num_embeddings=num_object_types, embedding_dim=embedding_dim)
        cnn_input_channels = embedding_dim * num_stack
        
        # Deeper and Wider CNN
        self.cnn = nn.Sequential(
            nn.Conv2d(cnn_input_channels, 128, kernel_size=3, stride=1, padding=1), # Wider
            nn.ReLU(),
            nn.Conv2d(128, 256, kernel_size=3, stride=2, padding=1), # Wider, 16x16 -> 8x8
            nn.ReLU(),
            ResidualBlock(256),
            ResidualBlock(256), # Deeper: Added another residual block
            nn.Conv2d(256, 256, kernel_size=3, stride=2, padding=1), # 8x8 -> 4x4
            nn.ReLU(),
            ResidualBlock(256),
            nn.AdaptiveAvgPool2d(1)
        )
        cnn_feature_size = 256 # Updated feature size

        # --- 2. Enhanced Vector (State Info) Processing Stream ---
        vector_shape = observation_space['vector'].shape
        vector_size = vector_shape[0]
        # Wider MLP for vector data
        self.vector_mlp = nn.Sequential(
            nn.Linear(vector_size, hidden_sizes[0]),
            nn.ReLU(),
            nn.Linear(hidden_sizes[0], hidden_sizes[1]),
            nn.ReLU()
        )
        vector_feature_size = hidden_sizes[1] # Updated feature size

        # The total feature dimension is now the sum of the two streams
        self.feature_dim = cnn_feature_size + vector_feature_size

    def forward(self, states: dict):
        # --- Grid Path ---
        grid_obs = states['grid']
        batch_size, num_stack, height, width = grid_obs.shape
        embedded_grid = self.embedding(grid_obs.long())
        embedded_grid = embedded_grid.view(batch_size, num_stack, height, width, -1)
        embedded_grid = embedded_grid.permute(0, 1, 4, 2, 3)
        cnn_input = embedded_grid.reshape(batch_size, num_stack * self.embedding.embedding_dim, height, width)
        grid_features = self.cnn(cnn_input).view(batch_size, -1)

        # --- Vector Path ---
        vector_input = states['vector']
        vector_features = self.vector_mlp(vector_input)

        # --- 3. Simple Concatenation Fusion ---
        final_features = torch.cat([grid_features, vector_features], dim=1)

        return final_features


class CNNActor(CNNBase):
    """Actor network for PPO."""
    def __init__(self, observation_space, output_dim, **kwargs):
        super().__init__(observation_space, **kwargs)
        self.actor_head = nn.Linear(self.feature_dim, output_dim)

        device = "cuda:0" if torch.cuda.is_available() else "cpu"
        self.device = torch.device(device)
        self.to(self.device)

    def _format_obs(self, obs: dict):
        return {
            'grid': torch.tensor(obs['grid'], dtype=torch.long, device=self.device),
            'vector': torch.tensor(obs['vector'], dtype=torch.float32, device=self.device)
        }

    def _format_single_obs(self, obs: dict):
        return {
            'grid': torch.tensor(obs['grid'], dtype=torch.long, device=self.device).unsqueeze(0),
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
        return dist.sample().item()

    def select_greedy_action(self, obs: dict):
        states = self._format_single_obs(obs)
        logits = self.forward(states)
        return torch.argmax(logits, dim=-1).item()

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
    """Critic network for PPO."""
    def __init__(self, observation_space, **kwargs):
        super().__init__(observation_space, **kwargs)
        self.critic_head = nn.Linear(self.feature_dim, 1)

        device = "cuda:0" if torch.cuda.is_available() else "cpu"
        self.device = torch.device(device)
        self.to(self.device)

    def _format_obs(self, obs: dict):
        return {
            'grid': torch.tensor(obs['grid'], dtype=torch.long, device=self.device),
            'vector': torch.tensor(obs['vector'], dtype=torch.float32, device=self.device)
        }

    def forward(self, states):
        if not isinstance(states['grid'], torch.Tensor):
            states = self._format_obs(states)
        features = super().forward(states)
        values = self.critic_head(features)
        return values.squeeze(-1)