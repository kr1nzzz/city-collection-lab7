import java.io.Serializable;

public class CommandRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final CommandType type;
    private final Serializable argument;
    private final String login;
    private final String password;

    public CommandRequest(CommandType type, Serializable argument, String login, String password) {
        this.type = type;
        this.argument = argument;
        this.login = login;
        this.password = password;
    }

    public CommandRequest(CommandType type, String login, String password) {
        this(type, null, login, password);
    }

    public CommandType getType() { return type; }
    public Serializable getArgument() { return argument; }
    public String getLogin() { return login; }
    public String getPassword() { return password; }
}
