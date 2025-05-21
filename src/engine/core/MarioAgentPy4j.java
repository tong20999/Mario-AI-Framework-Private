package engine.core;

public interface MarioAgentPy4j extends MarioAgent{
    void update(boolean[] actions, MarioForwardModel state, MarioForwardModel nextState, float reward, boolean isTerminate);
}
