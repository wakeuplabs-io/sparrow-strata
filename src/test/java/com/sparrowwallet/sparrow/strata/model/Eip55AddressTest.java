package com.sparrowwallet.sparrow.strata.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Eip55AddressTest {
    private static final String CHECKSUMMED = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
    private static final String LOWERCASE = "0xd8da6bf26964af9d7eed9e03e53415d37aa96045";

    @Test
    void parseValidChecksumAddress() {
        AlpenAddress address = Eip55Address.parse(CHECKSUMMED);
        assertEquals(LOWERCASE, address.toHexString());
    }

    @Test
    void parseLowercaseAddress() {
        AlpenAddress address = Eip55Address.parse(LOWERCASE);
        assertEquals(LOWERCASE, address.toHexString());
    }

    @Test
    void rejectsInvalidChecksum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Eip55Address.parse("0xd8dA6BF26964aF9D7eeEd9e03E53415D37aA96046"));
        assertEquals(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE, exception.getMessage());
    }

    @Test
    void rejectsBitcoinAddress() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Eip55Address.parse("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"));
        assertEquals(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE, exception.getMessage());
    }

    @Test
    void isValidChecksumMatchesEip55Rules() {
        assertTrue(Eip55Address.isValidChecksum(CHECKSUMMED));
        assertFalse(Eip55Address.isValidChecksum("0xd8da6bf26964af9d7eed9e03e53415d37aa96046"));
    }
}
