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
import java.util.Map;
import java.util.TreeMap;

/**
 * P4 client protocol implementation.
 *
 * @author Artem V. Navrotskiy
 */
public class Client {
    @FunctionalInterface
    public interface Callback {

        @Nullable
        Message.Builder exec(@NotNull Message message) throws IOException;

    }

    @FunctionalInterface
    public interface InputResolver {
        @Nullable
        String getUserInput(@Nullable String prompt, boolean noecho);
    }

    @NotNull
    private final Message.Builder baseMessage;
    @NotNull
    private final InputResolver inputResolver;
    @NotNull
    private final Socket socket;
    @NotNull
    private final HashMap<String, Callback> funcs;

    private boolean protocolSent = false;
    public boolean verbose = false;
    @Nullable
    private byte[] secretToken = null;
    @Nullable
    private byte[] secretHash = null;

    public Client(@NotNull Socket socket, @NotNull InputResolver inputResolver, @Nullable Map<String, String> params) {
        this.baseMessage = createBaseMessage(params);
        this.socket = socket;
        this.funcs = new HashMap<>();
        this.inputResolver = inputResolver;
        funcs.put("flush1", this::flush1);
        funcs.put("protocol", this::noop);
        funcs.put("client-Crypto", this::clientCrypto);
        funcs.put("client-Prompt", this::clientPrompt);
        funcs.put("client-SetPassword", this::clientSetPassword);
    }

    private Message.Builder createBaseMessage(@Nullable Map<String, String> params) {
        final Message.Builder result = new Message.Builder();
        for (Map.Entry<String, String> param : getDefaultParams().entrySet()) {
            result.param(param.getKey(), param.getValue());
        }
        if (params != null) {
            for (Map.Entry<String, String> param : params.entrySet()) {
                result.param(param.getKey(), param.getValue());
            }
        }
        return result;
    }

    public static Map<String, String> getDefaultParams() {
        final String user = System.getenv("P4USER");
        final Map<String, String> params = new TreeMap<>();
        params.put("autoLogin", "");
        params.put("tag", "yes");
        params.put("enableStreams", "expandAndmaps" /*, "yes"*/);
        params.put("client", "");
        params.put("cwd", "");
        params.put("os", "UNIX");
        params.put("user", user == null ? System.getProperty("user.name") : user);
        params.put("charset", "1"); // UTF-8
        params.put("clientCase", "1"); // 0 - case insensitive, 1 - case sensitive
        return params;
    }

    public synchronized void p4(@NotNull Callback callback, @NotNull String func, @NotNull String... args) throws IOException {
        if (!protocolSent) {
            final Message.Builder builder = new Message.Builder()
                    .param("client", "80")
                    .param("sndbuf", "524288")
                    .param("rcvbuf", "524288")
                    .param("func", "protocol");
            send(builder);
            protocolSent = true;
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
            final Callback buildin = funcs.get(clientFunc);
            final Message.Builder response;
            if (buildin != null) {
                response = buildin.exec(message);
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
        if (daddr != null) {
            result = md5(result, daddr);
        }
        return new Message.Builder()
                .param(Message.FUNC, confirm)
                .param("token", result)
                .param("daddr", daddr);
    }

    @Nullable
    protected Message.Builder clientPrompt(@NotNull Message req) {
        final String userInput = inputResolver.getUserInput(req.getString("data"), req.getBytes("noecho") != null);
        return clientPrompt(req, userInput != null ? userInput.getBytes(StandardCharsets.UTF_8) : null);
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
            if (daddr != null) {
                result = md5(result, daddr);
            }
        }
        return req.toBuilder()
                .param(Message.FUNC, confirm)
                .param("data", result)
                .param("digest", digest)
                .param("daddr", daddr);
    }

    @Nullable
    protected Message.Builder noop(@NotNull Message req) {
        return null;
    }

    @NotNull
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
}
