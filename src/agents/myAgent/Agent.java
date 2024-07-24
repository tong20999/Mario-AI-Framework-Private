package agents.myAgent;

import engine.core.MarioAgent;
import engine.core.MarioForwardModel;
import engine.core.MarioTimer;
import engine.helper.MarioActions;

/**
 * @author RobinBaumgarten
 */
public class Agent implements MarioAgent {
    AgentListener listener;
    MarioForwardModel model;
    boolean[] actions = new boolean[MarioActions.numberOfActions()];

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
//        List<Boolean> o = (List<Boolean>)listener.getActions(State.make(model));
//        boolean[] actions = new boolean[5];
//        for (int i = 0; i < o.size(); i++) {
//            actions[i] = o.get(i);
//        }
//        return actions;
        this.model = model;
        return this.actions;
    }

    @Override
    public String getAgentName() {
        return "MyAgent";
    }
}
