package agents.myAgent;

import engine.core.*;
import engine.helper.MarioActions;
import py4j.GatewayServer;

import java.util.List;

/**
 * @author RobinBaumgarten
 */
public class Agent implements MarioAgentPy4j {
    AgentListener listener;
    MarioForwardModel model;

    public Agent getAgent(){
        return this;
    }

    @Override
    public void initialize(MarioForwardModel model, MarioTimer timer) {
        this.model = model;
    }

    public void registerListener(AgentListener listener) {
        this.listener = listener;
    }

    public MarioForwardModel getState(){
        return this.model;
    }

    @Override
    public boolean[] getActions(MarioForwardModel model, MarioTimer timer) {
        List<Boolean> o = (List<Boolean>)listener.getActions(model.getMarioCompleteObservation());
        boolean[] actions = new boolean[o.size()];
        for (int i = 0; i < o.size(); i++) {
            actions[i] = o.get(i);
        }
        return actions;
    }

    @Override
    public String getAgentName() {
        return "MyAgent";
    }

    @Override
    public void update(MarioPlayStepResult playstepResult, MarioForwardModel oldState, boolean[] actions, MarioForwardModel nextState, MarioTimer timer) {
        listener.update(playstepResult, oldState, actions, nextState, timer);
    }
}
