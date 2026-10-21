import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public class Human implements Comparable<Human>, Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private Float height;
    private LocalDateTime birthday;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Float getHeight() {
        return height;
    }

    public void setHeight(Float height) {
        this.height = height;
    }

    public LocalDateTime getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDateTime birthday) {
        this.birthday = birthday;
    }

    @Override
    public int compareTo(Human other) {
        int result = this.name.compareToIgnoreCase(other.name);
        if (result != 0) {
            return result;
        }
        result = Float.compare(this.height, other.height);
        if (result != 0) {
            return result;
        }
        if (this.birthday == null && other.birthday == null) {
            return 0;
        }
        if (this.birthday == null) {
            return -1;
        }
        if (other.birthday == null) {
            return 1;
        }
        return this.birthday.compareTo(other.birthday);
    }

    @Override
    public String toString() {
        return "Human{" +
                "name='" + name + '\'' +
                ", height=" + height +
                ", birthday=" + birthday +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Human human)) {
            return false;
        }
        return Objects.equals(name, human.name)
                && Objects.equals(height, human.height)
                && Objects.equals(birthday, human.birthday);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, height, birthday);
    }
}
