package info;
public class Info {
    int episode = -1;
    boolean evaluation = false;
    String level;
    boolean isVisual;

    public int getEpisode() {
        return episode;
    }

    public boolean isVisual(){
        return this.isVisual;
    }

    public boolean isEvaluation() {
        return evaluation;
    }

    public Info(int episode, boolean evaluation, boolean visual, String level) {
        this.episode = episode;
        this.evaluation = evaluation;
        this.isVisual = visual;
        this.level = level;
    }

    public String getPayload() {
        return this.level;
    }
}
