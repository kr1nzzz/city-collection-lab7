import java.time.LocalDateTime;
import java.util.Scanner;
import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;

public class InputManager {
    private final Scanner scanner;
    private final boolean interactive;

    public InputManager(Scanner scanner, boolean interactive) {
        this.scanner = scanner;
        this.interactive = interactive;
    }

    private String prompt(String text) {
        if (interactive) {
            System.out.print(text);
        }
        if (!scanner.hasNextLine()) {
            return null;
        }
        String line = scanner.nextLine();
        if (!interactive) {
            System.out.println(text + line);
        }
        return line;
    }

    private String readNonEmptyString(String field) {
        while (true) {
            String s = prompt("Введите " + field + ": ");
            if (s == null) {
                throw new IllegalArgumentException("Нет ввода для " + field);
            }
            s = s.trim();
            if (!s.isEmpty()) {
                return s;
            }
            System.out.println("Ошибка: строка не должна быть пустой.");
        }
    }

    private Integer readInt(String field, IntPredicate predicate, String errMsg) {
        while (true) {
            String s = prompt("Введите " + field + ": ");
            if (s == null) {
                throw new IllegalArgumentException("Нет ввода для " + field);
            }
            try {
                int v = Integer.parseInt(s.trim());
                if (predicate != null && !predicate.test(v)) {
                    System.out.println("Ошибка: " + errMsg);
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно целое число.");
            }
        }
    }

    private Long readLongObj(String field, LongPredicate predicate, String errMsg) {
        while (true) {
            String s = prompt("Введите " + field + ": ");
            if (s == null) {
                throw new IllegalArgumentException("Нет ввода для " + field);
            }
            try {
                long v = Long.parseLong(s.trim());
                if (predicate != null && !predicate.test(v)) {
                    System.out.println("Ошибка: " + errMsg);
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно целое число long.");
            }
        }
    }

    private long readLongPrim(String field) {
        while (true) {
            String s = prompt("Введите " + field + ": ");
            if (s == null) {
                throw new IllegalArgumentException("Нет ввода для " + field);
            }
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно целое число long.");
            }
        }
    }

    private Double readDoubleObj(String field, DoublePredicate predicate, String errMsg) {
        while (true) {
            String s = prompt("Введите " + field + ": ");
            if (s == null) {
                throw new IllegalArgumentException("Нет ввода для " + field);
            }
            try {
                double v = Double.parseDouble(s.trim());
                if (predicate != null && !predicate.test(v)) {
                    System.out.println("Ошибка: " + errMsg);
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно число double.");
            }
        }
    }

    private Float readFloatObj(String field, DoublePredicate predicate, String errMsg) {
        while (true) {
            String s = prompt("Введите " + field + ": ");
            if (s == null) {
                throw new IllegalArgumentException("Нет ввода для " + field);
            }
            try {
                float v = Float.parseFloat(s.trim());
                if (predicate != null && !predicate.test(v)) {
                    System.out.println("Ошибка: " + errMsg);
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: нужно число float.");
            }
        }
    }

    private <E extends Enum<E>> E readEnum(String field, Class<E> enumClass, boolean allowNull) {
        while (true) {
            if (interactive) {
                System.out.println("Возможные значения " + field + ":");
                for (E value : enumClass.getEnumConstants()) {
                    System.out.println(" - " + value.name());
                }
            }
            String s = prompt("Введите " + field + (allowNull ? " (пусто = null)" : "") + ": ");
            if (s == null) {
                throw new IllegalArgumentException("Нет ввода для " + field);
            }
            s = s.trim();
            if (allowNull && s.isEmpty()) {
                return null;
            }
            try {
                return Enum.valueOf(enumClass, s);
            } catch (IllegalArgumentException e) {
                System.out.println("Ошибка: нужно одно из значений списка.");
            }
        }
    }

    private LocalDateTime readDateTime(String field, boolean allowNull) {
        while (true) {
            String s = prompt("Введите " + field + " в формате YYYY-MM-DDTHH:MM:SS" + (allowNull ? " (пусто = null)" : "") + ": ");
            if (s == null) {
                throw new IllegalArgumentException("Нет ввода для " + field);
            }
            s = s.trim();
            if (allowNull && s.isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(s);
            } catch (Exception e) {
                System.out.println("Ошибка: неверный формат даты-времени.");
            }
        }
    }

    public City readCity() {
        City city = new City();
        fillCityFields(city, true);
        return city;
    }

    private void fillCityFields(City city, boolean allowGovernorNull) {
        city.setName(readNonEmptyString("name (не пустое)"));

        Coordinates coordinates = new Coordinates();
        coordinates.setX(readInt("coordinates.x (int > -288)", v -> v > -288, "x должен быть > -288"));
        coordinates.setY(readInt("coordinates.y (Integer, <= 882)", v -> v <= 882, "y должен быть <= 882"));
        city.setCoordinates(coordinates);

        city.setArea(readDoubleObj("area (Double > 0)", v -> v > 0, "area должен быть > 0"));
        city.setPopulation(readLongObj("population (Long > 0)", v -> v > 0, "population должен быть > 0"));
        city.setMetersAboveSeaLevel(readLongPrim("metersAboveSeaLevel (long)"));
        city.setClimate(readEnum("climate", Climate.class, false));
        city.setGovernment(readEnum("government", Government.class, false));
        city.setStandardOfLiving(readEnum("standardOfLiving", StandardOfLiving.class, true));

        if (allowGovernorNull) {
            String answer = prompt("Вводить governor? (y/N): ");
            if (answer != null && answer.trim().equalsIgnoreCase("y")) {
                city.setGovernor(readHuman());
            }
        } else {
            city.setGovernor(readHuman());
        }
    }

    private Human readHuman() {
        Human human = new Human();
        human.setName(readNonEmptyString("governor.name (не пустое)"));
        human.setHeight(readFloatObj("governor.height (Float > 0)", v -> v > 0, "height должен быть > 0"));
        human.setBirthday(readDateTime("governor.birthday", true));
        return human;
    }

    public Human readGovernorValue() {
        String answer = prompt("Удалять по governor=null? (y/N): ");
        if (answer != null && answer.trim().equalsIgnoreCase("y")) {
            return null;
        }
        return readHuman();
    }
}
