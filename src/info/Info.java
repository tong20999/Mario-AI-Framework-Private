package info;
public class Info {
    int episode = -1;
    boolean evaluation = false;

    public int getEpisode() {
        return episode;
    }

    public boolean isEvaluation() {
        return evaluation;
    }

    public Info(int episode, boolean evaluation) {
        this.episode = episode;
        this.evaluation = evaluation;
    }
}
