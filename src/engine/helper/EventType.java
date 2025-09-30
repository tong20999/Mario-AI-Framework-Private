package engine.helper;

public enum EventType {
    BUMP(1),
    STOMP_KILL(2),
    FIRE_KILL(3),
    SHELL_KILL(4),
    FALL_KILL(5),
    JUMP(6),
    LAND(7),
    COLLECT(8),
    HURT(9),
    KICK(10),
    LOSE(11),
    WIN(12),
    HIT_WALL(13),
    FALL_PIT(14),
    FLAG(15),
    BUMP_KILL(16),
    DAMAGE(17),
    BREAK(18),
    BONK(19),
    TIME_OUT(20),
    STEP(21);

    private final int value;

    EventType(int newValue) {
        value = newValue;
    }

    public int getValue() {
        return value;
    }
}
