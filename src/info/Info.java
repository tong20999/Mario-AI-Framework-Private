package info;
public class Info {
    int episode = -1;
    boolean evaluation = false;
    float epsilon = 0.0f;

    public int getEpisode() {
        return episode;
    }

    public boolean isEvaluation() {
        return evaluation;
    }

    public Info(int episode, boolean evaluation, float epsilon) {
        this.episode = episode;
        this.evaluation = evaluation;
        this.epsilon = epsilon;
    }

    public float getEpsilon() {
        return this.epsilon;
    }
}
