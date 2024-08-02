package agents.myAgent;

import engine.core.MarioPlayStepResult;
import engine.core.MarioTimer;

public interface AgentListener {
    Object getActions(Object source);

    void update(Object playstepResult, Object oldstate, Object actions, Object nextState, Object timer);
}
