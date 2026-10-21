import java.io.File;
import java.io.FileNotFoundException;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class ClientMain {
    private UdpClient udpClient;
    private ClientCommandParser parser;
    private final Set<String> scriptStack = new HashSet<>();

    public ClientMain(UdpClient udpClient) {
        this.udpClient = udpClient;
    }

    public static void main(String[] args) {
        String host = args.length >= 1 ? args[0] : "localhost";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : 5555;
        int timeoutMillis = args.length >= 3 ? Integer.parseInt(args[2]) : 5000;

        try (UdpClient udpClient = new UdpClient(host, port, timeoutMillis)) {
            ClientMain client = new ClientMain(udpClient);
            client.start();
        } catch (Exception e) {
            System.out.println("Не удалось запустить клиент: " + e.getMessage());
        }
    }

    private void start() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Добро пожаловать! Введите:");
        System.out.println("  1 — войти");
        System.out.println("  2 — зарегистрироваться");
        System.out.println("  3 — выйти");

        String login = null;
        String password = null;

        while (login == null) {
            System.out.print("Выбор: ");
            String choice = scanner.hasNextLine() ? scanner.nextLine().trim() : "3";

            switch (choice) {
                case "1" -> {
                    String[] creds = readCredentials(scanner);
                    try {
                        CommandRequest req = new CommandRequest(
                                CommandType.LOGIN, creds[0], creds[1]);
                        CommandResponse resp = udpClient.sendWithRetries(req, 3);
                        System.out.println(resp.getMessage());
                        if (resp.isSuccess()) {
                            login = creds[0];
                            password = creds[1];
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Сервер недоступен.");
                    } catch (Exception e) {
                        System.out.println("Ошибка: " + e.getMessage());
                    }
                }
                case "2" -> {
                    String[] creds = readCredentials(scanner);
                    try {
                        CommandRequest req = new CommandRequest(
                                CommandType.REGISTER, creds[0], creds[1]);
                        CommandResponse resp = udpClient.sendWithRetries(req, 3);
                        System.out.println(resp.getMessage());
                        if (resp.isSuccess()) {
                            // После регистрации сразу логинимся
                            CommandRequest loginReq = new CommandRequest(
                                    CommandType.LOGIN, creds[0], creds[1]);
                            CommandResponse loginResp = udpClient.sendWithRetries(loginReq, 3);
                            if (loginResp.isSuccess()) {
                                login = creds[0];
                                password = creds[1];
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Сервер недоступен.");
                    } catch (Exception e) {
                        System.out.println("Ошибка: " + e.getMessage());
                    }
                }
                case "3" -> { System.out.println("Выход."); return; }
                default -> System.out.println("Введите 1, 2 или 3.");
            }
        }

        System.out.println("Добро пожаловать, " + login + "! Введите help для списка команд.");
        this.parser = new ClientCommandParser(login, password);
        interactiveLoop(scanner);
    }

    private String[] readCredentials(Scanner scanner) {
        System.out.print("Логин: ");
        String login = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        System.out.print("Пароль: ");
        String password = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        return new String[]{login, password};
    }

    private void interactiveLoop(Scanner scanner) {
        InputManager input = new InputManager(scanner, true);

        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            if (!handleLine(line, input)) break;
        }

        System.out.println("Клиент завершил работу.");
    }

    private boolean handleLine(String line, InputManager input) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) return true;

        if (parts[0].equals("exit")) return false;

        if (parts[0].equals("execute_script")) {
            if (parts.length != 2) { System.out.println("Использование: execute_script file_name"); return true; }
            executeScript(parts[1]);
            return true;
        }

        try {
            CommandRequest request = parser.parse(line, input);
            if (request == null) return true;
            CommandResponse response = udpClient.sendWithRetries(request, 3);
            System.out.println(response.getMessage());
        } catch (SocketTimeoutException e) {
            System.out.println("Сервер временно недоступен. Команда не выполнена.");
        } catch (Exception e) {
            System.out.println("Ошибка клиента: " + e.getMessage());
        }
        return true;
    }

    private void executeScript(String fileName) {
        File file = new File(fileName);
        String absolutePath = file.getAbsolutePath();
        if (scriptStack.contains(absolutePath)) {
            System.out.println("Рекурсия execute_script для: " + absolutePath);
            return;
        }
        scriptStack.add(absolutePath);
        try (Scanner scriptScanner = new Scanner(file)) {
            InputManager scriptInput = new InputManager(scriptScanner, false);
            while (scriptScanner.hasNextLine()) {
                String line = scriptScanner.nextLine().trim();
                if (line.isEmpty()) continue;
                System.out.println("$ " + line);
                if (!handleLine(line, scriptInput)) break;
            }
        } catch (FileNotFoundException e) {
            System.out.println("Файл скрипта не найден: " + fileName);
        } finally {
            scriptStack.remove(absolutePath);
        }
    }
}
