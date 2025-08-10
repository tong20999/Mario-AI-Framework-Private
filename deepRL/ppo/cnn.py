import torch
import torch.nn as nn
import torch.nn.functional as F
import numpy as np
from gymnasium.spaces import Dict

class CNNBase(nn.Module):
    def __init__(self, observation_space: Dict, embedding_dim: int = 16, hidden_dims=(256, 256)):
        super(CNNBase, self).__init__()
        scene_vocab_size = 3
        enemy_vocab_size = 2
        self.scene_embedding = nn.Embedding(scene_vocab_size, embedding_dim)
        self.enemy_embedding = nn.Embedding(enemy_vocab_size, embedding_dim)
        scene_channels = observation_space['gridScene'].shape[0]
        enemy_channels = observation_space['gridEnemies'].shape[0]
        scene_in = embedding_dim * scene_channels
        enemy_in = embedding_dim * enemy_channels
        self.scene_stream = nn.Sequential(
            nn.Conv2d(scene_in, 32, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
        )
        self.enemy_stream = nn.Sequential(
            nn.Conv2d(enemy_in, 32, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
        )
        self.shared_cnn = nn.Sequential(
            nn.MaxPool2d(kernel_size=2, stride=2),
            nn.Conv2d(64, 64, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.MaxPool2d(kernel_size=2, stride=2),
            nn.Conv2d(64, 64, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
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
        scene_grid = states['gridScene'].long()
        batch_size, channels, height, width = scene_grid.shape
        embedded_scene = self.scene_embedding(scene_grid).permute(0, 1, 4, 2, 3).reshape(batch_size, channels * self.scene_embedding.embedding_dim, height, width)
        enemy_grid = states['gridEnemies'].long()
        batch_size_e, channels_e, height_e, width_e = enemy_grid.shape
        embedded_enemy = self.enemy_embedding(enemy_grid).permute(0, 1, 4, 2, 3).reshape(batch_size_e, channels_e * self.enemy_embedding.embedding_dim, height_e, width_e)
        s = self.scene_stream(embedded_scene)
        e = self.enemy_stream(embedded_enemy)
        fused = torch.cat([s, e], dim=1)
        cnn_out = self.shared_cnn(fused)
        mlp_out = self.mlp(states['vector'])
        combined_features = torch.cat([cnn_out, mlp_out], dim=1)
        final_features = self.combined_mlp(combined_features)
        return final_features

class CNNActor(CNNBase):
    def __init__(self, observation_space, output_dim, embedding_dim=16, hidden_dims=(256, 256)):
        super(CNNActor, self).__init__(observation_space, embedding_dim, hidden_dims)
        self.actor_head = nn.Linear(self.feature_dim, output_dim)
        device = "cpu"
        if torch.cuda.is_available():
            device = "cuda:0"
        self.device = torch.device(device)
        self.to(self.device)

    def _format_obs(self, obs: dict):
        return {
            'gridScene': torch.tensor(obs['gridScene'], dtype=torch.long, device=self.device),
            'gridEnemies': torch.tensor(obs['gridEnemies'], dtype=torch.long, device=self.device),
            'vector': torch.tensor(obs['vector'], dtype=torch.float32, device=self.device)
        }

    def _format_single_obs(self, obs: dict):
        return {
            'gridScene': torch.tensor(obs['gridScene'], dtype=torch.long, device=self.device).unsqueeze(0),
            'gridEnemies': torch.tensor(obs['gridEnemies'], dtype=torch.long, device=self.device).unsqueeze(0),
            'vector': torch.tensor(obs['vector'], dtype=torch.float32, device=self.device).unsqueeze(0)
        }

    def forward(self, states, is_batched=False):
        if not is_batched:
            states = self._format_single_obs(states)
        elif not isinstance(states['gridScene'], torch.Tensor):
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
    def __init__(self, observation_space, embedding_dim=16, hidden_dims=(256, 256)):
        super(CNNCritic, self).__init__(observation_space, embedding_dim, hidden_dims)
        self.critic_head = nn.Linear(self.feature_dim, 1)
        device = "cpu"
        if torch.cuda.is_available():
            device = "cuda:0"
        self.device = torch.device(device)
        self.to(self.device)

    def forward(self, states):
        if not isinstance(states['gridScene'], torch.Tensor):
            states = {
                'gridScene': torch.tensor(states['gridScene'], dtype=torch.long, device=self.device),
                'gridEnemies': torch.tensor(states['gridEnemies'], dtype=torch.long, device=self.device),
                'vector': torch.tensor(states['vector'], dtype=torch.float32, device=self.device)
            }
        features = super().forward(states)
        values = self.critic_head(features)
        return values.squeeze(-1)