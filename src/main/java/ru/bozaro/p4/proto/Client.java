package ru.bozaro.p4.proto;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.p4.crypto.Mangle;

import javax.xml.ws.Holder;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

/**
 * P4 client protocol implementation.
 *
 * @author Artem V. Navrotskiy
 */
public final class Client implements AutoCloseable {

    @NotNull
    private final Message.Builder baseMessage;
    @NotNull
    private final InputResolver inputResolver;
    @NotNull
    private final MessageOutput messageOutput;
    @NotNull
    private final Socket socket;
    @NotNull
    private final HashMap<String, Callback> funcs;
    @NotNull
    private final String username;
    private final boolean verbose;
    private boolean protocolSent = false;
    private int protocolServer = -1;
    @NotNull
    private String password = "";
    @Nullable
    private byte[] secretToken = null;
    @Nullable
    private byte[] secretHash = null;

    public Client(@NotNull Socket socket,
                  @NotNull String username,
                  @NotNull String password,
                  @NotNull InputResolver inputResolver,
                  @NotNull MessageOutput messageOutput,
                  boolean verbose) {
        this.username = username;
        this.messageOutput = messageOutput;
        this.verbose = verbose;
        this.baseMessage = createBaseMessage();
        this.socket = socket;
        this.password = password;
        this.funcs = new HashMap<>();
        this.inputResolver = inputResolver;
        funcs.put("flush1", this::flush1);
        funcs.put("protocol", this::clientProtocol);
        funcs.put("client-Crypto", this::clientCrypto);
        funcs.put("client-Message", this::clientMessage);
        funcs.put("client-Prompt", this::clientPrompt);
        funcs.put("client-SetPassword", this::clientSetPassword);
    }

