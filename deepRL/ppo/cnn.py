import torch
import torch.nn as nn
import torch.nn.functional as F
import numpy as np
from gymnasium.spaces import Dict

class CNNBase(nn.Module):
    def __init__(self, observation_space: Dict, hidden_dims=(256, 256)):
        super(CNNBase, self).__init__()
        
        grid_shape = observation_space['grid'].shape
        self.cnn = nn.Sequential(
            nn.Conv2d(grid_shape[0], 32, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.Conv2d(32, 64, kernel_size=3, stride=1, padding=1),
            nn.ReLU(),
            nn.Flatten(),
        )

        with torch.no_grad():
            dummy_grid = torch.zeros(1, *grid_shape)
            cnn_output_dim = self.cnn(dummy_grid).shape[1]

        vector_shape = observation_space['vector'].shape
        self.mlp = nn.Sequential(
            nn.Linear(vector_shape[0], 64),
            nn.ReLU()
        )
        
        self.combined_mlp = nn.Sequential(
            nn.Linear(cnn_output_dim + 64, hidden_dims[0]),
            nn.ReLU(),
            nn.Linear(hidden_dims[0], hidden_dims[1]),
            nn.ReLU(),
        )
        
        self.feature_dim = hidden_dims[1]

    def forward(self, states: dict):
        # This now correctly assumes 'states' contains tensors
        cnn_out = self.cnn(states['grid'])
        mlp_out = self.mlp(states['vector'])
        
        combined_features = torch.cat([cnn_out, mlp_out], dim=1)
        final_features = self.combined_mlp(combined_features)
        
        return final_features

class CNNActor(CNNBase):
    def __init__(self, observation_space, output_dim, hidden_dims=(256, 256)):
        super(CNNActor, self).__init__(observation_space, hidden_dims)
        self.actor_head = nn.Linear(self.feature_dim, output_dim)
        
        device = "cpu"
        if torch.cuda.is_available():
            device = "cuda:0"
        self.device = torch.device(device)
        self.to(self.device)

    def _format_single_obs(self, obs: dict):
        """
        Helper to convert a single dictionary observation (from env.reset() or env.step())
        into a batched dictionary of tensors for the network.
        """
        # Add a batch dimension to each numpy array and convert to a tensor
        return {
            key: torch.tensor(value, dtype=torch.float32, device=self.device).unsqueeze(0)
            for key, value in obs.items()
        }

    def forward(self, states, is_batched=False):
        """
        The main forward pass. Can handle both single (from evaluation) 
        and batched (from buffer) observations.
        """
        if not is_batched:
             states = self._format_single_obs(states)
        elif not isinstance(states['grid'], torch.Tensor):
            # Batched, but still numpy. Convert to tensor.
            states = {k: torch.tensor(v, dtype=torch.float32, device=self.device) for k, v in states.items()}

        features = super().forward(states)
        logits = self.actor_head(features)
        return logits

    # --- Methods for the EpisodeBuffer (handle batches) ---
    def np_pass(self, states):
        logits = self.forward(states, is_batched=True)
        np_logits = logits.detach().cpu().numpy()
        dist = torch.distributions.Categorical(logits=logits)
        actions = dist.sample()
        np_actions = actions.detach().cpu().numpy()
        logpas = dist.log_prob(actions)
        np_logpas = logpas.detach().cpu().numpy()
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

    # --- Methods for Evaluation (handle single observations) ---
    
    def select_action(self, obs: dict):
        logits = self.forward(obs, is_batched=False)
        dist = torch.distributions.Categorical(logits=logits)
        action = dist.sample()
        return action.item()

    def select_greedy_action(self, obs: dict):
        logits = self.forward(obs, is_batched=False)
        # np.argmax works on the tensor's first (and only) batch entry
        action = np.argmax(logits.detach().cpu().numpy()[0])
        return action


class CNNCritic(CNNBase):
    def __init__(self, observation_space, hidden_dims=(256, 256)):
        super(CNNCritic, self).__init__(observation_space, hidden_dims)
        self.critic_head = nn.Linear(self.feature_dim, 1)

        device = "cpu"
        if torch.cuda.is_available():
            device = "cuda:0"
        self.device = torch.device(device)
        self.to(self.device)

    def forward(self, states):
        """
        FIX: This is the entry point. It checks if the input is NumPy and converts it.
        """
        if not isinstance(states['grid'], torch.Tensor):
            # Convert dictionary of NumPy arrays to dictionary of Tensors
            states = {k: torch.tensor(v, dtype=torch.float32, device=self.device) for k, v in states.items()}

        features = super().forward(states)
        values = self.critic_head(features)
        return values.squeeze(-1)