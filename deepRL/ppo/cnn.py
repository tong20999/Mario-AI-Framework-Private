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
    def __init__(self, num_stack: int = 4, num_object_types: int = 14,
                 embedding_dim: int = 8):
        super().__init__()

        # --- 1. Embedding (semantic token representation) ---
        self.embedding = nn.Embedding(num_embeddings=num_object_types, embedding_dim=embedding_dim)
        in_channels = embedding_dim * num_stack
        size = 128
        # Multi-scale path (Architecture B):
        # 16x16 -> (res block) -> 16x16 -> stride2 -> 8x8 -> res -> stride2 -> 4x4 -> res
        self.cnn = nn.Sequential(
            # Stem @16x16
            nn.Conv2d(in_channels, size, kernel_size=3, padding=1),
            nn.ReLU(inplace=True),
            ResidualBlock(size),  # Stage 1 full resolution
            # Downsample to 8x8
            nn.Conv2d(size, size, kernel_size=3, stride=2, padding=1),
            nn.ReLU(inplace=True),
            ResidualBlock(size),  # Stage 2 @8x8
            # Downsample to 4x4
            nn.Conv2d(size, size, kernel_size=3, stride=2, padding=1),
            nn.ReLU(inplace=True),
            ResidualBlock(size)   # Stage 3 @4x4
        )

        self.flatten = nn.Flatten()
        grid_feature_dim = size * 4 * 4  # final spatial size 4x4

        # --- 3. Vector Path (unchanged) ---
        mario_phys_dim = 12
        objective_dim = 90
        self.mario_mlp = nn.Sequential(nn.Linear(mario_phys_dim, 64), nn.ReLU())
        self.objective_mlp = nn.Sequential(nn.Linear(objective_dim, 128), nn.ReLU())
        vector_feature_dim = 64 + 128

        # --- 4. Fusion ---
        self.combined_dim = grid_feature_dim + vector_feature_dim

    def forward(self, states: dict):
        grid_obs = states['grid']
        batch_size, num_stack, height, width = grid_obs.shape
        embedded_grid = self.embedding(grid_obs.long())  # (B, S, H, W, E)
        embedded_grid = embedded_grid.view(batch_size, num_stack, height, width, -1).permute(0, 1, 4, 2, 3)
        cnn_input = embedded_grid.reshape(batch_size, num_stack * self.embedding.embedding_dim, height, width)
        x = self.cnn(cnn_input)
        grid_features = self.flatten(x)

        # --- Vector Path (unchanged) ---
        vector_obs = states['vector']
        mario_phys_vec = vector_obs[:, :12]
        objective_vec = vector_obs[:, 12:]

        mario_features = self.mario_mlp(mario_phys_vec)
        objective_features = self.objective_mlp(objective_vec)
        
        # --- Fusion ---
        combined_features = torch.cat([grid_features, mario_features, objective_features], dim=1)
        
        return combined_features

class CNNActor(nn.Module):
    def __init__(self, output_dim, num_stack: int, **kwargs):
        super(CNNActor, self).__init__()
        self.features = CNNBase(num_stack=num_stack)
        hidden_dim = [512, 256]
        self.actor_head = nn.Sequential(
            nn.Linear(self.features.combined_dim, hidden_dim[0]),
            nn.ReLU(),
            nn.Linear(hidden_dim[0], hidden_dim[1]),
            nn.ReLU(),
            nn.Linear(hidden_dim[1], output_dim)
        )
        
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
        features = self.features.forward(states)
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

class CNNCritic(nn.Module):
    def __init__(self, num_stack: int, **kwargs):
        super(CNNCritic, self).__init__()
        hidden_dim = [512, 256]
        self.features = CNNBase(num_stack=num_stack)
        self.critic_head = nn.Sequential(
            nn.Linear(self.features.combined_dim, hidden_dim[0]),
            nn.ReLU(),
            nn.Linear(hidden_dim[0], hidden_dim[1]),
            nn.ReLU(),
            nn.Linear(hidden_dim[1], 1)
        )
        
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
            
        features = self.features.forward(states)
        values = self.critic_head(features)
        return values.squeeze(-1)