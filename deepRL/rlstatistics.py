import os

def write_hyperparameters(
              working_dir,
              policy_optimizer_lr,
              policy_optimization_epochs,
              policy_sample_ratio,
              policy_clip_range,
              policy_stopping_kl,
              value_optimizer_lr,
              value_optimization_epochs,
              value_clip_range,
              value_stopping_mse,
              ewc_lambda,
              max_buffer_episodes,
              max_buffer_episode_steps,
              entropy_loss_weight,
              tau,
              n_workers,
              batch_size,     
              level_pool,
              evaluation_levels,
              rehearsal_level_tasks):
      with open(os.path.join(working_dir, "hyperparameters.txt"), "a") as file:
                    file.write("policy_optimizer_lr {}\n".format(policy_optimizer_lr))
                    file.write("policy_optimization_epochs {}\n".format(policy_optimization_epochs))
                    file.write("policy_sample_ratio {}\n".format(policy_sample_ratio))
                    file.write("policy_clip_range {}\n".format(policy_clip_range))
                    file.write("policy_stopping_kl {}\n".format(policy_stopping_kl))

                    file.write("value_optimizer_lr {}\n".format(value_optimizer_lr))
                    file.write("value_optimization_epochs {}\n".format(value_optimization_epochs))
                    file.write("value_clip_range {}\n".format(value_clip_range))
                    file.write("value_stopping_mse {}\n".format(value_stopping_mse))

                    file.write("ewc_lambda {}\n".format(ewc_lambda))

                    file.write("max_buffer_episodes {}\n".format(max_buffer_episodes))
                    file.write("max_buffer_episode_steps {}\n".format(max_buffer_episode_steps))

                    file.write("entropy_loss_weight {}\n".format(entropy_loss_weight))
                    file.write("tau {}\n".format(tau))
                    file.write("n_workers {}\n".format(n_workers))
                    file.write("batch_size {}\n".format(batch_size))
                    file.write("levels\n")
                    for level in level_pool:
                          file.write("{}\n".format(level))
                    file.write("evaluation_levels\n")
                    for evaluation_level in evaluation_levels:
                          file.write("{}\n".format(evaluation_level))
                    file.write("rehearsal_level_tasks\n")
                    for rehearsal_level_task in rehearsal_level_tasks:
                          file.write("task\n")
                          for rehearsal_level in rehearsal_level_task:
                               file.write("{}\n".format(rehearsal_level))