import logging
import sys
import torch.multiprocessing as mp
import numpy as np

logger = logging.getLogger('Agent:MultiprocessEnv')

def worker_process(rank, worker_end, make_env_fn):
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

        except (KeyboardInterrupt, EOFError):
            # Handle graceful shutdown if the pipe is closed or on interrupt
            logger.error(f"Worker {rank} exiting.")
            env.close()
            break

        except (Exception) as ex:
            logger.error(f"Worker {rank} encountered an error: {ex}. Sending error to parent.")
            env.close()
            worker_end.send(('error', str(ex)))
            worker_end.close()
            sys.exit(1)

class MultiprocessEnv(object):
    def __init__(self, make_env_fn, n_workers):
        self.make_env_fn = make_env_fn
        self.n_workers = n_workers
        
        # Create a list of pipes for communication
        self.parent_pipes, self.worker_pipes = zip(*[mp.Pipe() for _ in range(self.n_workers)])

        self.workers = []
        for rank in range(self.n_workers):
            p = mp.Process(
                target=worker_process, 
                args=(rank, self.worker_pipes[rank], self.make_env_fn)
            )
            self.workers.append(p)
        
        [w.start() for w in self.workers]

    def reset(self, episodeStart:int, ranks=None, visual = False, levels: list = None, **kwargs):
        if ranks is None:
            ranks = range(self.n_workers)

        if levels:
            assert len(ranks) == len(levels) , "Must provide one level per rank."

        # Send reset command to specified workers
        for i, rank in enumerate(ranks):
            episode = episodeStart + rank
            level_name = levels[i]
            info = {"episode" : episode, "evaluation" : False, "visual" : rank == 0 and visual, "level" :  level_name}
            kwargs['options'] = info
            self.parent_pipes[rank].send(('reset', kwargs))
        
        # Receive results
        results = [self.parent_pipes[rank].recv() for rank in ranks]
        obs_batch = {key: np.stack([d[0][key] for d in results]) for key in results[0][0]}
        info_batch = [d[1] for d in results]
        return obs_batch

    def step(self, actions):
        assert len(actions) == self.n_workers, "Number of actions must match number of workers."
        
        for rank, action in enumerate(actions):
            self.parent_pipes[rank].send(('step', {'action': action}))
            
        results = [self.parent_pipes[rank].recv() for rank in range(self.n_workers)]
        
        obs_list, rewards, terminateds, truncateds, infos = zip(*results)
        obs_batch = {key: np.stack([o[key] for o in obs_list]) for key in obs_list[0]}
        return obs_batch, np.array(rewards), np.array(terminateds), np.array(truncateds), infos

    def close(self):
        for pipe in self.parent_pipes:
            pipe.send(('close', {}))
        [w.join() for w in self.workers]