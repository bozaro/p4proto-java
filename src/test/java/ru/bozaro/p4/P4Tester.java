package ru.bozaro.p4;

import org.jetbrains.annotations.NotNull;
import ru.bozaro.p4.proto.Client;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.testng.Assert.assertEquals;

/**
 * @author Marat Radchenko
 */
public final class P4Tester implements AutoCloseable {

    @NotNull
    private static final String HOST = "127.0.0.2";

    @NotNull
    private final Path serverPath;
    @NotNull
    private final Process daemon;
    private final int serverPort;

    public P4Tester(boolean unicode) throws Exception {
        File serverDir = File.createTempFile("p4-server-", "");
        if (!serverDir.delete())
            throw new IOException("Failed to delete " + serverDir);
        if (!serverDir.mkdir())
            throw new IOException("Failed to mkdir " + serverDir);
        serverPath = serverDir.toPath();

        if (unicode) {
            final Process process = Runtime.getRuntime().exec(new String[]{
                    "p4d",
                    "-xi",
                    "-r", serverPath.toString(),
            });
            final int exitCode = process.waitFor();
            assertEquals(0, exitCode);
        }

        serverPort = detectPort();
        daemon = Runtime.getRuntime().exec(new String[]{
                "p4d",
                "-p", String.format("%s:%s", HOST, serverPort),
                "-r", serverPath.toString(),
        });

    }

    private static int detectPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName(HOST))) {
            return socket.getLocalPort();
        }
    }

    public static void deleteDirectory(@NotNull Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @NotNull
    public Client connect(@NotNull String username, @NotNull String password) throws Exception {
        long timeout = System.currentTimeMillis() + 10 * 1000;
        while (true) {
            try {
                Socket socket = new Socket(HOST, serverPort);
                return new Client(socket, username, password, username, (prompt, noecho) -> "", (severity, message) -> {
                }, false);
            } catch (ConnectException e) {
                if (System.currentTimeMillis() > timeout)
                    throw new IOException("Server connect timeout", e);
                else
                    Thread.sleep(100);
            }
        }
    }

    @NotNull
    public Client connect() throws Exception {
        return connectWithPassword("");
    }

    @NotNull
    public Client connectWithPassword(@NotNull String password) throws Exception {
        return connect("JackSparrow", password);
    }

    @Override
    public void close() throws Exception {
        daemon.destroy();
        daemon.waitFor();
        deleteDirectory(serverPath);
    }
}
