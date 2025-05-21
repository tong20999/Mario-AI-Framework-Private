package agents.myAgentMachineLearning;

public interface AgentListener {
    Object getActions(Object source);
    Object getTrainingActions(Object source);
    void update(Object output);
    Object getEvaluateTrainingActions(Object source);
}
