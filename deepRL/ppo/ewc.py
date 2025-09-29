import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Dataset

class EWC:
    def __init__(self, model: nn.Module, ewc_lambda: float):
        self.model = model
        self.ewc_lambda = ewc_lambda
        self.fisher_matrix = self.create_empty_clone()
        self.optimal_params = {}

    def create_empty_clone(self):
        matrix = {}
        for name, param in self.model.named_parameters():
            if param.requires_grad:
                matrix[name] = torch.zeros_like(param.data)
        return matrix

    def _compute_fisher_for_task(self, dataset: Dataset, batch_size: int = 256):
        task_fisher_matrix = self.create_empty_clone()
        self.model.eval()
        
        dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)

        device = self.model.device

        for states_batch, actions_batch in dataloader:
            states_batch = {key: val.to(device) for key, val in states_batch.items()}
            actions_batch = actions_batch.to(device)
            self.model.zero_grad()
            
            log_probs, _ = self.model.get_predictions(states_batch, actions_batch)
            
            log_prob_sample = log_probs.mean()
            log_prob_sample.backward()

            for name, param in self.model.named_parameters():
                if param.requires_grad and param.grad is not None:
                    task_fisher_matrix[name] += param.grad.data.pow(2)

        num_samples = len(dataset)
        for name in task_fisher_matrix:
            task_fisher_matrix[name] /= num_samples
            
        return task_fisher_matrix

    def register_task(self, dataset: Dataset):

        task_fisher = self._compute_fisher_for_task(dataset)

        for name in self.fisher_matrix:
            self.fisher_matrix[name] += task_fisher[name]

        self.optimal_params = {}
        for name, param in self.model.named_parameters():
            if param.requires_grad:
                self.optimal_params[name] = param.data.clone().half()

    def penalty(self) -> torch.Tensor:
        if not self.optimal_params:
            return torch.tensor(0.0, device=self.model.device)

        ewc_loss = torch.tensor(0.0, device=self.model.device)
        
        for name, param in self.model.named_parameters():
            if name in self.fisher_matrix:
                optimal_p = self.optimal_params[name].float()
                ewc_loss += (self.fisher_matrix[name] * (param - optimal_p).pow(2)).sum()
        
        return (self.ewc_lambda / 2.0) * ewc_loss