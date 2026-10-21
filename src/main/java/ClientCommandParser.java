public class ClientCommandParser {
    private final String login;
    private final String password;

    public ClientCommandParser(String login, String password) {
        this.login = login;
        this.password = password;
    }

    public CommandRequest parse(String line, InputManager input) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) return null;

        String commandName = parts[0];
        return switch (commandName) {
            case "help"   -> noArgs(parts, CommandType.HELP);
            case "info"   -> noArgs(parts, CommandType.INFO);
            case "show"   -> noArgs(parts, CommandType.SHOW);
            case "add"    -> new CommandRequest(CommandType.ADD, input.readCity(), login, password);
            case "update" -> parseUpdate(parts, input);
            case "remove_by_id" -> parseRemoveById(parts);
            case "clear"  -> noArgs(parts, CommandType.CLEAR);
            case "remove_lower" ->
                    new CommandRequest(CommandType.REMOVE_LOWER, input.readCity(), login, password);
            case "reorder" -> noArgs(parts, CommandType.REORDER);
            case "history" -> noArgs(parts, CommandType.HISTORY);
            case "remove_all_by_governor" ->
                    new CommandRequest(CommandType.REMOVE_ALL_BY_GOVERNOR,
                            input.readGovernorValue(), login, password);
            case "min_by_climate" -> noArgs(parts, CommandType.MIN_BY_CLIMATE);
            case "print_field_ascending_governor" ->
                    noArgs(parts, CommandType.PRINT_FIELD_ASCENDING_GOVERNOR);
            case "save" -> {
                System.out.println("Команда save недоступна — коллекция хранится в PostgreSQL.");
                yield null;
            }
            default -> {
                System.out.println("Неизвестная команда: " + commandName + ". Введите help.");
                yield null;
            }
        };
    }

    private CommandRequest noArgs(String[] parts, CommandType type) {
        if (parts.length != 1) {
            System.out.println("Команда " + parts[0] + " не принимает аргументы.");
            return null;
        }
        return new CommandRequest(type, null, login, password);
    }

    private CommandRequest parseUpdate(String[] parts, InputManager input) {
        if (parts.length != 2) { System.out.println("Использование: update id"); return null; }
        try {
            long id = Long.parseLong(parts[1]);
            City city = input.readCity();
            return new CommandRequest(CommandType.UPDATE, new UpdateArgument(id, city), login, password);
        } catch (NumberFormatException e) {
            System.out.println("id должен быть числом.");
            return null;
        }
    }

    private CommandRequest parseRemoveById(String[] parts) {
        if (parts.length != 2) { System.out.println("Использование: remove_by_id id"); return null; }
        try {
            return new CommandRequest(CommandType.REMOVE_BY_ID, Long.parseLong(parts[1]),
                    login, password);
        } catch (NumberFormatException e) {
            System.out.println("id должен быть числом.");
            return null;
        }
    }
}
