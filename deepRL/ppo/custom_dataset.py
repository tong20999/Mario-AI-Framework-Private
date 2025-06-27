import torch
from torch.utils.data import Dataset

class CustomDictDataset(Dataset):
    """A custom PyTorch Dataset to handle dictionary-based states."""
    def __init__(self, states_dict, actions_tensor):
        # Ensure all tensors in the states dictionary have the same first dimension (number of samples)
        self.n_samples = actions_tensor.shape[0]
        for key, val in states_dict.items():
            assert val.shape[0] == self.n_samples, f"Mismatch in number of samples for key {key}"
            
        self.states = states_dict
        self.actions = actions_tensor

    def __len__(self):
        """Returns the total number of samples in the dataset."""
        return self.n_samples

    def __getitem__(self, idx):
        """
        Retrieves the sample at the given index.
        For states, it returns a dictionary containing the idx-th slice of each tensor.
        """
        # Slice each tensor in the states dictionary at the given index
        state_sample = {key: val[idx] for key, val in self.states.items()}
        action_sample = self.actions[idx]
        
        return state_sample, action_sample