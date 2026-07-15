package com.sparrowwallet.sparrow.strata.model;

import com.sparrowwallet.sparrow.strata.protocol.AlpenConstants;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.strata.model.crypto.Keccak256;

import java.util.Locale;

public final class Eip55Address {
    private static final int HEX_LENGTH = AlpenConstants.EVM_ADDRESS_BYTES * 2;

    private Eip55Address() {
    }

    public static AlpenAddress parse(String input) {
        if(input == null) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        String trimmed = input.trim();
        if(trimmed.isEmpty() || looksLikeBitcoinAddress(trimmed)) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        boolean hasPrefix = trimmed.regionMatches(true, 0, "0x", 0, 2);
        String hexBody = hasPrefix ? trimmed.substring(2) : trimmed;
        if(hexBody.length() != HEX_LENGTH) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        for(int i = 0; i < hexBody.length(); i++) {
            if(!AlpenAddress.isHexDigit(hexBody.charAt(i))) {
                throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
            }
        }

        if(hasMixedCase(hexBody) && !isValidChecksum(trimmed)) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        return new AlpenAddress(AlpenAddress.parseHexBytes(hexBody, AlpenConstants.EVM_ADDRESS_BYTES));
    }

    private static boolean looksLikeBitcoinAddress(String value) {
        if(value.startsWith("bc1") || value.startsWith("tb1") || value.startsWith("bcrt1")) {
            return true;
        }
        if(value.length() >= 26 && value.length() <= 35 && isBase58(value)) {
            return true;
        }
        return false;
    }

    private static boolean isBase58(String value) {
        for(int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if("123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasMixedCase(String hexBody) {
        boolean hasLower = false;
        boolean hasUpper = false;
        for(int i = 0; i < hexBody.length(); i++) {
            char c = hexBody.charAt(i);
            if(Character.isLowerCase(c)) {
                hasLower = true;
            } else if(Character.isUpperCase(c)) {
                hasUpper = true;
            }
            if(hasLower && hasUpper) {
                return true;
            }
        }
        return false;
    }

    static boolean isValidChecksum(String address) {
        String prefixed = address.startsWith("0x") || address.startsWith("0X") ? address : "0x" + address;
        String body = prefixed.substring(2);
        String lower = body.toLowerCase(Locale.ROOT);
        byte[] hash = Keccak256.hashAsciiLowercase(lower);
        String hashHex = Utils.bytesToHex(hash);
        StringBuilder checksummed = new StringBuilder("0x");
        for(int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if(c >= 'a' && c <= 'f') {
                int hashNibble = Character.digit(hashHex.charAt(i), 16);
                if(hashNibble >= 8) {
                    c = Character.toUpperCase(c);
                }
            }
            checksummed.append(c);
        }
        return prefixed.equals(checksummed.toString());
    }
}
