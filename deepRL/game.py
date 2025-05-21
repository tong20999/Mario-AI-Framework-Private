from numpy import intp
import numpy as np

class Game():
    def __init__(self, javaGame) -> None:
        self.javaGame = javaGame

    def update(self, output:bytes):
        pass
        
    def getTrainingActions(self, state:bytes):
        pass

    def reset(self):
        return self.javaGame.reset()
    
    def runGame(self, level, agent, episode):
        return self.javaGame.runGame(agent, level, episode, 200, 0, True, 2000, 2.0, False)
    
    def runEvaluation(self, level, agent, episode):
        return self.javaGame.runGame(agent, level, episode, 200, 0, True, 2000, 2.0, True)
            
    class Java:
        implements = ["agents.myAgentMachineLearning.AgentListener"]