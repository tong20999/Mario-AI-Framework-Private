import torch
import torch.nn as nn
import numpy as np
from gymnasium.spaces import Dict


class CNNBase(nn.Module):
    def __init__(self, observation_space: Dict, num_stack: int = 3, num_object_types: int = 24,
                 embedding_dim: int = 8, hidden_sizes=(512, 256)):
        super().__init__()

        # --- Embedding Layer for Grid Input ---
        self.embedding = nn.Embedding(num_embeddings=num_object_types, embedding_dim=embedding_dim)

        cnn_input_channels = embedding_dim * num_stack
        self.cnn = nn.Sequential(
            nn.Conv2d(cnn_input_channels, 32, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.Conv2d(32, 64, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.Flatten()
        )

        cnn_output_channels = 64
        cnn_feature_size = cnn_output_channels * 16 * 16

        # Vector input size
        vector_shape = observation_space['vector'].shape
        vector_size = vector_shape[0]

        # --- Hidden MLP after concatenation ---
        combined_input_size = cnn_feature_size + vector_size
        self.mlp = nn.Sequential(
            nn.Linear(combined_input_size, hidden_sizes[0]),
            nn.ReLU(),
            nn.Linear(hidden_sizes[0], hidden_sizes[1]),
            nn.ReLU()
        )

        self.feature_dim = hidden_sizes[1]

    def forward(self, states: dict):
        # Grid path: expects a tensor of integer IDs
        grid_obs = states['grid']
        batch_size, num_stack, height, width = grid_obs.shape

        # Apply embedding layer and reshape for the CNN
        embedded_grid = self.embedding(grid_obs.long())
        embedded_grid = embedded_grid.view(batch_size, num_stack, height, width, -1)
        embedded_grid = embedded_grid.permute(0, 1, 4, 2, 3)
        cnn_input = embedded_grid.reshape(batch_size, num_stack * self.embedding.embedding_dim, height, width)
        grid_features = self.cnn(cnn_input)

        # Vector path
        vector_features = states['vector']

        # Concatenate and pass through MLP
        combined = torch.cat([grid_features, vector_features], dim=1)
        features = self.mlp(combined)
        return features
    

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