package ru.bozaro.p4.proto;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * P4 message.
 *
 * @author Artem V. Navrotskiy
 */
public class Message {
    @NotNull
    public static final String FUNC = "func";
    private static final byte[] EMPTY_BYTES = {};

    @NotNull
    private final Map<String, byte[]> params;
    @NotNull
    private final List<String> args;

    public Message(@NotNull Map<String, byte[]> params, @NotNull List<String> args) {
        this.params = params;
        this.args = args;
    }

    @NotNull
    public Message show(@NotNull PrintStream out, @NotNull String prefix) {
        out.printf("===== MESSAGE BEGIN =====\n");
        out.printf("%s Function: %s\n", prefix, getString(FUNC));
        for (Map.Entry<String, byte[]> entry : params.entrySet()) {
            if (entry.getKey().equals(FUNC)) continue;
            out.printf("%s %s = %s\n", prefix, entry.getKey(), toString(entry.getValue()));
        }
        for (String arg : args) {
            out.printf("%s - %s\n", prefix, arg);
        }
        out.printf("===== MESSAGE END =====\n");
        return this;
    }

    @NotNull
    public String getFunc() {
        return toString(params.getOrDefault("func", EMPTY_BYTES));
    }

    @Nullable
    public String getString(@NotNull String key) {
        byte[] value = params.get(key);
        return (value != null) ? toString(value) : null;
    }

    public byte[] getBytes(@NotNull String key) {
        return params.get(key);
    }

    @NotNull
    private String toString(@NotNull byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    @NotNull
    public Map<String, byte[]> getParams() {
        return Collections.unmodifiableMap(params);
    }

    @NotNull
    public List<String> getArgs() {
        return Collections.unmodifiableList(args);
    }

    public Builder toBuilder() {
        final Builder builder = new Builder();
        builder.params.putAll(params);
        builder.args.addAll(args);
        return builder;
    }

    public static class Builder {
        @NotNull
        private final Map<String, byte[]> params = new TreeMap<>();
        @NotNull
        private final List<String> args = new ArrayList<>();

        public Builder() {
        }

        @NotNull
        public Builder param(@NotNull String name, @Nullable String value) {
            if (value != null) {
                if (name.isEmpty()) {
                    args.add(value);
                } else {
                    params.put(name, value.getBytes(StandardCharsets.UTF_8));
                }
            }
            return this;
        }

        @NotNull
        public Builder param(@NotNull String name, @Nullable byte[] value) {
            if (value != null) {
                if (name.isEmpty()) {
                    args.add(new String(value, StandardCharsets.UTF_8));
                } else {
                    params.put(name, value);
                }
            }
            return this;
        }

        @NotNull
        public Builder arg(@Nullable String value) {
            if (value != null) {
                args.add(value);
            }
            return this;
        }

        @Override
        @NotNull
        public Builder clone() {
            final Builder cloned = new Builder();
            cloned.params.putAll(params);
            cloned.args.addAll(args);
            return cloned;
        }

        @NotNull
        public Message build() {
            return new Message(params, args);
        }
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream writer = new ByteArrayOutputStream(1024);
        writer.write(0);
        write32(writer, 0);
        for (Map.Entry<String, byte[]> entry : params.entrySet()) {
            byte[] key = entry.getKey().getBytes(StandardCharsets.UTF_8);
            writer.write(key);
            writer.write(0);

            byte[] value = entry.getValue();
            write32(writer, value.length);
            writer.write(value);
            writer.write(0);
        }

        for (String arg : args) {
            writer.write(0);
            byte[] value = arg.getBytes(StandardCharsets.UTF_8);
            write32(writer, value.length);
            writer.write(value);
            writer.write(0);
        }

        byte[] buffer = writer.toByteArray();
        int length = buffer.length - 5;
        byte checksum = 0;
        for (int i = 0; i < 4; i++) {
            buffer[i + 1] = (byte) (length & 0xFF);
            checksum ^= (byte) (length & 0xFF);
            length >>= 8;
        }
        buffer[0] = checksum;
        return buffer;
    }

    private void write32(@NotNull OutputStream stream, int value) throws IOException {
        int v = value;
        for (int i = 0; i < 4; i++) {
            stream.write(v & 0xFF);
            v >>= 8;
        }
    }

    public void send(@NotNull OutputStream stream) throws IOException {
        stream.write(serialize());
    }

    @NotNull
    public static Message recv(InputStream stream) throws IOException {
        final Builder builder = new Builder();
        int checksum = stream.read();
        int length = read32(stream);
        for (int i = 0; i < 4; ++i) {
            checksum ^= 0xFF & (length >> (i << 3));
        }
        if (checksum != 0) throw new IOException("Checksum mismatch");

        byte[] buf = new byte[length];
        for (int position = 0; position < buf.length; ) {
            int size = stream.read(buf, position, length - position);
            if (size < 0) throw new IOException("Unexpected end of stream");
            position += size;
        }

        for (int position = 0; position < buf.length; ) {
            int end = indexOf(buf, position, (byte) 0);
            if (end < 0)
                throw new IOException("Can't parse parameter name");
            String name = new String(buf, position, end - position, StandardCharsets.UTF_8);
            position = end + 1;

            int len = read32(buf, position);
            position += 4;

            if (len + position + 1 > buf.length)
                throw new IOException("Unexpected end of stream");
            byte[] value = Arrays.copyOfRange(buf, position, position + len);
            position += len;

            if (buf[position] != 0)
                throw new IOException("Can't parse parameter value");
            position++;
            builder.param(name, value);
        }
        return builder.build();
    }

    private static int indexOf(byte[] buf, int startPosition, byte b) {
        for (int i = startPosition; i < buf.length; ++i) {
            if (buf[i] == b) return i;
        }
        return -1;
    }

    private static int read32(InputStream stream) throws IOException {
        int result = 0;
        for (int i = 0; i < 4; ++i) {
            int b = stream.read();
            if (b < 0) throw new IOException("Unexpected end of stream");
            result |= (0xFF & b) << (i << 3);
        }
        return result;
    }

    private static int read32(byte[] buf, int offset) throws IOException {
        if (offset < 0 || offset > buf.length + 4) throw new IOException("Unexpected end of stream");
        int result = 0;
        for (int i = 0; i < 4; ++i) {
            result |= (0xFF & (int) (buf[i + offset])) << (i << 3);
        }
        return result;
    }
}
