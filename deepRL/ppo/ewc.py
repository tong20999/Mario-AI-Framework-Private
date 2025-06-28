import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Dataset

class EWC:
    """
    Online Elastic Weight Consolidation (EWC) implementation.
    
    This version maintains a single consolidated Fisher Information Matrix
    and the optimal parameters from the most recent task to prevent
    linear growth in memory usage as more tasks are learned.
    """
    def __init__(self, model: nn.Module, ewc_lambda: float):
        self.model = model
        self.ewc_lambda = ewc_lambda
        
        # Initialize attributes for the consolidated Fisher matrix
        # and the optimal parameters from the last learned task.
        self.fisher_matrix = self.create_empty_clone()
        self.optimal_params = {} # Will be populated after the first task

    def create_empty_clone(self):
        """Creates a dictionary with zero-filled tensors matching model parameter shapes."""
        matrix = {}
        for name, param in self.model.named_parameters():
            if param.requires_grad:
                matrix[name] = torch.zeros_like(param.data)
        return matrix

    def _compute_fisher_for_task(self, dataset: Dataset, batch_size: int = 256):
        """Computes the diagonal Fisher Information Matrix for a single task dataset."""
        task_fisher_matrix = self.create_empty_clone()
        self.model.eval()
        
        dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)

        device = self.model.device

        for states_batch, actions_batch in dataloader:
            states_batch = {key: val.to(device) for key, val in states_batch.items()}
            actions_batch = actions_batch.to(device)
            self.model.zero_grad()
            
            # The model is expected to handle the dictionary-based state
            log_probs, _ = self.model.get_predictions(states_batch, actions_batch)
            
            # Use the mean log probability to compute the gradient w.r.t. the loss
            log_prob_sample = log_probs.mean()
            log_prob_sample.backward()

            # Accumulate the squared gradients into the Fisher matrix
            for name, param in self.model.named_parameters():
                if param.requires_grad and param.grad is not None:
                    task_fisher_matrix[name] += param.grad.data.pow(2)

        # Normalize the Fisher matrix by the number of samples
        num_samples = len(dataset)
        for name in task_fisher_matrix:
            task_fisher_matrix[name] /= num_samples
            
        return task_fisher_matrix

    def register_task(self, dataset: Dataset):
        """
        Updates the EWC state after a task is completed.
        
        Args:
            dataset: A PyTorch Dataset (like CustomDictDataset) containing
                     (state, action) pairs from the completed task.
        """
        # 1. Compute the Fisher Information Matrix for the new task
        task_fisher = self._compute_fisher_for_task(dataset)

        # 2. Update the consolidated Fisher matrix by adding the new importances.
        for name in self.fisher_matrix:
            self.fisher_matrix[name] += task_fisher[name]

        # 3. Update the optimal parameters to the model's current state.
        #    We store them in half-precision to save disk space.
        self.optimal_params = {} # Clear old params before saving new ones
        for name, param in self.model.named_parameters():
            if param.requires_grad:
                self.optimal_params[name] = param.data.clone().half()

    def penalty(self) -> torch.Tensor:
        """
        Calculates the EWC penalty loss. This is added to the main loss function
        during training on a new task.
        """
        # If we haven't finished any tasks yet, there is no penalty.
        if not self.optimal_params:
            return torch.tensor(0.0, device=self.model.device)

        ewc_loss = torch.tensor(0.0, device=self.model.device)
        
        # Calculate the penalty using the single consolidated Fisher matrix
        for name, param in self.model.named_parameters():
            if name in self.fisher_matrix:
                # The optimal params are float16, so cast them back to float32 for the calculation
                optimal_p = self.optimal_params[name].float()
                ewc_loss += (self.fisher_matrix[name] * (param - optimal_p).pow(2)).sum()
        
        return (self.ewc_lambda / 2.0) * ewc_loss