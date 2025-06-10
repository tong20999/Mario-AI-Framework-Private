package info;

import java.text.MessageFormat;

public class EndInfo {
    public int lose;
    public int win;
    public int hurt;
    public int timeout;
    public int fallPit;

    public int collect;
    public long fallKill;
    public long stompKill;
    public long shellKill;
    public long fireKill;


    @Override
    public String toString() {
        long kill = fallKill + stompKill + shellKill + fireKill;
        return MessageFormat.format("win {0}, lose {1}, timeout {2}, hurt {3} fallPit {4}, collect {5}, kill {6}, {7}, {8}, {9}, {10}",
                win, lose, timeout, hurt, fallPit, collect, kill, stompKill, fireKill, shellKill, fallKill);
    }
}