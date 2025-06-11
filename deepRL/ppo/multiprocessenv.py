import torch.multiprocessing as mp
import numpy as np

# ✅ STEP 1: Move the worker logic to a top-level function.
# It is no longer a method of the class.
def worker_process(rank, worker_end, make_env_fn):
    """
    This function runs in a separate process.
    It creates its own environment and communicates through a pipe.
    """
    # Create the environment inside the new process
    env = make_env_fn()

    while True:
        try:
            # Receive commands from the main process
            cmd, kwargs = worker_end.recv()
            
            if cmd == 'reset':
                worker_end.send(env.reset(**kwargs))
            elif cmd == 'step':
                # The result of env.step() is a tuple (obs, reward, terminated, truncated, info)
                # This tuple is pickleable and can be sent directly.
                worker_end.send(env.step(**kwargs))
            elif cmd == 'close':
                env.close()
                worker_end.close()
                break
            # You can add other custom commands here if needed
            # elif cmd == '_past_limit':
            #   ...
            else:
                raise NotImplementedError(f"Command '{cmd}' is not implemented.")

        except (KeyboardInterrupt, EOFError):
            # Handle graceful shutdown if the pipe is closed or on interrupt
            print(f"Worker {rank} exiting.")
            env.close()
            break

class MultiprocessEnv(object):
    def __init__(self, make_env_fn, n_workers):
        self.make_env_fn = make_env_fn
        self.n_workers = n_workers
        
        # Create a list of pipes for communication
        self.parent_pipes, self.worker_pipes = zip(*[mp.Pipe() for _ in range(self.n_workers)])

        self.workers = []
        for rank in range(self.n_workers):
            # ✅ STEP 2: Use the top-level function as the target.
            # Pass everything it needs as simple, pickleable arguments.
            p = mp.Process(
                target=worker_process, 
                args=(rank, self.worker_pipes[rank], self.make_env_fn)
            )
            self.workers.append(p)
        
        # Start all worker processes
        [w.start() for w in self.workers]

    # The rest of the class now only interacts through the pipes
    # and does not need a 'work' method.

    def reset(self, ranks=None, **kwargs):
        if ranks is None:
            ranks = range(self.n_workers)

        # Send reset command to specified workers
        for rank in ranks:
            self.parent_pipes[rank].send(('reset', kwargs))
        
        # Receive results
        results = [self.parent_pipes[rank].recv() for rank in ranks]
        return np.stack(results)

    def step(self, actions):
        assert len(actions) == self.n_workers, "Number of actions must match number of workers."
        
        # Send step command with corresponding action to each worker
        for rank, action in enumerate(actions):
            self.parent_pipes[rank].send(('step', {'action': action}))
            
        # Receive results from all workers
        results = [self.parent_pipes[rank].recv() for rank in range(self.n_workers)]
        
        # Unzip the results: (obs, reward, terminated, truncated, info)
        obs, rewards, terminateds, truncateds, infos = zip(*results)
        
        return np.stack(obs), np.array(rewards), np.array(terminateds), np.array(truncateds), infos

    def close(self):
        # Send close command to all workers
        for pipe in self.parent_pipes:
            pipe.send(('close', {}))
        
        # Wait for all worker processes to finish
        [w.join() for w in self.workers]