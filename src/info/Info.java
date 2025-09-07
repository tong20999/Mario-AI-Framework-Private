package info;
public class Info {
    int episode = -1;
    boolean evaluation = false;
    String level;
    boolean isVisual;
    boolean playMode;

    public int getEpisode() {
        return episode;
    }

    public boolean isVisual(){
        return this.isVisual;
    }

    public boolean isEvaluation() {
        return evaluation;
    }
    public boolean isPlayMode() {return playMode;}

    public Info(int episode, boolean evaluation, boolean visual, boolean playMode, String level) {
        this.episode = episode;
        this.evaluation = evaluation;
        this.isVisual = visual;
        this.level = level;
        this.playMode = playMode;
    }

    public String getPayload() {
        return this.level;
    }
}
