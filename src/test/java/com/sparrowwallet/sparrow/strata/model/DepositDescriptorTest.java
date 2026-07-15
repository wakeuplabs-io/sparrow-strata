package com.sparrowwallet.sparrow.strata.model;

import com.sparrowwallet.sparrow.strata.protocol.AlpenConstants;
import com.sparrowwallet.drongo.Utils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DepositDescriptorTest {
    private static final String BRIDGE_PRECOMPILE = "5400000000000000000000000000000000000001";

    @Test
    void goldenVectorForAlpenEeDeposit() {
        byte[] subject = Utils.hexToBytes(BRIDGE_PRECOMPILE);
        DepositDescriptor descriptor = DepositDescriptor.create(AlpenConstants.ALPEN_EE_ACCT_SERIAL, subject);
        byte[] encoded = descriptor.encodeToBytes();
        assertArrayEquals(Utils.hexToBytes("0080" + BRIDGE_PRECOMPILE), encoded);
    }

    @Test
    void forAlpenDepositUsesEeAccountSerial() {
        AlpenAddress address = Eip55Address.parse("0x" + BRIDGE_PRECOMPILE);
        DepositDescriptor descriptor = DepositDescriptor.forAlpenDeposit(address);
        assertEquals(AlpenConstants.ALPEN_EE_ACCT_SERIAL, descriptor.getDestAcctSerial());
        assertArrayEquals(address.getBytes(), descriptor.getDestSubject());
    }

    @Test
    void roundtripSerialBoundaries() {
        roundtripSerial(0);
        roundtripSerial(4095);
        roundtripSerial(4096);
        roundtripSerial(1_048_575);
        roundtripSerial(1_048_576);
        roundtripSerial(268_435_455);
    }

    @Test
    void rejectsSerialTooLarge() {
        DepositDescriptorException exception = assertThrows(DepositDescriptorException.class,
                () -> DepositDescriptor.create(268_435_456, new byte[] {0x01}));
        assertEquals("Serial 268435456 exceeds maximum encodable value 268435455", exception.getMessage());
    }

    @Test
    void rejectsSubjectTooLong() {
        byte[] subject = new byte[AlpenConstants.MAX_SUBJECT_BYTES + 1];
        DepositDescriptorException exception = assertThrows(DepositDescriptorException.class,
                () -> DepositDescriptor.create(128, subject));
        assertEquals("Subject bytes too long: 33", exception.getMessage());
    }

    @Test
    void decodeRejectsEmptyDescriptor() {
        DepositDescriptorException exception = assertThrows(DepositDescriptorException.class,
                () -> DepositDescriptor.decodeFromBytes(new byte[0]));
        assertEquals("Descriptor is empty", exception.getMessage());
    }

    private void roundtripSerial(int serial) {
        byte[] subject = Utils.hexToBytes(BRIDGE_PRECOMPILE);
        DepositDescriptor original = DepositDescriptor.create(serial, subject);
        DepositDescriptor decoded = DepositDescriptor.decodeFromBytes(original.encodeToBytes());
        assertEquals(original, decoded);
    }
}
