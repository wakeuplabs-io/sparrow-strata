package com.sparrowwallet.sparrow.strata.deposit;

import com.sparrowwallet.sparrow.strata.protocol.StrataBridgeProtocol;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.ScriptOpCodes;
import com.sparrowwallet.sparrow.strata.protocol.AlpenConstants;
import com.sparrowwallet.sparrow.strata.model.DepositDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Sps50EncoderTest {
    private static final String RECOVERY_PK = "89f96f834e39766f97e245d70b27236681f741ae51c117df19761af7cb2f657e";
    private static final String SUBJECT = "5400000000000000000000000000000000000001";

    @Test
    void encodesDepositRequestTag() {
        byte[] recoveryPk = Utils.hexToBytes(RECOVERY_PK);
        DepositDescriptor descriptor = DepositDescriptor.create(AlpenConstants.ALPEN_EE_ACCT_SERIAL, Utils.hexToBytes(SUBJECT));
        DrtHeaderAux headerAux = DrtHeaderAux.create(recoveryPk, descriptor.encodeToBytes());

        byte[] tag = Sps50Encoder.encodeTag(headerAux.buildAuxData());
        assertEquals(6 + 32 + descriptor.encodeToBytes().length, tag.length);
        assertArrayEquals(StrataBridgeProtocol.MAGIC_BYTES, new byte[] {tag[0], tag[1], tag[2], tag[3]});
        assertEquals(StrataBridgeProtocol.BRIDGE_V1_SUBPROTOCOL_ID, tag[4]);
        assertEquals(StrataBridgeProtocol.DEPOSIT_REQUEST_TX_TYPE, tag[5]);
        assertEquals(ScriptOpCodes.OP_RETURN, Sps50Encoder.encodeOpReturnScript(headerAux.buildAuxData()).getChunks().get(0).getOpcode());
    }

    @Test
    void encodesDepositRequestTagWithLowercaseAlpnMagic() {
        byte[] recoveryPk = Utils.hexToBytes(RECOVERY_PK);
        DepositDescriptor descriptor = DepositDescriptor.create(AlpenConstants.ALPEN_EE_ACCT_SERIAL, Utils.hexToBytes(SUBJECT));
        DrtHeaderAux headerAux = DrtHeaderAux.create(recoveryPk, descriptor.getDestSubject());
        byte[] magicBytes = "alpn".getBytes(StandardCharsets.US_ASCII);

        byte[] tag = Sps50Encoder.encodeTag(headerAux.buildAuxData(), magicBytes);

        assertArrayEquals(magicBytes, new byte[] {tag[0], tag[1], tag[2], tag[3]});
        assertEquals(4 + headerAux.buildAuxData().length, tag.length);
        assertArrayEquals(recoveryPk, Arrays.copyOfRange(tag, 4, 36));
        assertArrayEquals(descriptor.getDestSubject(), Arrays.copyOfRange(tag, 36, tag.length));
    }
}
