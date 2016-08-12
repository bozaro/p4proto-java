package ru.bozaro.p4.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * This code implements the lucifer encryption/decryption
 * code but has been modified to work just on small strings.
 * <p>
 * Originally written by Arthur Sorkin in 1984 in FORTRAN
 * <p>
 * Rewritten in 1991 by Jonathan M. Smith
 * <p>
 * This code is in the public domain.  Patents have expired.
 * <p>
 * To encrypt a string (must be 16 bytes or less) instantiate
 * a Mangle object, call In() passing the string, key and an
 * output buffer to store the result.  To decrypt the string
 * instantiate a Mangle object, call Out() passing in the 32
 * character (hex) string and the originally key, string will
 * be returned in the output buffer in the same way as In().
 * <p>
 * Notes:
 * <p>
 * data must be 16 bytes or less (except see below).
 * key will be truncated to a maximum of 16 characters.
 * <p>
 * As part of the security improvements the digest stored in the
 * server is no longer usable on the client, in order to mangle
 * the 32 character digest (data > 16 characters) alternative
 * interfaces InMD5() and OutMD5() handle the "raw" digest.
 */
public class Mangle {
    private static final int BPB = 8;
    // diffusion pattern
    private static final int[] o = new int[]{7, 6, 2, 1, 5, 0, 3, 4};
    // inverse of fixed permutation
    private static final int[] pr = new int[]{2, 5, 4, 0, 3, 1, 7, 6};
    // S-box permutations
    private static final int[] s0 = new int[]{12, 15, 7, 10, 14, 13, 11, 0, 2, 6, 3, 1, 9, 4, 5, 8};
    // S-box permutations
    private static final int[] s1 = new int[]{7, 2, 14, 9, 3, 11, 0, 4, 12, 13, 1, 10, 6, 15, 8, 5};
    // S-box permutations
    private static final int[] s2 = new int[]{10, 1, 13, 12, 4, 0, 11, 3};

