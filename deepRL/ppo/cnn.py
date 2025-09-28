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

class CNNBase(nn.Module):
    def __init__(self, num_stack: int = 4, num_object_types: int = 21,
                 embedding_dim: int = 8):
        super().__init__()

        # --- 1. Grid Embedding Path ---
        self.embedding = nn.Embedding(num_embeddings=num_object_types, embedding_dim=embedding_dim)
        grid_channels = embedding_dim * num_stack

        # --- 2. Vector Processing Path (unchanged) ---
        mario_phys_dim = 13
        objective_dim = 90
        self.mario_mlp = nn.Sequential(nn.Linear(mario_phys_dim, 64), nn.ReLU())
        self.objective_mlp = nn.Sequential(nn.Linear(objective_dim, 128), nn.ReLU())
        
        # --- 3. Early Fusion Setup ---
        # We will add the processed vector features as extra channels to the grid.
        # Let's define how many extra channels the vector data will contribute.
        vector_channels = 16  # This is a new hyperparameter you can tune.

        # A linear layer to project the combined vector features into the desired channel size.
        combined_vector_dim = 64 + 128
        self.vector_projector = nn.Linear(combined_vector_dim, vector_channels)

        # --- 4. Unified CNN Path ---
        # The CNN's input now includes channels from the grid AND the projected vector.
        total_in_channels = grid_channels + vector_channels
        
        size = 32  # Number of output channels for the first conv layer
        self.cnn = nn.Sequential(
            nn.Conv2d(total_in_channels, size, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.Flatten()
        )

        # The final output dimension is determined by the CNN's flattened output.
        # This is now the single source of features.
        self.combined_dim = size * 16 * 16

    def forward(self, states: dict):
        # --- Process Grid Input ---
        grid_obs = states['grid']
        batch_size, num_stack, height, width = grid_obs.shape
        embedded_grid = self.embedding(grid_obs.long())
        embedded_grid = embedded_grid.view(batch_size, num_stack, height, width, -1).permute(0, 1, 4, 2, 3)
        grid_features_spatial = embedded_grid.reshape(batch_size, num_stack * self.embedding.embedding_dim, height, width)

        # --- Process Vector Input ---
        vector_obs = states['vector']
        mario_phys_vec = vector_obs[:, :13]
        objective_vec = vector_obs[:, 13:]
        mario_features = self.mario_mlp(mario_phys_vec)
        objective_features = self.objective_mlp(objective_vec)
        combined_vector_features = torch.cat([mario_features, objective_features], dim=1)

        # --- EARLY FUSION STEP ---
        # Project the vector features to the desired channel size.
        projected_vector = self.vector_projector(combined_vector_features) # Shape: (B, vector_channels)

        # "Tile" the projected vector across the spatial dimensions of the grid.
        # We expand its dimensions to match the grid's HxW.
        tiled_vector_features = projected_vector.unsqueeze(-1).unsqueeze(-1) # Shape: (B, vector_channels, 1, 1)
        tiled_vector_features = tiled_vector_features.expand(-1, -1, height, width) # Shape: (B, vector_channels, H, W)

        # Concatenate the grid and tiled vector features along the channel dimension.
        fused_input = torch.cat([grid_features_spatial, tiled_vector_features], dim=1)
        
        # --- Pass the fused representation through the single CNN ---
        combined_features = self.cnn(fused_input)
        
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