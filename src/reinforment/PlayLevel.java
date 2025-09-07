package reinforment;

import com.google.gson.annotations.SerializedName;

public class PlayLevel {
    @SerializedName("file")
    private final String file;

    @SerializedName("fps")
    private final int fps;

    public PlayLevel(String file, int fps) {
        this.file = file;
        this.fps = fps;
    }

    public String getFile() {
        return file;
    }

    public int getFps() {
        return fps;
    }
}
