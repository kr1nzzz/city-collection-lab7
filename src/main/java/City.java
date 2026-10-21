import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public class City implements Comparable<City>, Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private String name;
    private Coordinates coordinates;
    private LocalDateTime creationDate;
    private Double area;
    private Long population;
    private long metersAboveSeaLevel;
    private Climate climate;
    private Government government;
    private StandardOfLiving standardOfLiving;
    private Human governor;
    private String ownerLogin;

    @Override
    public int compareTo(City other) {
        int c = this.name.compareToIgnoreCase(other.name);
        if (c != 0) return c;
        return Long.compare(this.id, other.id);
    }

    public double sizeValue() {
        return area == null ? 0.0 : area;
    }

    @Override
    public String toString() {
        return "City{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", coordinates=" + coordinates +
                ", creationDate=" + creationDate +
                ", area=" + area +
                ", population=" + population +
                ", metersAboveSeaLevel=" + metersAboveSeaLevel +
                ", climate=" + climate +
                ", government=" + government +
                ", standardOfLiving=" + standardOfLiving +
                ", governor=" + governor +
                ", owner='" + ownerLogin + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof City city)) return false;
        return id == city.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Coordinates getCoordinates() { return coordinates; }
    public void setCoordinates(Coordinates coordinates) { this.coordinates = coordinates; }
    public LocalDateTime getCreationDate() { return creationDate; }
    public void setCreationDate(LocalDateTime creationDate) { this.creationDate = creationDate; }
    public Double getArea() { return area; }
    public void setArea(Double area) { this.area = area; }
    public Long getPopulation() { return population; }
    public void setPopulation(Long population) { this.population = population; }
    public long getMetersAboveSeaLevel() { return metersAboveSeaLevel; }
    public void setMetersAboveSeaLevel(long metersAboveSeaLevel) { this.metersAboveSeaLevel = metersAboveSeaLevel; }
    public Climate getClimate() { return climate; }
    public void setClimate(Climate climate) { this.climate = climate; }
    public Government getGovernment() { return government; }
    public void setGovernment(Government government) { this.government = government; }
    public StandardOfLiving getStandardOfLiving() { return standardOfLiving; }
    public void setStandardOfLiving(StandardOfLiving standardOfLiving) { this.standardOfLiving = standardOfLiving; }
    public Human getGovernor() { return governor; }
    public void setGovernor(Human governor) { this.governor = governor; }
    public String getOwnerLogin() { return ownerLogin; }
    public void setOwnerLogin(String ownerLogin) { this.ownerLogin = ownerLogin; }
}