    @NotNull
    private static byte[] md5(@NotNull byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            for (byte[] part : parts) {
                digest.update(part);
            }
            return Mangle.OtoX(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }

    @NotNull
    public String getUsername() {
        return username;
    }

    @NotNull
    private Message.Builder createBaseMessage() {
        final Message.Builder result = new Message.Builder();

        result.param("enableStreams", "expandAndmaps" /*, "yes"*/);
        result.param("client", "");
        result.param("cwd", "");
        result.param("os", "UNIX");
        result.param("user", username);
        result.param("charset", "1"); // UTF-8
        result.param("clientCase", "1"); // 0 - case insensitive, 1 - case sensitive

        return result;
    }

    @Nullable
    private Message.Builder clientMessage(@NotNull Message message, @NotNull Holder<ErrorSeverity> severityHolder) {
        String fmt;
        for (int i = 0; (fmt = message.getString("fmt" + i)) != null; ++i) {
            final String codeString = message.getString("code" + i);
            ErrorSeverity severity = ErrorSeverity.None;

            if (codeString != null) {
                int code = Integer.parseInt(codeString);
                severity = ErrorSeverity.values()[(code >> 28) & 0x3ff];

                if (severity.compareTo(severityHolder.value) > 0)
                    severityHolder.value = severity;
            }

            final String msg = StringInterpolator.interpolate(fmt, s -> message.getStringOrDefault(s, ""));
            messageOutput.output(severity, msg);
        }
        return null;
    }

    public synchronized boolean p4(@NotNull Callback callback, @NotNull String func, @NotNull String... args) throws IOException {
        if (!protocolSent) {
            send(new Message.Builder()
                    .param("client", "80")
                    .param("sndbuf", "524288")
                    .param("rcvbuf", "524288")
                    .param(Message.FUNC, "protocol"));
            protocolSent = true;

            final boolean[] needLogin = {false};
            final Callback autologinCallback = (message, severityHolder) -> {
                needLogin[0] = "enabled".equals(message.getString("password"));
                return null;
            };
            if (p4(autologinCallback, "info") && needLogin[0]) {
                p4((message, severityHolder) -> null, "login");
            }
        }

        final Message.Builder builder = baseMessage.clone().param(Message.FUNC, "user-" + func);
        for (String arg : args) {
            builder.arg(arg);
        }

        if (callback.tag()) {
            builder.param("tag", "");
        }
        send(builder);

        final Holder<ErrorSeverity> severityHolder = new Holder<>(ErrorSeverity.None);

        while (true) {
            Message message = Message.recv(socket.getInputStream());
            if (verbose) {
                show(">>", message);
            }
            final String clientFunc = message.getFunc();
            if (clientFunc.length() <= 0)
                throw new StreamCorruptedException();

            if ("release".equals(clientFunc))
                break;

            final Callback builtin = funcs.get(clientFunc);
            final Message.Builder response;
            if (builtin != null) {
                response = builtin.exec(message, severityHolder);
            } else {
                response = callback.exec(message, severityHolder);
            }
            if (response != null) {
                send(response);
            }
        }

        return severityHolder.value.isOk();
    }

    private void send(@NotNull Message.Builder builder) throws IOException {
        Message msg = builder.build();
        if (verbose) {
            show("<<", msg);
        }
        msg.send(socket.getOutputStream());
    }

    private void show(@NotNull String prefix, @NotNull Message msg) throws IOException {
        msg.show(System.out, prefix);
    }

    @Nullable
    private Message.Builder flush1(@NotNull Message req, @NotNull Holder<ErrorSeverity> severityHolder) {
        return new Message.Builder()
                .param("fseq", req.getBytes("fseq"))
                .param(Message.FUNC, "flush2");
    }

    @Nullable
    private Message.Builder clientProtocol(@NotNull Message req, @NotNull Holder<ErrorSeverity> severityHolder) {
        String protocolVersionString = req.getString("server2");

        if (protocolVersionString == null) {
            protocolVersionString = req.getString("server");
        }

        if (protocolVersionString != null) {
            protocolServer = Integer.parseInt(protocolVersionString);
        }

        if (req.getBytes("unicode") != null) {
            baseMessage.param("unicode", "1");
        }

        return null;
    }

    @Nullable
    private Message.Builder clientSetPassword(@NotNull Message req, @NotNull Holder<ErrorSeverity> severityHolder) {
        final byte[] token = req.getBytes("digest");
        final byte[] ticket = req.getBytes("data");
        if (token != null && secretHash != null) {
            secretToken = Mangle.XOR(ticket, Mangle.InMD5(token, secretHash));
        }
        return null;
    }

    @Nullable
    private Message.Builder clientCrypto(@NotNull Message req, @NotNull Holder<ErrorSeverity> severityHolder) {
        final byte[] confirm = req.getBytes("confirm");
        if (secretToken == null) {
            return new Message.Builder()
                    .param(Message.FUNC, confirm)
                    .param("token", "");
        }
        final byte[] token = req.getBytes("token");
        byte[] result = md5(token, secretToken);

        return new Message.Builder()
                .param(Message.FUNC, confirm)
                .param("token", result);
    }

    @Nullable
    private Message.Builder clientPrompt(@NotNull Message req, @NotNull Holder<ErrorSeverity> severityHolder) throws IOException {
        final boolean truncate = req.getBytes("truncate") != null;
        final byte[] digest = req.getBytes("digest");
        final byte[] confirm = req.getBytes("confirm");
        final boolean noprompt = req.getBytes("noprompt") != null;
        final boolean noecho = req.getBytes("noecho") != null;
        final String data = req.getString("data");

        if (password.length() <= 0 && !noprompt) {
            password = inputResolver.getUserInput(data, noecho);
        }

        byte[] result = password.getBytes(StandardCharsets.UTF_8);

        if (truncate && (result.length > 0x10))
            result = Arrays.copyOf(result, 0x10);

        if (digest != null) {
            result = md5(result);
            secretHash = result;
            if (digest.length > 0) {
                result = md5(result, digest);
            }
        }
        return req.toBuilder()
                .param(Message.FUNC, confirm)
                .param("data", result);
    }

    @Override
    public void close() throws Exception {
        socket.close();
    }

    @FunctionalInterface
    public interface MessageOutput {

        void output(@NotNull ErrorSeverity severity, @NotNull String message);
    }

    @FunctionalInterface
    public interface Callback {

        default boolean tag() {
            return true;
        }

        Message.Builder exec(@NotNull Message message, Holder<ErrorSeverity> severityHolder) throws IOException;
    }

    @FunctionalInterface
    public interface InputResolver {
        @NotNull
        String getUserInput(@Nullable String prompt, boolean noecho) throws IOException;
    }
}
