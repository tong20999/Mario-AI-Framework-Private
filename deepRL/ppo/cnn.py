import torch
import torch.nn as nn
import numpy as np

class ResidualBlock3D(nn.Module):
    def __init__(self, channels):
        super().__init__()
        self.conv1 = nn.Conv3d(channels, channels, 3, padding=1)
        self.conv2 = nn.Conv3d(channels, channels, 3, padding=1)
        self.act = nn.ReLU(inplace=True)

    def forward(self, x):
        out = self.act(self.conv1(x))
        out = self.conv2(out)
        return self.act(out + x)

class CNNBase(nn.Module):
    def __init__(self, num_stack: int = 4, num_object_types: int = 22,
                 embedding_dim: int = 16):
        super().__init__()

        # --- 1. Embedding (semantic token representation) ---
        self.embedding = nn.Embedding(num_embeddings=num_object_types, embedding_dim=embedding_dim)
        self.num_stack = num_stack
        
        c1 = 16

        # --- 2. 3D Convolutional Path ---
        self.cnn = nn.Sequential(
            nn.Conv3d(embedding_dim, c1, kernel_size=1),
            ResidualBlock3D(c1),
            ResidualBlock3D(c1),
        )

        grid_feature_dim = (num_stack * c1) * 16 * 16  # after temporal mean

        # --- 3. Vector Path ---
        self.mario_phys_dim = 22          # first 6B + 16f
        self.objective_dim = 90           # three 30-length integer lists
        self.total_vector_dim = self.mario_phys_dim + self.objective_dim
        # (Optional safety) you can assert at runtime if desired:
        # assert self.total_vector_dim == 112, "Vector length mismatch."

        self.mario_mlp = nn.Sequential(
            nn.Linear(self.mario_phys_dim, 64),
            nn.ReLU(),
            nn.Linear(64, 64),
            nn.ReLU()
        )
        self.objective_mlp = nn.Sequential(
            nn.Linear(self.objective_dim, 128), 
            nn.ReLU(),
            nn.Linear(128, 128),
            nn.ReLU(),
        )
        vector_feature_dim = 64 + 128

        # --- 4. Fusion ---
        self.combined_dim = grid_feature_dim + vector_feature_dim

    def forward(self, states: dict):
        grid = states['grid']
        vec  = states['vector']

        emb = self.embedding(grid.long())                 # (B,S,H,W,E)
        x = emb.permute(0, 4, 1, 2, 3).contiguous()       # (B,E,S,H,W)
        x = self.cnn(x)                                   # (B,C,S,H,W)

        B,C,S,H,W = x.shape
        x = x.view(B, C*S, H, W)
        grid_feat = x.flatten(1)

        mario_feat = self.mario_mlp(vec[:, :self.mario_phys_dim])
        objective_feat = self.objective_mlp(vec[:, self.mario_phys_dim:])
        return torch.cat([grid_feat, mario_feat, objective_feat], dim=1)


class CNNActor(nn.Module):
    def __init__(self, output_dim, num_stack: int, **kwargs):
        super(CNNActor, self).__init__()
        self.features = CNNBase(num_stack=num_stack, **kwargs)
        hidden_dim = [512, 512]
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
        hidden_dim = [512, 512]
        self.features = CNNBase(num_stack=num_stack, **kwargs)
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
            'grid': torch.tensor(np.array(obs['grid']), dtype=torch.long, device=self.device),
            'vector': torch.tensor(np.array(obs['vector']), dtype=torch.float32, device=self.device)
        }

    def forward(self, states):
        if not isinstance(states['grid'], torch.Tensor):
            states = self._format_obs(states)
            
        features = self.features.forward(states)
        values = self.critic_head(features)
        return values.squeeze(-1)