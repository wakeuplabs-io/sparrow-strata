package com.sparrowwallet.sparrow.strata.model;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.sparrow.strata.model.crypto.Keccak256;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Keccak256Test {
    @Test
    void emptyInputMatchesKnownVector() {
        assertArrayEquals(
                Utils.hexToBytes("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"),
                Keccak256.hash(new byte[0]));
    }

    @Test
    void abcInputMatchesKnownVector() {
        assertArrayEquals(
                Utils.hexToBytes("4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45"),
                Keccak256.hashAsciiLowercase("abc"));
    }

    @Test
    void addressBodyMatchesKnownVector() {
        byte[] hash = Keccak256.hashAsciiLowercase("d8da6bf26964af9d7eed9e03e53415d37aa96045");
        assertEquals("535bdae9bb214b3cc583b53384464999f2f7f48625f160728c63e73e766ff71e", Utils.bytesToHex(hash));
    }
}
