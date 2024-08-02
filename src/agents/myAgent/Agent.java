package agents.myAgent;

import agents.myAgentMachineLearning.AgentListener;
import engine.core.*;

import java.util.List;

/**
 * @author RobinBaumgarten
 */
public class Agent implements MarioAgent {
    AgentListener listener;
    MarioForwardModel model;

    public agents.myAgent.Agent getAgent(){
        return this;
    }

    public void registerListener(AgentListener listener) {
        this.listener = listener;
    }

    @Override
    public void initialize(MarioForwardModel model, MarioTimer timer) {
        this.model = model;
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
}
