package ru.bozaro.p4.proto;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.p4.crypto.Mangle;

import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
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
public class Client {

    @NotNull
    private final Message.Builder baseMessage;
    @NotNull
    private final InputResolver inputResolver;
    @NotNull
    private final Socket socket;
    @NotNull
    private final HashMap<String, Callback> funcs;
    public boolean verbose = false;
    private boolean protocolSent = false;
    private int protocolServer = -1;
    @Nullable
    private String password;
    @Nullable
    private byte[] secretToken = null;
    @Nullable
    private byte[] secretHash = null;

    public Client(@NotNull Socket socket, @NotNull String username, @Nullable String password, boolean tag, @NotNull InputResolver inputResolver) {
        this.baseMessage = createBaseMessage(username, tag);
        this.socket = socket;
        this.password = password;
        this.funcs = new HashMap<>();
        this.inputResolver = inputResolver;
        funcs.put("flush1", this::flush1);
        funcs.put("protocol", this::clientProtocol);
        funcs.put("client-Crypto", this::clientCrypto);
        funcs.put("client-Prompt", this::clientPrompt);
        funcs.put("client-SetPassword", this::clientSetPassword);
    }

    @NotNull
    private static Message.Builder createBaseMessage(@NotNull String username, boolean tag) {
        final Message.Builder result = new Message.Builder();

        result.param("autoLogin", "");
        if (tag) {
            result.param("tag", "");
        }
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
    protected static byte[] getSocketAddr(@NotNull SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) address;
            return String.format("%s:%d", inet.getHostString(), inet.getPort()).getBytes();
        }
        return null;
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

    public synchronized void p4(@NotNull Callback callback, @NotNull String func, @NotNull String... args) throws IOException {
        if (!protocolSent) {
            send(new Message.Builder()
                    .param("client", "80")
                    .param("sndbuf", "524288")
                    .param("rcvbuf", "524288")
                    .param("func", "protocol"));
            protocolSent = true;

            // HACK! We need to know server version before calling RPCs but server won't send us 'protocol' message
            // before we call any RPC. So, call any random RPC and just ignore its result.
            p4(message -> null, "discover");
        }
        final Message.Builder builder = baseMessage.clone().param(Message.FUNC, "user-" + func);
        for (String arg : args) {
            builder.arg(arg);
        }
        send(builder);
        while (true) {
            Message message = Message.recv(socket.getInputStream());
            if (verbose) {
                show(">>", message);
            }
            String clientFunc = message.getString(Message.FUNC);
            if (clientFunc == null)
                throw new StreamCorruptedException();
            if ("release".equals(clientFunc))
                break;
            final Callback builtin = funcs.get(clientFunc);
            final Message.Builder response;
            if (builtin != null) {
                response = builtin.exec(message);
            } else {
                response = callback.exec(message);
            }
            if (response != null) {
                send(response);
            }
        }
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
    protected Message.Builder flush1(@NotNull Message req) {
        return new Message.Builder()
                .param("fseq", req.getBytes("fseq"))
                .param(Message.FUNC, "flush2");
    }

    @Nullable
    protected Message.Builder clientProtocol(@NotNull Message req) {
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
    protected Message.Builder clientSetPassword(@NotNull Message req) {
        final byte[] token = req.getBytes("digest");
        final byte[] ticket = req.getBytes("data");
        if (token != null && secretHash != null) {
            secretToken = Mangle.XOR(ticket, Mangle.InMD5(token, secretHash));
        }
        return null;
    }

    @Nullable
    protected Message.Builder clientCrypto(@NotNull Message req) {
        final byte[] confirm = req.getBytes("confirm");
        if (secretToken == null) {
            return new Message.Builder()
                    .param(Message.FUNC, confirm)
                    .param("token", "");
        }
        final byte[] token = req.getBytes("token");
        final byte[] daddr = getSocketAddr(socket.getRemoteSocketAddress());
        byte[] result = md5(token, secretToken);
        if (daddr != null && protocolServer >= 29) {
            result = md5(result, daddr);
        }
        return new Message.Builder()
                .param(Message.FUNC, confirm)
                .param("token", result)
                .param("daddr", daddr);
    }

    @Nullable
    protected Message.Builder clientPrompt(@NotNull Message req) throws IOException {
        if (password == null || password.length() <= 0) {
            password = inputResolver.getUserInput(req.getString("data"), req.getBytes("noecho") != null);
        }

        return clientPrompt(req, password != null ? password.getBytes(StandardCharsets.UTF_8) : null);
    }

    @Nullable
    protected Message.Builder clientPrompt(@NotNull Message req, byte[] secret) {
        final byte[] truncate = req.getBytes("truncate");
        final byte[] digest = req.getBytes("digest");
        final byte[] confirm = req.getBytes("confirm");

        byte[] result = secret == null ? new byte[0] : secret;
        if ((truncate != null) && (result.length > 0x10))
            result = Arrays.copyOf(result, 0x10);

        final byte[] daddr = getSocketAddr(socket.getRemoteSocketAddress());
        if (digest != null) {
            result = md5(result);
            secretHash = result;
            if (digest.length > 0) {
                result = md5(result, digest);
            }
            if (daddr != null && protocolServer >= 29) {
                result = md5(result, daddr);
            }
        }
        return req.toBuilder()
                .param(Message.FUNC, confirm)
                .param("data", result)
                .param("digest", digest)
                .param("daddr", daddr);
    }

    @FunctionalInterface
    public interface Callback {

        @Nullable
        Message.Builder exec(@NotNull Message message) throws IOException;
    }

    @FunctionalInterface
    public interface InputResolver {
        @Nullable
        String getUserInput(@Nullable String prompt, boolean noecho) throws IOException;
    }
}