    public static byte[] In(byte[] in, byte[] key) {
        try {
            ByteArrayOutputStream mangledValue = new ByteArrayOutputStream();
            int inputLen = in.length;
            int offset = 0;

            while (offset < inputLen) {
                int chunkSize = inputLen - offset;
                if (chunkSize > 16) chunkSize = 16;
                byte[] data = Arrays.copyOfRange(in, offset, offset + chunkSize);
                byte[] mangledChunk = DoIt(data, key, false, false);
                mangledValue.write(mangledChunk);
                offset += chunkSize;
            }

            return mangledValue.toByteArray();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public static byte[] Out(byte[] out, byte[] key) {
        try {
            ByteArrayOutputStream extractedValue = new ByteArrayOutputStream();
            int offset = 0;
            int inputLen = out.length;
            while (offset < inputLen) {
                int chunkSize = inputLen - offset;
                if (chunkSize > 32) chunkSize = 32;
                byte[] data = Arrays.copyOfRange(out, offset, offset + chunkSize);
                byte[] extractedChunk = DoIt(data, key, true, false);
                extractedValue.write(extractedChunk);
                offset += chunkSize;
            }
            return extractedValue.toByteArray();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public static byte[] InMD5(byte[] data, byte[] key) {
        return DoIt(data, key, false, true);
    }

    public static byte[] OutMD5(byte[] data, byte[] key) {
        return DoIt(data, key, true, true);
    }

    public static byte[] XOR(byte[] data, byte[] key) {
        if (data.length != 32 || key.length != 32)
            throw new IllegalArgumentException();

        byte[] src = XtoO(data);
        byte[] buf = XtoO(key);

        for (int i = 0; i < 16; ++i)
            buf[i] ^= src[i];

        return OtoX(buf);
    }

    private static void Mix(byte[] buf, int offset) {
        for (int i = 0; i < s2.length; ++i) {
            s2[i] = buf[offset + i];
        }
    }

    private static byte[] DoIt(final byte[] data, final byte[] key, boolean decipher, boolean digest) {
        int[] m = new int[128];
        int[] k = new int[128];
        int counter;
        int c, i, j;

        if ((decipher && (data.length != 32) && (data.length != 0)) ||
                (!decipher && (data.length > 16) && !digest) ||
                (!decipher && (data.length != 32) && digest)) {
            throw new IllegalArgumentException();
        }

        // truncate key to 16 character max
        byte[] enc = new byte[0x10];
        byte[] buf = Arrays.copyOf(key, 0x10);
        byte[] src = (decipher || digest) ? XtoO(data) : Arrays.copyOf(data, 0x10);

        int p = 0;
        int q = 0;

        for (counter = 0; counter < 16; counter += 1) {
            c = buf[counter] & 0xFF;
            for (i = 0; i < BPB; i += 1) {
                k[(BPB * counter) + i] = c & 0x1;
                c = c >> 1;
            }
        }

        counter = 0;

        // Mix it up a bit
        if (decipher) {
            s1[4] = s2[0];
            s1[5] = s2[1];
            s1[6] = s2[2];
            s1[7] = s2[3];
        }

        // Lose check for NULL, since its a valid character, assume its a
        // 16 byte cipher block (thats all it ever should be, lose the
        // while( (c=*p++) != '\0' )
        for (j = 0; j < 16; ++j) {
            c = src[p++];
            if (counter == 16) {
                Getdval(decipher, m, k);

                for (counter = 0; counter < 16; counter += 1) {
                    int output = 0;
                    for (i = BPB - 1; i >= 0; i -= 1)
                        output = (output << 1) + m[(BPB * counter) + i];
                    enc[q++] = (byte) output;
                }
                counter = 0;
            }
            for (i = 0; i < BPB; i += 1) {
                m[(BPB * counter) + i] = c & 0x1;
                c = c >> 1;
            }
            counter += 1;
        }

        for (; counter < 16; counter += 1)
            for (i = 0; i < BPB; i += 1)
                m[(BPB * counter) + i] = 0;

        Getdval(decipher, m, k);

        for (counter = 0; counter < 16; counter += 1) {
            int output = 0;
            for (i = BPB - 1; i >= 0; i -= 1)
                output = (output << 1) + m[(BPB * counter) + i];
            enc[q++] = (byte) output;
        }

        return (decipher && !digest) ? enc : OtoX(enc);
    }

    public static byte[] XtoO(byte[] data) {
        final byte[] result = new byte[(data.length + 1) / 2];
        for (int i = 0; i < data.length; ++i) {
            int h = data[i];
            if (h >= '0' && h <= '9') {
                h -= '0';
            } else if (h >= 'A' && h <= 'F') {
                h -= 'A' - 10;
            } else if (h >= 'a' && h <= 'f') {
                h -= 'a' - 10;
            } else {
                throw new IllegalArgumentException("Invalid hex character: " + (char) h);
            }
            result[i / 2] |= (byte) (h << (((i & 1) ^ 1) * 4));
        }
        return result;
    }

    public static byte[] OtoX(byte[] data) {
        final byte[] result = new byte[data.length * 2];
        for (int i = 0; i < data.length; ++i) {
            int v = data[i];
            for (int j = 0; j < 2; ++j) {
                int h = v & 0x0F;
                v >>= 4;
                result[i * 2 + 1 - j] = (byte) ((h >= 10 ? 'A' - 10 : '0') + h);
            }
        }
        return result;
    }

    private static void Getdval(boolean decipher, int m[], int k[]) {
        int tcbindex; // transfer control byte indices
        int round, hi, lo, h_0, h_1;
        int index, v;
        int[] tr = new int[BPB];

        h_0 = 0;
        h_1 = 1;

        int tcbcontrol = decipher ? 8 : 0;

        // mix it up a bit
        if (decipher) {
            s1[8] = s2[4];
            s1[9] = s2[5];
            s1[10] = s2[6];
            s1[11] = s2[7];
        }

        for (round = 0; round < 16; round += 1) {
            if (decipher)
                tcbcontrol = (tcbcontrol + 1) & 0xF;
            tcbindex = tcbcontrol;
            for (int b = 0; b < 8; b += 1) {
                lo = (m[(h_1 * 64) + (BPB * b) + 7]) * 8
                        + (m[(h_1 * 64) + (BPB * b) + 6]) * 4
                        + (m[(h_1 * 64) + (BPB * b) + 5]) * 2
                        + (m[(h_1 * 64) + (BPB * b) + 4]);
                hi = (m[(h_1 * 64) + (BPB * b) + 3]) * 8
                        + (m[(h_1 * 64) + (BPB * b) + 2]) * 4
                        + (m[(h_1 * 64) + (BPB * b) + 1]) * 2
                        + (m[(h_1 * 64) + (BPB * b) + 0]);

                v = (s0[lo] + 16 * s1[hi]) * (1 - k[(BPB * tcbindex) + b])
                        + (s0[hi] + 16 * s1[lo]) * k[(BPB * tcbindex) + b];

                for (int temp1 = 0; temp1 < BPB; temp1 += 1) {
                    tr[temp1] = v & 0x1;
                    v = v >> 1;
                }

                for (int bit = 0; bit < BPB; bit += 1) {
                    index = (o[bit] + b) & 0x7;
                    int temp1 = m[(h_0 * 64) + (BPB * index) + bit]
                            + k[(BPB * tcbcontrol) + pr[bit]]
                            + tr[pr[bit]];
                    m[(h_0 * 64) + (BPB * index) + bit] = temp1 & 0x1;
                }

                if (b < 7 || decipher)
                    tcbcontrol = (tcbcontrol + 1) & 0xF;
            }

            int temp1 = h_0;
            h_0 = h_1;
            h_1 = temp1;
        }

        // final swap
        for (int b = 0; b < 8; b += 1) {
            for (int bit = 0; bit < BPB; bit += 1) {
                int temp1 = m[(BPB * b) + bit];
                m[(BPB * b) + bit] = m[64 + (BPB * b) + bit];
                m[64 + (BPB * b) + bit] = temp1;
            }
        }
    }
}
