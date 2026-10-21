import java.io.Serializable;

public class UpdateArgument implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long id;
    private final City city;

    public UpdateArgument(long id, City city) {
        this.id = id;
        this.city = city;
    }

    public long getId() {
        return id;
    }

    public City getCity() {
        return city;
    }
}
