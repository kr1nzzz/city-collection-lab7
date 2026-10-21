import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.sql.SQLException;
import java.util.concurrent.*;

public class ServerMain {
    private static final Logger logger = LogManager.getLogger(ServerMain.class);
    private static final int FIXED_POOL_SIZE = 4;

    private static volatile boolean running = true;

    public static void main(String[] args) {
        DbConfig config;
        try {
            config = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsage();
            return;
        }

        String jdbcUrl = "jdbc:postgresql://" + config.host() + ":5433/" + config.database();
        logger.info("Подключение к PostgreSQL: {}", jdbcUrl);

        try (DatabaseManager db = new DatabaseManager(jdbcUrl, config.user(), config.password())) {
            db.initialize();

            CollectionManager collectionManager = new CollectionManager(db);
            collectionManager.load();
            logger.info("Коллекция загружена. Элементов: {}", collectionManager.size());

            CommandProcessor processor = new CommandProcessor(collectionManager, db);
            ExecutorService fixedPool = Executors.newFixedThreadPool(FIXED_POOL_SIZE);
            ForkJoinPool forkJoinPool = new ForkJoinPool();

            try (DatagramChannel channel = DatagramChannel.open();
                 BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {

                channel.bind(new InetSocketAddress(config.port()));
                channel.configureBlocking(false);
                logger.info("Сервер запущен на порту {}. Команды: exit", config.port());
                System.out.println("Сервер запущен на порту " + config.port() + ". Команды: exit");

                RequestReader requestReader = new RequestReader();
                ResponseSender responseSender = new ResponseSender();

                while (running) {
                    handleConsole(console, running);

                    ReceivedPacket packet = new ServerReceiver().receive(channel);
                    if (packet == null) {
                        Thread.sleep(10);
                        continue;
                    }

                    logger.info("Получен пакет от {}", packet.getClientAddress());

                    final ReceivedPacket finalPacket = packet;
                    Thread readerThread = new Thread(() -> {
                        try {
                            CommandRequest request = requestReader.read(finalPacket);
                            if (request == null) {
                                logger.debug("Фрагмент сохранён, ждём остальные части");
                                return;
                            }
                            logger.info("Полный запрос от {}: {}", finalPacket.getClientAddress(), request.getType());

                            fixedPool.submit(() -> {
                                CommandResponse response = processor.process(request);
                                logger.info("Команда {} выполнена, success={}",
                                        request.getType(), response.isSuccess());

                                forkJoinPool.submit(() -> {
                                    try {
                                        responseSender.send(channel,
                                                finalPacket.getClientAddress(), response);
                                        logger.info("Ответ отправлен клиенту {}",
                                                finalPacket.getClientAddress());
                                    } catch (IOException e) {
                                        logger.warn("Ошибка отправки ответа", e);
                                    }
                                });
                            });

                        } catch (Exception e) {
                            logger.warn("Ошибка чтения/обработки запроса", e);
                            try {
                                CommandResponse err = new CommandResponse(false,
                                        "Ошибка обработки запроса: " + e.getMessage());
                                responseSender.send(channel, finalPacket.getClientAddress(), err);
                            } catch (IOException ignore) {}
                        }
                    }, "reader-" + finalPacket.getClientAddress());

                    readerThread.start();
                }

                fixedPool.shutdown();
                forkJoinPool.shutdown();
            }

        } catch (SQLException e) {
            logger.fatal("Ошибка PostgreSQL", e);
            System.err.println("Ошибка PostgreSQL: " + e.getMessage());
        } catch (Exception e) {
            logger.fatal("Критическая ошибка сервера", e);
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    private static void handleConsole(BufferedReader console, boolean isRunning) throws IOException {
        if (!console.ready()) return;
        String line = console.readLine();
        if (line == null) { running = false; return; }
        switch (line.trim()) {
            case "exit" -> {
                logger.info("Серверная команда exit");
                running = false;
            }
            case "" -> {}
            default -> System.out.println("Неизвестная команда. Доступно: exit");
        }
    }


    private static DbConfig parseArgs(String[] args) {
        if (args == null || (args.length != 1 && args.length != 3 && args.length != 5))
            throw new IllegalArgumentException("Неверное количество аргументов.");

        int port = parsePort(args[0]);

        String dbUser, dbPassword, dbHost, dbName;

        if (args.length >= 3) {
            dbUser = args[1];
            dbPassword = args[2];
        } else {
            dbUser = System.getenv("PGUSER");
            dbPassword = System.getenv("PGPASSWORD");
        }

        if (args.length == 5) {
            dbHost = args[3];
            dbName = args[4];
        } else {
            dbHost = getenvOrDefault("PGHOST", "pg");
            dbName = getenvOrDefault("PGDATABASE", "studs");
        }

        if (dbUser == null || dbUser.isBlank())
            throw new IllegalArgumentException(
                    "Не указан пользователь БД. Передайте аргументом или через PGUSER.");
        if (dbPassword == null) dbPassword = "";

        return new DbConfig(port, dbUser, dbPassword, dbHost, dbName);
    }

    private static int parsePort(String value) {
        try {
            int p = Integer.parseInt(value);
            if (p < 1 || p > 65535) throw new NumberFormatException();
            return p;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("port должен быть числом от 1 до 65535.");
        }
    }

    private static String getenvOrDefault(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static void printUsage() {
        System.err.println("Использование:");
        System.err.println("  java -jar server.jar <port> <dbUser> <dbPassword>");
        System.err.println("  java -jar server.jar <port> <dbUser> <dbPassword> <dbHost> <dbName>");
        System.err.println("  java -jar server.jar <port>   # dbUser/dbPassword из PGUSER/PGPASSWORD");
        System.err.println("По умолчанию host=pg, database=studs.");
        System.err.println();
        System.err.println("Примеры:");
        System.err.println("  java -jar server.jar 5555 s466817 s466817");
        System.err.println("  java -jar server.jar 5555 postgres pass localhost studs");
    }

    private record DbConfig(int port, String user, String password, String host, String database) {}
}
