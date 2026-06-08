package com.sparrowwallet.sparrow.strata.model;

import java.util.Arrays;

public final class Erc7930Address {
    private final Integer chainId;
    private final AlpenAddress address;

    private Erc7930Address(Integer chainId, AlpenAddress address) {
        this.chainId = chainId;
        this.address = address;
    }

    public Integer getChainId() {
        return chainId;
    }

    public AlpenAddress getAddress() {
        return address;
    }

    public static Erc7930Address parse(String input) {
        if(input == null) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        String normalized = AlpenAddress.normalizeHex(input);
        if(normalized.length() < 12 || normalized.length() % 2 != 0) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        byte[] bytes = AlpenAddress.parseHexBytes(normalized, normalized.length() / 2);
        int offset = 0;

        int version = readUint16(bytes, offset);
        offset += 2;
        if(version != AlpenConstants.ERC7930_VERSION) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        int chainType = readUint16(bytes, offset);
        offset += 2;
        if(chainType != AlpenConstants.ERC7930_EVM_CHAIN_TYPE) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        if(offset >= bytes.length) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        int chainReferenceLength = bytes[offset] & 0xFF;
        offset += 1;

        if(chainReferenceLength == 0 && bytes.length <= offset + 1) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        if(offset + chainReferenceLength > bytes.length) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        Integer chainId = null;
        if(chainReferenceLength > 0) {
            byte[] chainReference = Arrays.copyOfRange(bytes, offset, offset + chainReferenceLength);
            chainId = decodeChainId(chainReference);
            offset += chainReferenceLength;
        }

        if(offset >= bytes.length) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        int addressLength = bytes[offset] & 0xFF;
        offset += 1;
        if(addressLength != AlpenConstants.EVM_ADDRESS_BYTES) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        if(offset + addressLength > bytes.length || offset + addressLength != bytes.length) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }

        byte[] addressBytes = Arrays.copyOfRange(bytes, offset, offset + addressLength);
        return new Erc7930Address(chainId, new AlpenAddress(addressBytes));
    }

    static boolean looksLikeErc7930(String input) {
        if(input == null) {
            return false;
        }
        String normalized = AlpenAddress.normalizeHex(input);
        return normalized.startsWith("00010000") && normalized.length() > AlpenConstants.EVM_ADDRESS_BYTES * 2;
    }

    private static int readUint16(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static int decodeChainId(byte[] chainReference) {
        if(chainReference.length == 0 || chainReference.length > 4) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }
        int value = 0;
        for(byte b : chainReference) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }

    static byte[] encodeChainReference(int chainId) {
        if(chainId == 0) {
            return new byte[0];
        }
        int temp = chainId;
        int byteCount = 0;
        while(temp > 0) {
            byteCount++;
            temp >>>= 8;
        }
        byte[] result = new byte[byteCount];
        temp = chainId;
        for(int i = byteCount - 1; i >= 0; i--) {
            result[i] = (byte)(temp & 0xFF);
            temp >>>= 8;
        }
        return result;
    }
}
