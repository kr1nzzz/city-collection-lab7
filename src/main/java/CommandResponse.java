import java.io.Serializable;
import java.util.List;

public class CommandResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final List<City> cities;

    public CommandResponse(boolean success, String message) {
        this(success, message, List.of());
    }

    public CommandResponse(boolean success, String message, List<City> cities) {
        this.success = success;
        this.message = message;
        this.cities = cities == null ? List.of() : cities;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public List<City> getCities() { return cities; }
}
