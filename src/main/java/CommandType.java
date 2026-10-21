import java.io.Serializable;

public enum CommandType implements Serializable {
    REGISTER,
    LOGIN,
    HELP,
    INFO,
    SHOW,
    ADD,
    UPDATE,
    REMOVE_BY_ID,
    CLEAR,
    REMOVE_LOWER,
    REORDER,
    HISTORY,
    REMOVE_ALL_BY_GOVERNOR,
    MIN_BY_CLIMATE,
    PRINT_FIELD_ASCENDING_GOVERNOR
}
