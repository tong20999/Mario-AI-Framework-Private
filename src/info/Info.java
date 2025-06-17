package info;
public class Info {
    int episode = -1;
    boolean evaluation = false;
    String level;

    public int getEpisode() {
        return episode;
    }

    public boolean isEvaluation() {
        return evaluation;
    }

    public Info(int episode, boolean evaluation, String level) {
        this.episode = episode;
        this.evaluation = evaluation;
        this.level = level;
    }

    public String getLevel() {
        return this.level;
    }
}
