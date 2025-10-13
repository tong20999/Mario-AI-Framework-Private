import torch
import torch.nn as nn
import numpy as np
from gymnasium.spaces import Dict

class ResidualBlock3D(nn.Module):
    def __init__(self, channels):
        super(ResidualBlock3D, self).__init__()
        self.conv0 = nn.Conv3d(
            in_channels=channels, out_channels=channels, kernel_size=3, padding=1
        )
        self.relu = nn.ReLU(inplace=True)
        self.conv1 = nn.Conv3d(
            in_channels=channels, out_channels=channels, kernel_size=3, padding=1
        )

    def forward(self, x):
        identity = x
        
        out = self.relu(self.conv0(x))
        out = self.conv1(out)
        
        out = self.relu(out + identity)
        return out

class CNNBase(nn.Module):
    def __init__(self, num_stack: int = 4, num_object_types: int = 22,
                 embedding_dim: int = 8):
        super().__init__()

        # --- 1. Embedding (semantic token representation) ---
        self.embedding = nn.Embedding(num_embeddings=num_object_types, embedding_dim=embedding_dim)
        
        # The number of input channels for Conv3d is the embedding dimension.
        # The num_stack becomes the "depth" of our 3D data.
        in_channels = embedding_dim * num_stack
        c1 = 16

        # --- 2. 3D Convolutional Path ---
        # We replace Conv2d with Conv3d.
        self.cnn = nn.Sequential(
            nn.Conv2d(in_channels, c1, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.Flatten()
        )

        # Calculate the output size after flattening.
        # The Conv3d with this padding preserves the D, H, W dimensions.
        # So the output shape before flattening is (B, size, num_stack, 16, 16).
        grid_feature_dim = c1 * 16 * 16

        # --- 3. Vector Path (unchanged) ---
        mario_phys_dim = 22
        objective_dim = 90
        self.mario_mlp = nn.Sequential(nn.Linear(mario_phys_dim, 64), nn.ReLU())
        self.objective_mlp = nn.Sequential(nn.Linear(objective_dim, 128), nn.ReLU())
        vector_feature_dim = 64 + 128

        # --- 4. Fusion ---
        self.combined_dim = grid_feature_dim + vector_feature_dim

    def forward(self, states: dict):
        # GRID branch
        grid_obs = states['grid']                          # (B, S, 16, 16)
        embedded = self.embedding(grid_obs.long())         # (B, S, 16, 16, E)

        # Pack time (S) and embedding (E) into channels: (B,S,E,H,W) -> (B, S*E, H, W)
        x = embedded.permute(0, 1, 4, 2, 3).contiguous()   # (B, S, E, 16, 16)
        B, S, E, H, W = x.shape
        x = x.view(B, S * E, H, W)                         # (B, in_channels=S*E, 16, 16)

        grid_features = self.cnn(x)                        # (B, c1*16*16) after Flatten

        # VECTOR branch
        vector_obs      = states['vector']                 # (B, 103)
        mario_phys_vec  = vector_obs[:, :22]
        objective_vec   = vector_obs[:, 22:]
        mario_features  = self.mario_mlp(mario_phys_vec)
        objective_feats = self.objective_mlp(objective_vec)

        # FUSE
        combined_features = torch.cat([grid_features, mario_features, objective_feats], dim=1)
        return combined_features


class CNNActor(nn.Module):
    def __init__(self, output_dim, num_stack: int, **kwargs):
        super(CNNActor, self).__init__()
        self.features = CNNBase(num_stack=num_stack)
        hidden_dim = [512]
        self.actor_head = nn.Sequential(
            nn.Linear(self.features.combined_dim, hidden_dim[0]),
            nn.ReLU(),
            nn.Linear(hidden_dim[0], output_dim)
        )
        
        device = "cuda:0" if torch.cuda.is_available() else "cpu"
        self.device = torch.device(device)
        self.to(self.device)

    def _format_obs(self, obs: dict):
        return {
            'grid': torch.tensor(np.array(obs['grid']), dtype=torch.long, device=self.device),
            'vector': torch.tensor(np.array(obs['vector']), dtype=torch.float32, device=self.device)
        }

    def _format_single_obs(self, obs: dict):
        return {
            'grid': torch.tensor(obs['grid'], dtype=torch.long, device=self.device).unsqueeze(0),
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
        hidden_dim = [512]
        self.features = CNNBase(num_stack=num_stack)
        self.critic_head = nn.Sequential(
            nn.Linear(self.features.combined_dim, hidden_dim[0]),
            nn.ReLU(),
            nn.Linear(hidden_dim[0], 1)
        )
        
        device = "cuda:0" if torch.cuda.is_available() else "cpu"
        self.device = torch.device(device)
        self.to(self.device)

    def _format_obs(self, obs: dict):
        return {
            'grid': torch.tensor(np.array(obs['grid']), dtype=torch.long, device=self.device),
            'vector': torch.tensor(np.array(obs['vector']), dtype=torch.float32, device=self.device)
        }

    def forward(self, states):
        if not isinstance(states['grid'], torch.Tensor):
            states = self._format_obs(states)
            
        features = self.features.forward(states)
        values = self.critic_head(features)
        return values.squeeze(-1)