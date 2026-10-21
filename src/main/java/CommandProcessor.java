import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class CommandProcessor {
    private static final Logger logger = LogManager.getLogger(CommandProcessor.class);

    private final CollectionManager collectionManager;
    private final DatabaseManager db;

    private final Deque<String> history = new ArrayDeque<>();
    private final Object historyLock = new Object();

    public CommandProcessor(CollectionManager collectionManager, DatabaseManager db) {
        this.collectionManager = collectionManager;
        this.db = db;
    }

    public CommandResponse process(CommandRequest request) {
        if (request == null || request.getType() == null)
            return new CommandResponse(false, "Пустой запрос.");

        try {
            return switch (request.getType()) {
                case REGISTER -> handleRegister(request);
                case LOGIN    -> handleLogin(request);
                default       -> processAuthenticated(request);
            };
        } catch (AuthException e) {
            return new CommandResponse(false, e.getMessage());
        } catch (SQLException e) {
            logger.error("Ошибка БД при команде {}", request.getType(), e);
            return new CommandResponse(false, "Ошибка БД: " + e.getMessage());
        } catch (Exception e) {
            logger.warn("Ошибка при выполнении команды {}", request.getType(), e);
            return new CommandResponse(false, "Ошибка: " + e.getMessage());
        }
    }


    private CommandResponse handleRegister(CommandRequest req) throws SQLException {
        DatabaseManager.normalizeLogin(req.getLogin());
        boolean created = db.registerUser(req.getLogin(), req.getPassword());
        if (!created) return new CommandResponse(false, "Пользователь с таким логином уже существует.");
        logger.info("Зарегистрирован: {}", req.getLogin());
        return new CommandResponse(true, "Регистрация успешна. Теперь войдите командой login.");
    }

    private CommandResponse handleLogin(CommandRequest req) throws SQLException, AuthException {
        DatabaseManager.DbUser user = requireAuth(req);
        logger.info("Авторизован: {}", user.login());
        return new CommandResponse(true, "Авторизация успешна. Добро пожаловать, " + user.login() + "!");
    }

    private CommandResponse processAuthenticated(CommandRequest req)
            throws SQLException, AuthException {
        DatabaseManager.DbUser user = requireAuth(req);
        String cmdName = req.getType().name().toLowerCase();
        addToHistory(cmdName);
        logger.info("Команда {} от {}", cmdName, user.login());

        return switch (req.getType()) {
            case HELP    -> help();
            case INFO    -> new CommandResponse(true, collectionManager.info());
            case SHOW    -> show();
            case ADD     -> add(asCity(req), user);
            case UPDATE  -> update(asUpdateArgument(req), user);
            case REMOVE_BY_ID -> removeById(asLong(req), user);
            case CLEAR   -> clear(user);
            case REMOVE_LOWER -> removeLower(asCity(req), user);
            case REORDER -> reorder();
            case HISTORY -> history();
            case REMOVE_ALL_BY_GOVERNOR -> removeAllByGovernor(req.getArgument(), user);
            case MIN_BY_CLIMATE -> minByClimate();
            case PRINT_FIELD_ASCENDING_GOVERNOR -> printFieldAscendingGovernor();
            default -> new CommandResponse(false, "Неизвестная команда.");
        };
    }

    private DatabaseManager.DbUser requireAuth(CommandRequest req)
            throws SQLException, AuthException {
        if (req.getLogin() == null || req.getLogin().trim().isEmpty())
            throw new AuthException("Сначала выполните login или register.");
        Optional<DatabaseManager.DbUser> user = db.authenticate(req.getLogin(), req.getPassword());
        if (user.isEmpty()) throw new AuthException("Неверный логин или пароль.");
        return user.get();
    }


    private CommandResponse help() {
        return new CommandResponse(true,
                "Доступные команды:\n" +
                " register               — регистрация\n" +
                " login                  — авторизация\n" +
                " help                   — вывести справку\n" +
                " info                   — информация о коллекции\n" +
                " show                   — вывести элементы, отсортированные по area\n" +
                " add                    — добавить новый элемент\n" +
                " update id              — обновить свой элемент по id\n" +
                " remove_by_id id        — удалить свой элемент по id\n" +
                " clear                  — очистить свои элементы\n" +
                " execute_script file    — выполнить скрипт на стороне клиента\n" +
                " exit                   — завершить клиент\n" +
                " remove_lower           — удалить свои элементы меньше заданного\n" +
                " reorder                — развернуть порядок коллекции\n" +
                " history                — последние 15 команд\n" +
                " remove_all_by_governor — удалить свои элементы с заданным governor\n" +
                " min_by_climate         — вывести элемент с минимальным climate\n" +
                " print_field_ascending_governor — вывести governor по возрастанию\n\n" +
                "Команда save недоступна (коллекция хранится в PostgreSQL).");
    }

    private CommandResponse show() {
        List<City> sorted = collectionManager.sortedBySizeForClient();
        if (sorted.isEmpty()) return new CommandResponse(true, "Коллекция пуста.", sorted);
        String message = sorted.stream().map(City::toString).collect(Collectors.joining("\n"));
        return new CommandResponse(true, message, sorted);
    }

    private CommandResponse add(City city, DatabaseManager.DbUser owner) throws SQLException {
        validateCity(city);
        City stored = collectionManager.add(city, owner);
        return new CommandResponse(true, "Элемент добавлен. id=" + stored.getId());
    }

    private CommandResponse update(UpdateArgument arg, DatabaseManager.DbUser owner) throws SQLException {
        if (arg == null) return new CommandResponse(false, "Нет аргументов для update.");
        validateCity(arg.getCity());
        Optional<City> existing = collectionManager.findById(arg.getId());
        if (existing.isEmpty())
            return new CommandResponse(false, "Элемента с id=" + arg.getId() + " не существует.");
        if (!collectionManager.isOwner(existing.get(), owner))
            return new CommandResponse(false, "Нельзя изменять объект другого пользователя.");
        boolean ok = collectionManager.update(arg.getId(), arg.getCity(), owner);
        return new CommandResponse(ok, ok ? "Элемент обновлён." : "Не удалось обновить.");
    }

    private CommandResponse removeById(Long id, DatabaseManager.DbUser owner) throws SQLException {
        if (id == null) return new CommandResponse(false, "id не указан.");
        Optional<City> existing = collectionManager.findById(id);
        if (existing.isEmpty()) return new CommandResponse(false, "Элемент с id=" + id + " не найден.");
        if (!collectionManager.isOwner(existing.get(), owner))
            return new CommandResponse(false, "Нельзя удалять объект другого пользователя.");
        boolean ok = collectionManager.removeById(id, owner);
        return new CommandResponse(ok, ok ? "Удалено." : "Не удалось удалить.");
    }

    private CommandResponse clear(DatabaseManager.DbUser owner) throws SQLException {
        int removed = collectionManager.clear(owner);
        return new CommandResponse(true, "Удалено ваших элементов: " + removed);
    }

    private CommandResponse removeLower(City pivot, DatabaseManager.DbUser owner) throws SQLException {
        validateCity(pivot);
        int removed = collectionManager.removeLower(pivot, owner);
        return new CommandResponse(true, "Удалено ваших элементов: " + removed);
    }

    private CommandResponse reorder() {
        collectionManager.reorder();
        return new CommandResponse(true, "Порядок коллекции обращён.");
    }

    private CommandResponse history() {
        synchronized (historyLock) {
            if (history.isEmpty()) return new CommandResponse(true, "История команд пуста.");
            return new CommandResponse(true, String.join("\n", history));
        }
    }

    private CommandResponse removeAllByGovernor(Serializable argument,
                                                  DatabaseManager.DbUser owner) throws SQLException {
        Human governor = null;
        if (argument != null) {
            if (!(argument instanceof Human))
                return new CommandResponse(false, "Аргумент должен быть Human или null.");
            governor = (Human) argument;
            validateHuman(governor, "governor");
        }
        int removed = collectionManager.removeAllByGovernor(governor, owner);
        return new CommandResponse(true, "Удалено ваших элементов: " + removed);
    }

    private CommandResponse minByClimate() {
        City city = collectionManager.minByClimate();
        return new CommandResponse(true, city == null ? "Коллекция пуста." : city.toString());
    }

    private CommandResponse printFieldAscendingGovernor() {
        List<Human> governors = collectionManager.governorsAscending();
        if (governors.isEmpty()) return new CommandResponse(true, "В коллекции нет governor.");
        return new CommandResponse(true,
                governors.stream().map(Human::toString).collect(Collectors.joining("\n")));
    }


    private void addToHistory(String cmd) {
        synchronized (historyLock) {
            history.addLast(cmd);
            while (history.size() > 15) history.removeFirst();
        }
    }

    private City asCity(CommandRequest req) {
        if (!(req.getArgument() instanceof City))
            throw new IllegalArgumentException("Ожидался объект City.");
        return (City) req.getArgument();
    }

    private UpdateArgument asUpdateArgument(CommandRequest req) {
        if (!(req.getArgument() instanceof UpdateArgument))
            throw new IllegalArgumentException("Ожидался объект UpdateArgument.");
        return (UpdateArgument) req.getArgument();
    }

    private Long asLong(CommandRequest req) {
        if (!(req.getArgument() instanceof Long))
            throw new IllegalArgumentException("Ожидался Long.");
        return (Long) req.getArgument();
    }

    private void validateCity(City city) {
        if (city == null) throw new IllegalArgumentException("City не может быть null.");
        if (city.getName() == null || city.getName().isBlank())
            throw new IllegalArgumentException("name не может быть пустым.");
        if (city.getCoordinates() == null)
            throw new IllegalArgumentException("coordinates не может быть null.");
        if (city.getCoordinates().getX() <= -288)
            throw new IllegalArgumentException("coordinates.x должен быть > -288.");
        if (city.getCoordinates().getY() == null || city.getCoordinates().getY() > 882)
            throw new IllegalArgumentException("coordinates.y должен быть not null и <= 882.");
        if (city.getArea() == null || city.getArea() <= 0)
            throw new IllegalArgumentException("area должен быть > 0.");
        if (city.getPopulation() == null || city.getPopulation() <= 0)
            throw new IllegalArgumentException("population должен быть > 0.");
        if (city.getClimate() == null)
            throw new IllegalArgumentException("climate не может быть null.");
        if (city.getGovernment() == null)
            throw new IllegalArgumentException("government не может быть null.");
        if (city.getGovernor() != null) validateHuman(city.getGovernor(), "governor");
    }

    private void validateHuman(Human human, String field) {
        Objects.requireNonNull(human, field + " не может быть null.");
        if (human.getName() == null || human.getName().isBlank())
            throw new IllegalArgumentException(field + ".name не может быть пустым.");
        if (human.getHeight() == null || human.getHeight() <= 0)
            throw new IllegalArgumentException(field + ".height должен быть > 0.");
    }

    private static final class AuthException extends Exception {
        AuthException(String msg) { super(msg); }
    }
}
