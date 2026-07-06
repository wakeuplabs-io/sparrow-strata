package com.sparrowwallet.sparrow.strata.model.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Keccak-256 as used by Ethereum (not NIST SHA3-256).
 */
public final class Keccak256 {
    private static final long[] ROUND_CONSTANTS = {
            0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL, 0x8000000080008000L,
            0x000000000000808BL, 0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L,
            0x000000000000008AL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
            0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L, 0x8000000000008003L,
            0x8000000000008002L, 0x8000000000000080L, 0x000000000000800AL, 0x800000008000000AL,
            0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
    };

    private static final int[] RHO_OFFSETS = {
            1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 2, 14, 27, 41, 56, 8, 25, 43, 62, 18, 39, 61, 20, 44
    };

    private static final int[] PI_LANE_INDEXES = {
            10, 7, 11, 17, 18, 3, 5, 16, 8, 21, 24, 4, 15, 23, 19, 13, 12, 2, 20, 14, 22, 9, 6, 1
    };

    private static final int RATE = 136;

    private Keccak256() {
    }

    public static byte[] hash(byte[] input) {
        long[] state = new long[25];
        int offset = 0;
        while(offset + RATE <= input.length) {
            absorb(state, input, offset);
            offset += RATE;
        }

        byte[] block = new byte[RATE];
        int remaining = input.length - offset;
        if(remaining > 0) {
            System.arraycopy(input, offset, block, 0, remaining);
        }
        block[remaining] = 0x01;
        block[RATE - 1] |= (byte)0x80;
        absorb(state, block, 0);

        byte[] output = new byte[32];
        for(int i = 0; i < 4; i++) {
            long lane = state[i];
            for(int j = 0; j < 8; j++) {
                output[i * 8 + j] = (byte)(lane >>> (8 * j));
            }
        }
        return output;
    }

    public static byte[] hashAsciiLowercase(String value) {
        return hash(value.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private static void absorb(long[] state, byte[] block, int offset) {
        for(int i = 0; i < RATE / 8; i++) {
            long lane = 0;
            for(int j = 0; j < 8; j++) {
                lane |= (long)(block[offset + i * 8 + j] & 0xFF) << (8 * j);
            }
            state[i] ^= lane;
        }
        keccakF1600(state);
    }

    private static void keccakF1600(long[] state) {
        long[] bc = new long[5];
        for(int round = 0; round < 24; round++) {
            for(int i = 0; i < 5; i++) {
                bc[i] = state[i] ^ state[i + 5] ^ state[i + 10] ^ state[i + 15] ^ state[i + 20];
            }

            for(int i = 0; i < 5; i++) {
                long t = bc[(i + 4) % 5] ^ Long.rotateLeft(bc[(i + 1) % 5], 1);
                for(int j = 0; j < 25; j += 5) {
                    state[j + i] ^= t;
                }
            }

            long t = state[1];
            for(int i = 0; i < 24; i++) {
                int j = PI_LANE_INDEXES[i];
                bc[0] = state[j];
                state[j] = Long.rotateLeft(t, RHO_OFFSETS[i]);
                t = bc[0];
            }

            for(int j = 0; j < 25; j += 5) {
                for(int i = 0; i < 5; i++) {
                    bc[i] = state[j + i];
                }
                for(int i = 0; i < 5; i++) {
                    state[j + i] ^= (~bc[(i + 1) % 5]) & bc[(i + 2) % 5];
                }
            }

            state[0] ^= ROUND_CONSTANTS[round];
        }
    }

    public static boolean constantTimeEquals(byte[] left, byte[] right) {
        if(left.length != right.length) {
            return false;
        }
        int result = 0;
        for(int i = 0; i < left.length; i++) {
            result |= left[i] ^ right[i];
        }
        return result == 0;
    }

    public static byte[] copyOf(byte[] bytes) {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
