package ru.bozaro.p4;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import ru.bozaro.p4.proto.Client;
import ru.bozaro.p4.proto.Message;

import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple P4 usage example
 *
 * @author Artem V. Navrotskiy
 */
public class Example {
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        String user = "bozaro";
        String password = "secret";

        final Map<String, String> params = new HashMap<>();
        params.put("user", user);

        Client.InputResolver resolver = (prompt, noecho) -> password;
        Socket socket = new Socket("100.112.6.208", 1666);
        Client client = new Client(socket, resolver, params);
        client.p4(Example::show, "describe", "-s", "-m", "1", "4");
        client.p4(Example::show, "files", "@=4");
        client.p4(Example::show, "print", "//streamsDepot/mainline/Sample/binary/nodeps.zip#1");
    }

    @Nullable
    private static Message.Builder show(@NotNull Message req) {
        req.show(System.out, ">>");
        return null;
    }
}
