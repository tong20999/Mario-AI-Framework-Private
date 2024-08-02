package agents.myAgent.myAgentMachineLearning;

public interface AgentListener {
    Object getActions(Object source);

    void update(Object playstepResult, Object oldstate, Object actions, Object nextState, Object timer);
}
