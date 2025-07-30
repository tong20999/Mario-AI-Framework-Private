import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import os

# Define the file path
train_dir = "C:/thesis_data/blocks=1,enemies=1,pits=1,pipes=0,width_min=20,width_max=20/4/"
log_file = os.path.join(train_dir, "value_losses.txt")
save_dir = os.path.join(train_dir, "reconstructed_chart.png")

rewards_history = []
rewards_history_mean = []
# --- Load the Data ---
try:
    # Use np.loadtxt for simplicity and robustness
    with open(log_file, 'r') as file:
        for line in file:
            value = float(line)
            rewards_history.append(value)
            rewards_history_mean.append(np.mean(rewards_history[-100:]))
except IOError:
    print(f"Error: Could not find or read the file at {log_file}")
    exit()


    # rewards_history.append(np.mean(evaluation_score[i][-100:]))
# --- Recreate the Plot ---
# This logic is copied from your PPO.plot method
fig, ax = plt.subplots(figsize=(12, 7))
rewards_series = pd.Series(rewards_history_mean)
moving_avg = rewards_series.rolling(window=50, min_periods=10).mean()

ax.set_title('Reconstructed Training Results', fontsize=16)
ax.set_xlabel('Episode', fontsize=12)
ax.set_ylabel('Mean 100-Episode Evaluation Score', fontsize=12)
ax.grid(True, which='both', linestyle='--', linewidth=0.5)

# Plot the raw mean scores
ax.plot(rewards_history_mean, alpha=0.5, label='Mean 100-Episode Score')

# Plot the smoothed moving average
ax.plot(moving_avg, color='red', linewidth=2, label='Smoothed Average (50ep window)')
if not moving_avg.empty and pd.notna(moving_avg.iloc[-1]):
            last_ma_value = moving_avg.iloc[-1]
            plt.text(len(rewards_history_mean)-1, last_ma_value, f'{last_ma_value:.2f}')

ax.legend()
plt.tight_layout()
plt.savefig(save_dir)
plt.show()