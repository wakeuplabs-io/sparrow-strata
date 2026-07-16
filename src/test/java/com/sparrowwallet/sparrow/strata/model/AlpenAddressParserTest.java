package com.sparrowwallet.sparrow.strata.model;

import com.sparrowwallet.sparrow.strata.StrataNetwork;
import com.sparrowwallet.sparrow.strata.protocol.AlpenConstants;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlpenAddressParserTest {
    private static final String CHECKSUMMED = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
    private static final String ADDRESS_HEX = "d8da6bf26964af9d7eed9e03e53415d37aa96045";

    @Test
    void parseEip55AddressOnSignet() {
        AlpenAddressParseResult result = AlpenAddressParser.parse(CHECKSUMMED, Network.SIGNET);
        assertEquals("0x" + ADDRESS_HEX, result.getAddress().toHexString());
        assertArrayEquals(Utils.hexToBytes(ADDRESS_HEX), result.getDepositDescriptor().getDestSubject());
        assertEquals(AlpenConstants.ALPEN_EE_ACCT_SERIAL, result.getDepositDescriptor().getDestAcctSerial());
    }

    @Test
    void parseErc7930WithoutChainReference() {
        String interoperable = "0x000100000014" + ADDRESS_HEX;
        AlpenAddressParseResult result = AlpenAddressParser.parse(interoperable, Network.SIGNET);
        assertEquals("0x" + ADDRESS_HEX, result.getAddress().toHexString());
    }

    @Test
    void parseErc7930WithAlpenTestnetChainReference() {
        byte[] chainReference = Erc7930Address.encodeChainReference(StrataNetwork.ALPEN_TESTNET_CHAIN_ID);
        String interoperable = "0x00010000"
                + String.format("%02x", chainReference.length)
                + Utils.bytesToHex(chainReference).toLowerCase()
                + "14"
                + ADDRESS_HEX;
        AlpenAddressParseResult result = AlpenAddressParser.parse(interoperable, Network.SIGNET);
        assertEquals("0x" + ADDRESS_HEX, result.getAddress().toHexString());
    }

    @Test
    void rejectsBitcoinTestnetWalletForErc7930ChainReference() {
        byte[] chainReference = Erc7930Address.encodeChainReference(StrataNetwork.ALPEN_TESTNET_CHAIN_ID);
        String interoperable = "0x00010000"
                + String.format("%02x", chainReference.length)
                + Utils.bytesToHex(chainReference).toLowerCase()
                + "14"
                + ADDRESS_HEX;
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AlpenAddressParser.parse(interoperable, Network.TESTNET));
        assertEquals(AlpenAddressParser.INVALID_ALPEN_ADDRESS_MESSAGE, exception.getMessage());
    }

    @Test
    void rejectsEthereumMainnetChainReferenceOnSignetWallet() {
        String interoperable = "0x00010000010114" + ADDRESS_HEX;
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AlpenAddressParser.parse(interoperable, Network.SIGNET));
        assertEquals(AlpenAddressParser.INVALID_ALPEN_ADDRESS_MESSAGE, exception.getMessage());
    }

    @Test
    void rejectsBitcoinAddress() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AlpenAddressParser.parse("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", Network.SIGNET));
        assertEquals(AlpenAddressParser.INVALID_ALPEN_ADDRESS_MESSAGE, exception.getMessage());
    }

    @Test
    void rejectsTruncatedErc7930Address() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AlpenAddressParser.parse("0x000100000014" + ADDRESS_HEX.substring(0, 20), Network.SIGNET));
        assertEquals(AlpenAddressParser.INVALID_ALPEN_ADDRESS_MESSAGE, exception.getMessage());
    }

    @Test
    void rejectsInvalidEip55Checksum() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AlpenAddressParser.parse("0xd8dA6BF26964aF9D7eeEd9e03E53415D37aA96046", Network.SIGNET));
        assertEquals(AlpenAddressParser.INVALID_ALPEN_ADDRESS_MESSAGE, exception.getMessage());
    }

    @Test
    void rejectsBlankInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AlpenAddressParser.parse("   ", Network.SIGNET));
        assertEquals("Deposit address is required", exception.getMessage());
    }
}
