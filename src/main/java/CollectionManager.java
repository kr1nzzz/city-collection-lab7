import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CollectionManager {
    private static final Logger logger = LogManager.getLogger(CollectionManager.class);

    private final Vector<City> cities = new Vector<>();
    private final LocalDateTime initTime = LocalDateTime.now();
    private final DatabaseManager db;

    public CollectionManager(DatabaseManager db) {
        this.db = db;
    }


    public synchronized void load() throws SQLException {
        cities.clear();
        cities.addAll(db.loadAll());
        logger.info("Коллекция загружена из БД. Элементов: {}", cities.size());
    }


    public synchronized int size() {
        return cities.size();
    }

    public synchronized String info() {
        return "Тип коллекции: " + cities.getClass().getName()
                + "\nВремя инициализации: " + initTime
                + "\nКоличество элементов: " + cities.size()
                + "\nХранилище: PostgreSQL";
    }

    public synchronized boolean containsId(long id) {
        return cities.stream().anyMatch(c -> c.getId() == id);
    }

    public synchronized Optional<City> findById(long id) {
        return cities.stream().filter(c -> c.getId() == id).findFirst();
    }

    public synchronized boolean isOwner(City city, DatabaseManager.DbUser user) {
        return city != null && user != null && user.login().equals(city.getOwnerLogin());
    }

    public synchronized List<City> sortedBySizeForClient() {
        return cities.stream()
                .sorted(Comparator.comparingDouble(City::sizeValue)
                        .thenComparing(City::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(City::getId))
                .collect(Collectors.toList());
    }

    public synchronized City minByClimate() {
        return cities.stream()
                .filter(c -> c.getClimate() != null)
                .min(Comparator.comparing(City::getClimate))
                .orElse(null);
    }

    public synchronized List<Human> governorsAscending() {
        return cities.stream()
                .map(City::getGovernor)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }


    public synchronized City add(City source, DatabaseManager.DbUser owner) throws SQLException {
        City stored = db.insertCity(source, owner);
        cities.add(stored);
        return stored;
    }


    public synchronized boolean update(long id, City newCity, DatabaseManager.DbUser owner) throws SQLException {
        Optional<City> existing = findByIdLocked(id);
        if (existing.isEmpty()) return false;
        boolean dbOk = db.updateCity(id, newCity, existing.get().getCreationDate(), owner);
        if (!dbOk) return false;
        int idx = IntStream.range(0, cities.size())
                .filter(i -> cities.get(i).getId() == id)
                .findFirst().orElse(-1);
        if (idx >= 0) {
            City updated = copyUpdated(existing.get(), newCity, owner.login());
            cities.set(idx, updated);
        }
        return true;
    }


    public synchronized boolean removeById(long id, DatabaseManager.DbUser owner) throws SQLException {
        boolean dbOk = db.deleteByIdOwned(id, owner);
        if (dbOk) cities.removeIf(c -> c.getId() == id);
        return dbOk;
    }


    public synchronized int clear(DatabaseManager.DbUser owner) throws SQLException {
        int deleted = db.deleteAllOwned(owner);
        if (deleted > 0) cities.removeIf(c -> owner.login().equals(c.getOwnerLogin()));
        return deleted;
    }


    public synchronized int removeAllByGovernor(Human governor,
                                                  DatabaseManager.DbUser owner) throws SQLException {
        int deleted = db.deleteByGovernorOwned(governor, owner);
        if (deleted > 0) {
            cities.removeIf(c -> {
                if (!owner.login().equals(c.getOwnerLogin())) return false;
                Human g = c.getGovernor();
                if (governor == null) return g == null;
                if (g == null) return false;
                return g.equals(governor);
            });
        }
        return deleted;
    }


    public synchronized int removeLower(City pivot, DatabaseManager.DbUser owner) throws SQLException {
        List<Long> deletedIds = db.deleteLowerOwned(pivot, owner);
        if (!deletedIds.isEmpty()) {
            cities.removeIf(c -> deletedIds.contains(c.getId()));
        }
        return deletedIds.size();
    }


    public synchronized void reorder() {
        List<City> reversed = IntStream.range(0, cities.size())
                .mapToObj(i -> cities.get(cities.size() - 1 - i))
                .collect(Collectors.toList());
        cities.clear();
        cities.addAll(reversed);
    }


    private Optional<City> findByIdLocked(long id) {
        return cities.stream().filter(c -> c.getId() == id).findFirst();
    }

    private City copyUpdated(City existing, City newData, String ownerLogin) {
        newData.setId(existing.getId());
        newData.setCreationDate(existing.getCreationDate());
        newData.setOwnerLogin(ownerLogin);
        return newData;
    }
}
