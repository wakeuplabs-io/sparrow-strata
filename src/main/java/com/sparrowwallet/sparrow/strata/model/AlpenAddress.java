package com.sparrowwallet.sparrow.strata.model;

import com.sparrowwallet.drongo.Utils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public final class AlpenAddress {
    private final byte[] bytes;

    public AlpenAddress(byte[] bytes) {
        if(bytes == null || bytes.length != AlpenConstants.EVM_ADDRESS_BYTES) {
            throw new IllegalArgumentException("Alpen address must be exactly 20 bytes");
        }
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public String toHexString() {
        return "0x" + Utils.bytesToHex(bytes);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) {
            return true;
        }
        if(!(obj instanceof AlpenAddress other)) {
            return false;
        }
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return toHexString();
    }

    public static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    public static byte[] parseHexBytes(String hex, int expectedLength) {
        String normalized = normalizeHex(hex);
        if(normalized.length() != expectedLength * 2) {
            throw new IllegalArgumentException("Invalid hex length");
        }
        for(int i = 0; i < normalized.length(); i++) {
            if(!isHexDigit(normalized.charAt(i))) {
                throw new IllegalArgumentException("Invalid hex character");
            }
        }
        return Utils.hexToBytes(normalized);
    }

    public static String normalizeHex(String value) {
        String trimmed = value.trim();
        if(trimmed.regionMatches(true, 0, "0x", 0, 2)) {
            trimmed = trimmed.substring(2);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
