import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DatabaseManager implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger(DatabaseManager.class);

    private final Connection connection;

    public DatabaseManager(String jdbcUrl, String user, String password) throws SQLException {
        this.connection = DriverManager.getConnection(jdbcUrl, user, password);
        this.connection.setAutoCommit(true);
        logger.info("Подключение к PostgreSQL установлено: {}", jdbcUrl);
    }



    public void initialize() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS app_users (
                        id            SERIAL PRIMARY KEY,
                        login         TEXT NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL
                    )
                    """);

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cities (
                        id                    SERIAL PRIMARY KEY,
                        name                  TEXT             NOT NULL,
                        coord_x               INTEGER          NOT NULL,
                        coord_y               INTEGER,
                        creation_date         TIMESTAMP        NOT NULL,
                        area                  DOUBLE PRECISION,
                        population            BIGINT,
                        meters_above_sea_level BIGINT          NOT NULL,
                        climate               TEXT             NOT NULL,
                        government            TEXT             NOT NULL,
                        standard_of_living    TEXT,
                        governor_name         TEXT,
                        governor_height       REAL,
                        governor_birthday     TIMESTAMP,
                        owner_id              INTEGER          NOT NULL REFERENCES app_users(id) ON DELETE CASCADE
                    )
                    """);
        }
        logger.info("Схема БД инициализирована");
    }


    public boolean registerUser(String login, String password) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO app_users(login, password_hash) VALUES (?, ?)")) {
            ps.setString(1, normalizeLogin(login));
            ps.setString(2, hashPassword(password));
            ps.executeUpdate();
            logger.info("Зарегистрирован пользователь: {}", login);
            return true;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) return false;
            throw e;
        }
    }

    public Optional<DbUser> authenticate(String login, String password) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, login, password_hash FROM app_users WHERE login = ?")) {
            ps.setString(1, normalizeLogin(login));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String expected = rs.getString("password_hash");
                if (!Objects.equals(expected, hashPassword(password))) return Optional.empty();
                return Optional.of(new DbUser(rs.getInt("id"), rs.getString("login")));
            }
        }
    }


    public List<City> loadAll() throws SQLException {
        String sql = """
                SELECT c.*, u.login AS owner_login
                FROM cities c
                JOIN app_users u ON c.owner_id = u.id
                ORDER BY c.name
                """;
        List<City> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapRow(rs));
        }
        logger.info("Загружено {} городов из БД", result.size());
        return result;
    }

    public City insertCity(City source, DbUser owner) throws SQLException {
        String sql = """
                INSERT INTO cities (
                    name, coord_x, coord_y, creation_date, area, population,
                    meters_above_sea_level, climate, government, standard_of_living,
                    governor_name, governor_height, governor_birthday, owner_id
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                RETURNING id, creation_date
                """;
        LocalDateTime now = LocalDateTime.now();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            fillFields(ps, source, now, owner.id(), 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("БД не вернула вставленный объект.");
                long id = rs.getLong("id");
                LocalDateTime dbDate = rs.getTimestamp("creation_date").toLocalDateTime();
                logger.info("Вставлен город id={} owner={}", id, owner.login());
                return copyWithServerFields(source, id, dbDate, owner.login());
            }
        }
    }

    public boolean updateCity(long id, City source, LocalDateTime creationDate,
                               DbUser owner) throws SQLException {
        String sql = """
                UPDATE cities SET
                    name=?, coord_x=?, coord_y=?, creation_date=?, area=?, population=?,
                    meters_above_sea_level=?, climate=?, government=?, standard_of_living=?,
                    governor_name=?, governor_height=?, governor_birthday=?
                WHERE id=? AND owner_id=?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int i = fillFieldsWithoutOwner(ps, source, creationDate, 1);
            ps.setLong(i++, id);
            ps.setInt(i, owner.id());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteByIdOwned(long id, DbUser owner) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM cities WHERE id=? AND owner_id=?")) {
            ps.setLong(1, id);
            ps.setInt(2, owner.id());
            return ps.executeUpdate() > 0;
        }
    }

    public int deleteAllOwned(DbUser owner) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM cities WHERE owner_id=?")) {
            ps.setInt(1, owner.id());
            return ps.executeUpdate();
        }
    }

    public int deleteByGovernorOwned(Human governor, DbUser owner) throws SQLException {
        String sql;
        if (governor == null) {
            sql = "DELETE FROM cities WHERE owner_id=? AND governor_name IS NULL";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, owner.id());
                return ps.executeUpdate();
            }
        } else {
            sql = """
                    DELETE FROM cities WHERE owner_id=?
                    AND governor_name=?
                    AND (governor_height=? OR (governor_height IS NULL AND ?::real IS NULL))
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, owner.id());
                ps.setString(2, governor.getName());
                ps.setObject(3, governor.getHeight());
                ps.setObject(4, governor.getHeight());
                return ps.executeUpdate();
            }
        }
    }

    public List<Long> deleteLowerOwned(City pivot, DbUser owner) throws SQLException {
        String sql = "SELECT id FROM cities WHERE owner_id=?";
        List<Long> toDelete = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, owner.id());
            try (ResultSet rs = ps.executeQuery()) {
            }
        }

        String fullSql = """
                SELECT c.*, u.login AS owner_login
                FROM cities c JOIN app_users u ON c.owner_id = u.id
                WHERE c.owner_id=?
                """;
        try (PreparedStatement ps = connection.prepareStatement(fullSql)) {
            ps.setInt(1, owner.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    City city = mapRow(rs);
                    if (city.compareTo(pivot) < 0) toDelete.add(city.getId());
                }
            }
        }

        if (!toDelete.isEmpty()) {
            for (Long id : toDelete) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM cities WHERE id=? AND owner_id=?")) {
                    ps.setLong(1, id);
                    ps.setInt(2, owner.id());
                    ps.executeUpdate();
                }
            }
        }
        return toDelete;
    }


    private int fillFields(PreparedStatement ps, City c, LocalDateTime creationDate,
                           int ownerId, int start) throws SQLException {
        int i = fillFieldsWithoutOwner(ps, c, creationDate, start);
        ps.setInt(i++, ownerId);
        return i;
    }

    private int fillFieldsWithoutOwner(PreparedStatement ps, City c,
                                        LocalDateTime creationDate, int start) throws SQLException {
        int i = start;
        ps.setString(i++, c.getName());
        ps.setInt(i++, c.getCoordinates().getX());
        ps.setObject(i++, c.getCoordinates().getY());
        ps.setTimestamp(i++, Timestamp.valueOf(creationDate));
        ps.setObject(i++, c.getArea());
        ps.setObject(i++, c.getPopulation());
        ps.setLong(i++, c.getMetersAboveSeaLevel());
        ps.setString(i++, c.getClimate().name());
        ps.setString(i++, c.getGovernment().name());
        ps.setString(i++, c.getStandardOfLiving() != null ? c.getStandardOfLiving().name() : null);
        if (c.getGovernor() != null) {
            ps.setString(i++, c.getGovernor().getName());
            ps.setObject(i++, c.getGovernor().getHeight());
            ps.setObject(i++, c.getGovernor().getBirthday() != null
                    ? Timestamp.valueOf(c.getGovernor().getBirthday()) : null);
        } else {
            ps.setNull(i++, Types.VARCHAR);
            ps.setNull(i++, Types.REAL);
            ps.setNull(i++, Types.TIMESTAMP);
        }
        return i;
    }

    private City mapRow(ResultSet rs) throws SQLException {
        City city = new City();
        city.setId(rs.getLong("id"));
        city.setName(rs.getString("name"));
        city.setCreationDate(rs.getTimestamp("creation_date").toLocalDateTime());

        Coordinates coords = new Coordinates();
        coords.setX(rs.getInt("coord_x"));
        Object coordY = rs.getObject("coord_y");
        coords.setY(coordY != null ? ((Number) coordY).intValue() : null);
        city.setCoordinates(coords);

        city.setArea(rs.getObject("area") != null ? rs.getDouble("area") : null);
        city.setPopulation(rs.getObject("population") != null ? rs.getLong("population") : null);
        city.setMetersAboveSeaLevel(rs.getLong("meters_above_sea_level"));
        city.setClimate(Climate.valueOf(rs.getString("climate")));
        city.setGovernment(Government.valueOf(rs.getString("government")));
        String sol = rs.getString("standard_of_living");
        city.setStandardOfLiving(sol != null ? StandardOfLiving.valueOf(sol) : null);

        String govName = rs.getString("governor_name");
        if (govName != null) {
            Human gov = new Human();
            gov.setName(govName);
            float govHeight = rs.getFloat("governor_height");
            gov.setHeight(rs.wasNull() ? null : govHeight);
            Timestamp govBirthday = rs.getTimestamp("governor_birthday");
            gov.setBirthday(govBirthday != null ? govBirthday.toLocalDateTime() : null);
            city.setGovernor(gov);
        }

        city.setOwnerLogin(rs.getString("owner_login"));
        return city;
    }

    private static City copyWithServerFields(City src, long id,
                                              LocalDateTime creationDate, String ownerLogin) {
        City city = new City();
        city.setId(id);
        city.setName(src.getName());
        city.setCoordinates(src.getCoordinates());
        city.setCreationDate(creationDate);
        city.setArea(src.getArea());
        city.setPopulation(src.getPopulation());
        city.setMetersAboveSeaLevel(src.getMetersAboveSeaLevel());
        city.setClimate(src.getClimate());
        city.setGovernment(src.getGovernment());
        city.setStandardOfLiving(src.getStandardOfLiving());
        city.setGovernor(src.getGovernor());
        city.setOwnerLogin(ownerLogin);
        return city;
    }


    public static String normalizeLogin(String login) {
        if (login == null || login.trim().isEmpty())
            throw new IllegalArgumentException("Логин не должен быть пустым.");
        return login.trim();
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            String normalized = password == null ? "" : password;
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 недоступен", e);
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
        logger.info("Соединение с БД закрыто");
    }

    public record DbUser(int id, String login) {}
}
