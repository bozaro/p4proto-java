package ru.bozaro.p4;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.p4.proto.Client;
import ru.bozaro.p4.proto.ErrorSeverity;
import ru.bozaro.p4.proto.Message;

import javax.xml.ws.Holder;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Marat Radchenko
 */
public final class P4 {

    private P4() {
    }

    public static void main(@NotNull String[] args) throws IOException {
        final CmdArgs cmd = new CmdArgs();
        final JCommander jc = new JCommander(cmd, args);

        if (cmd.help || cmd.command.size() < 1) {
            jc.usage();
            return;
        }

        final String host;
        final int port;
        final int sepIndex = cmd.port.lastIndexOf(':');
        if (sepIndex >= 0) {
            host = cmd.port.substring(0, sepIndex);
            port = Integer.parseInt(cmd.port.substring(sepIndex + 1));
        } else {
            host = cmd.port;
            port = 1666;
        }

        try (Socket socket = new Socket(host, port)) {
            final Client client = new Client(socket,
                    cmd.user,
                    cmd.password,
                    cmd.tag,
                    P4::userInput,
                    P4::outputMessage);

            final String func = cmd.command.get(0);
            final String[] funcArgs = cmd.command.subList(1, cmd.command.size()).toArray(new String[0]);

            client.p4(P4::exec, func, funcArgs);
        }
    }

    private static void outputMessage(@NotNull ErrorSeverity severity, @NotNull String message) {
        if (!severity.isOk()) {
            System.out.print(severity.name() + ": ");
        }
        System.out.println(message);
    }

    @NotNull
    private static String userInput(@NotNull String prompt, boolean noecho) throws IOException {
        System.out.print(prompt);
        System.out.flush();

        final String result;
        Console console = System.console();
        if (!noecho || console == null) {
            result = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
        } else {
            result = new String(console.readPassword());
        }

        return result;
    }

    @Nullable
    private static Message.Builder exec(@NotNull Message message, @NotNull Holder<ErrorSeverity> severityHolder) throws IOException {
        switch (message.getFunc()) {
            case "client-FstatInfo":
                for (String paramName : message.getParams().keySet()) {
                    if (!Message.FUNC.equals(paramName)) {
                        System.out.println("... " + paramName + " " + message.getString(paramName));
                    }
                }
                return null;

            default:
                throw new UnsupportedOperationException(message.getFunc());
        }
    }

    public static class CmdArgs {

        @Parameter(description = "Command")
        private final List<String> command = new ArrayList<>(Collections.singletonList("help"));
        @NotNull
        @Parameter(names = {"-p"}, description = "set server port (default $P4PORT)")
        private String port = System.getenv().getOrDefault("P4PORT", "perforce:1666");
        @NotNull
        @Parameter(names = {"-u"}, description = "set user's username (default $P4USER)")
        private String user = System.getenv().getOrDefault("P4USER", System.getProperty("user.name"));
        @NotNull
        @Parameter(names = {"-P"}, description = "set user's password (default $P4PASSWD)")
        private String password = System.getenv().getOrDefault("P4PASSWD", "");
        @Parameter(names = {"-Ztag"})
        private boolean tag = false;
        @Parameter(names = {"-h", "--help"}, description = "Show help", help = true)
        private boolean help = false;
    }
}
