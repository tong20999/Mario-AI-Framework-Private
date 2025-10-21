package reinforcement;

public enum EnumBlockType {
    COIN_QUESTION_BLOCK('!'),
    COIN_BLOCK('C'),
    MUSHROOM_QUESTION_BLOCK('@'),
    MUSHROOM_BLOCK('U'),
    NORMAL_BLOCK('S'),
    USED_BLOCK('D'),
    PYRAMID_BLOCK('#');

    private final char value;

    // Constructor to set the char value for each enum constant
    EnumBlockType(char value) {
        this.value = value;
    }

    // Getter to access the char value
    public char getValue() {
        return value;
    }

    // Optional: Method to find enum by its char value
    public static EnumBlockType fromChar(char c) {
        for (EnumBlockType e : EnumBlockType.values()) {
            if (e.getValue() == c) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unexpected value: " + c);
    }
}
