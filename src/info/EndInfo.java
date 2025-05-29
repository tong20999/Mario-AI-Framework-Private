package info;

import java.text.MessageFormat;

public class EndInfo {
    public int lose;
    public int win;
    public int fallKill;
    public int hurt;
    public int timeout;

    @Override
    public String toString() {
        return MessageFormat.format("win {0}, lose {1}, timeout {2}, fallKill {3}, hurt {4}", win, lose, timeout, fallKill, hurt);
    }
}
