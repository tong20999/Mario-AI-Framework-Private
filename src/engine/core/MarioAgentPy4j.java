package engine.core;

public interface MarioAgentPy4j extends MarioAgent{
    void update(MarioPlayStepResult playstepResult,MarioForwardModel oldState, boolean[] actions ,MarioForwardModel nextState, MarioTimer agentTimer);
}
