package ru.bozaro.p4;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.p4.proto.Client;
import ru.bozaro.p4.proto.ErrorSeverity;
import ru.bozaro.p4.proto.Message;

import javax.xml.ws.Holder;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Marat Radchenko
 */
public final class P4 {

    private P4() {
    }

    public static void main(@NotNull String[] args) throws IOException, InterruptedException {
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
                    cmd.client,
                    P4::userInput,
                    P4::outputMessage,
                    cmd.verboseRPC > 0);

            final String func = cmd.command.get(0);
            final String[] funcArgs = cmd.command.subList(1, cmd.command.size()).toArray(new String[0]);

            final Client.Callback callback = new Client.Callback() {
                @Override
                public boolean tag() {
                    return cmd.tag;
                }

                @Override
                public Message.Builder exec(@NotNull Message message, Holder<ErrorSeverity> severityHolder) throws IOException, InterruptedException {
                    return P4.exec(message);
                }
            };
            client.p4(callback, func, funcArgs);
        }
    }

    private static void outputMessage(@NotNull ErrorSeverity severity, @NotNull String message) {
        if (!severity.isOk()) {
            System.out.print(severity.name() + ": ");
        }
        System.out.println(message);
    }

    /**
     * One cannot simply read line from stdin in Java.
     */
    @NotNull
    private static String userInput(@NotNull String prompt, boolean noecho) throws IOException {
        final Console console = System.console();
        if (console != null) {
            if (noecho)
                return new String(console.readPassword("%s", prompt));
            else
                return console.readLine("%s", prompt);
        }

        System.out.print(prompt);
        System.out.flush();

        final StringBuilder result = new StringBuilder();

        try (InputStreamReader is = new InputStreamReader(System.in)) {
            int c;
            while ((c = is.read()) != -1) {
                if (c == '\n')
                    return result.toString();

                result.append((char) c);
            }
        }

        throw new EOFException();
    }

    @Nullable
    private static Message.Builder exec(@NotNull Message message) throws IOException, InterruptedException {
        switch (message.getFunc()) {
            case "client-FstatInfo":
                for (String paramName : message.getParams().keySet()) {
                    if (!Message.FUNC.equals(paramName)) {
                        System.out.println("... " + paramName + " " + message.getString(paramName));
                    }
                }
                return null;

            case "client-EditData":
                final Path editFile = Files.createTempFile("p4-EditData", "");
                try {
                    Files.write(editFile, message.getBytes("data"));

                    final String editorPath = System.getenv().getOrDefault("EDITOR", "/usr/bin/nano");
                    final Process editor = new ProcessBuilder(editorPath, editFile.toString())
                            .inheritIO()
                            .start();
                    final int exitCode = editor.waitFor();

                    if (exitCode != 0) {
                        return message.toBuilder()
                                .param(Message.FUNC, message.getBytes("decline"));
                    }

                    final byte[] data = Files.readAllBytes(editFile);

                    return message.toBuilder()
                            .param("data", data)
                            .param(Message.FUNC, message.getBytes("confirm"));
                } finally {
                    Files.delete(editFile);
                }

            case "client-ErrorPause":
                System.out.println(message.getString("data"));
                System.out.println("Hit return to continue...");

                //noinspection StatementWithEmptyBody
                while (System.in.read() != '\n') ;

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
        @Parameter(names = {"-vrpc"})
        private int verboseRPC = 0;
        @Parameter(names = {"-c"})
        @NotNull
        private String client = System.getenv().getOrDefault("P4CLIENT", InetAddress.getLocalHost().getHostName());
        @Parameter(names = {"-h", "--help"}, description = "Show help", help = true)
        private boolean help = false;

        public CmdArgs() throws UnknownHostException {
        }
    }
}
