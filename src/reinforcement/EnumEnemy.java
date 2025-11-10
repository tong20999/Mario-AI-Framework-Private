package reinforcement;

public enum EnumEnemy {
    GOOMBA('g'),
    GREEN_KOOPA('k'),
    GREEN_KOOPA_WINGED('K');

    private final char value;

    // Constructor to set the char value for each enum constant
    EnumEnemy(char value) {
        this.value = value;
    }

    // Getter to access the char value
    public char getValue() {
        return value;
    }

    // Optional: Method to find enum by its char value
    public static EnumEnemy fromChar(char c) {
        for (EnumEnemy e : EnumEnemy.values()) {
            if (e.getValue() == c) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unexpected value: " + c);
    }
}
