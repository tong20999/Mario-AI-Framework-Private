# ewc.py
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset

class EWC:
    """
    Elastic Weight Consolidation (EWC) implementation.
    
    Args:
        model (nn.Module): The model to be protected (in this case, the policy network).
        ewc_lambda (float): The hyperparameter that controls the importance of the EWC penalty.
    """
    def __init__(self, model: nn.Module, ewc_lambda: float):
        self.model = model
        self.ewc_lambda = ewc_lambda
        self.saved_tasks = []

    def _compute_fisher(self, dataset: TensorDataset, batch_size: int = 256):
        """
        Computes the diagonal of the Fisher Information Matrix (FIM).

        Args:
            dataset (TensorDataset): A dataset containing the states and actions from the completed task.
            batch_size (int): The batch size to use for iterating through the dataset.
        """
        fisher_matrix = {}
        for name, param in self.model.named_parameters():
            if param.requires_grad:
                fisher_matrix[name] = torch.zeros_like(param.data)

        self.model.eval()  # Set model to evaluation mode

        dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)

        for states_batch, actions_batch in dataloader:
            # Zero gradients
            self.model.zero_grad()

            # Get log probabilities from the policy model
            log_probs, _ = self.model.get_predictions(states_batch, actions_batch)
            
            # Sample a log probability (as a proxy for the distribution) and backpropagate
            log_prob_sample = log_probs.mean() # Taking mean is a common simplification
            log_prob_sample.backward()

            # Accumulate squared gradients
            for name, param in self.model.named_parameters():
                if param.requires_grad and param.grad is not None:
                    fisher_matrix[name] += param.grad.data.pow(2)

        # Average the Fisher matrix over the number of samples
        num_samples = len(dataset)
        for name in fisher_matrix:
            fisher_matrix[name] /= num_samples

        return fisher_matrix

    def register_task(self, dataset: TensorDataset):
        """
        Call this method after a task is learned to compute and store its
        Fisher matrix and optimal parameters.
        
        Args:
            dataset (TensorDataset): The dataset (states, actions) from the completed task.
        """
        # 1. Compute the Fisher Information Matrix for the current task
        fisher_matrix = self._compute_fisher(dataset)

        # 2. Store the optimal parameters for the current task
        optimal_params = {}
        for name, param in self.model.named_parameters():
            if param.requires_grad:
                optimal_params[name] = param.data.clone()

        # 3. Save the task's (fisher_matrix, optimal_params)
        self.saved_tasks.append({
            'fisher': fisher_matrix,
            'params': optimal_params
        })

    def penalty(self) -> torch.Tensor:
        """
        Calculates the EWC penalty loss for all registered tasks.
        This should be added to the main loss function during training of a new task.
        """
        if not self.saved_tasks:
            return torch.tensor(0.0, device=self.model.device)

        ewc_loss = torch.tensor(0.0, device=self.model.device)

        # Iterate over all previously learned tasks
        for task in self.saved_tasks:
            fisher = task['fisher']
            optimal_params = task['params']

            # Calculate the quadratic penalty
            for name, param in self.model.named_parameters():
                if name in fisher:
                    ewc_loss += (fisher[name] * (param - optimal_params[name]).pow(2)).sum()
        
        return (self.ewc_lambda / 2.0) * ewc_loss