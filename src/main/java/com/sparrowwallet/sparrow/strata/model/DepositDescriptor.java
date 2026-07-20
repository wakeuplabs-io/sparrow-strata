package com.sparrowwallet.sparrow.strata.model;

import com.sparrowwallet.sparrow.strata.protocol.AlpenConstants;
import java.util.Arrays;

public final class DepositDescriptor {
    private static final int MAX_SERIAL_12_BITS = (1 << 12) - 1;
    private static final int MAX_SERIAL_20_BITS = (1 << 20) - 1;
    private static final int MAX_SERIAL_28_BITS = (1 << 28) - 1;
    private static final int MAX_SERIAL_VALUE = MAX_SERIAL_28_BITS;

    private static final int RESERVED_MASK = 0b1100_0000;
    private static final int LEN_MASK = 0b0011_0000;
    private static final int MSB_NIBBLE_MASK = 0b0000_1111;

    private final int destAcctSerial;
    private final byte[] destSubject;

    private DepositDescriptor(int destAcctSerial, byte[] destSubject) {
        this.destAcctSerial = destAcctSerial;
        this.destSubject = destSubject;
    }

    public static DepositDescriptor forAlpenDeposit(AlpenAddress address) {
        return new DepositDescriptor(AlpenConstants.ALPEN_EE_ACCT_SERIAL, address.getBytes());
    }

    public static DepositDescriptor create(int destAcctSerial, byte[] destSubject) {
        if(destAcctSerial < 0 || destAcctSerial > MAX_SERIAL_VALUE) {
            throw new DepositDescriptorException("Serial " + destAcctSerial + " exceeds maximum encodable value " + MAX_SERIAL_VALUE);
        }
        if(destSubject == null || destSubject.length > AlpenConstants.MAX_SUBJECT_BYTES) {
            throw new DepositDescriptorException("Subject bytes too long: " + (destSubject == null ? 0 : destSubject.length));
        }
        return new DepositDescriptor(destAcctSerial, Arrays.copyOf(destSubject, destSubject.length));
    }

    public int getDestAcctSerial() {
        return destAcctSerial;
    }

    public byte[] getDestSubject() {
        return Arrays.copyOf(destSubject, destSubject.length);
    }

    public byte[] encodeToBytes() {
        EncodedSerial encodedSerial = EncodedSerial.fromAccountSerial(destAcctSerial);
        byte[] additional = encodedSerial.additionalBytes();
        byte[] out = new byte[1 + additional.length + destSubject.length];
        out[0] = (byte)encodedSerial.controlByte();
        System.arraycopy(additional, 0, out, 1, additional.length);
        System.arraycopy(destSubject, 0, out, 1 + additional.length, destSubject.length);
        return out;
    }

    public static DepositDescriptor decodeFromBytes(byte[] bytes) {
        if(bytes == null || bytes.length == 0) {
            throw new DepositDescriptorException("Descriptor is empty");
        }

        ControlByte control = ControlByte.fromByte(bytes[0]);
        int serialLen = control.serialLength();
        if(bytes.length < 1 + serialLen) {
            throw new DepositDescriptorException("Descriptor length " + bytes.length + " is shorter than required " + (1 + serialLen));
        }

        byte[] additionalBytes = new byte[3];
        System.arraycopy(bytes, 1, additionalBytes, 0, serialLen);
        int destAcctSerial = new EncodedSerial(control, additionalBytes).toAccountSerial();

        int subjectStart = 1 + serialLen;
        byte[] subjectBytes = Arrays.copyOfRange(bytes, subjectStart, bytes.length);
        if(subjectBytes.length > AlpenConstants.MAX_SUBJECT_BYTES) {
            throw new DepositDescriptorException("Subject bytes too long: " + subjectBytes.length);
        }

        return new DepositDescriptor(destAcctSerial, subjectBytes);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj) {
            return true;
        }
        if(!(obj instanceof DepositDescriptor other)) {
            return false;
        }
        return destAcctSerial == other.destAcctSerial && Arrays.equals(destSubject, other.destSubject);
    }

    @Override
    public int hashCode() {
        return 31 * destAcctSerial + Arrays.hashCode(destSubject);
    }

    private static final class ControlByte {
        private final int value;

        private ControlByte(int value) {
            this.value = value;
        }

        static ControlByte fromByte(int byteValue) {
            if((byteValue & RESERVED_MASK) != 0) {
                throw new DepositDescriptorException("Reserved control bits set: 0x" + Integer.toHexString(byteValue));
            }
            int lenBits = (byteValue & LEN_MASK) >> 4;
            if(lenBits == 3) {
                throw new DepositDescriptorException("Reserved serial length bits set: 0x" + Integer.toHexString(byteValue));
            }
            return new ControlByte(byteValue);
        }

        int serialLength() {
            return ((value & LEN_MASK) >> 4) + 1;
        }

        int serialMsb() {
            return value & MSB_NIBBLE_MASK;
        }

        int controlByte() {
            return value;
        }

        ControlByte withSerialLength(int len) {
            return new ControlByte(value | (len << 4));
        }

        ControlByte withSerialMsb(int msb) {
            return new ControlByte(value | (msb & MSB_NIBBLE_MASK));
        }
    }

    private static final class EncodedSerial {
        private final ControlByte control;
        private final byte[] additionalBytes;

        private EncodedSerial(ControlByte control, byte[] additionalBytes) {
            this.control = control;
            this.additionalBytes = additionalBytes;
        }

        static EncodedSerial fromAccountSerial(int serial) {
            if(serial < 0 || serial > MAX_SERIAL_VALUE) {
                throw new DepositDescriptorException("Serial " + serial + " exceeds maximum encodable value " + MAX_SERIAL_VALUE);
            }

            byte[] be = new byte[] {
                    (byte)(serial >>> 24),
                    (byte)(serial >>> 16),
                    (byte)(serial >>> 8),
                    (byte)serial
            };

            ControlByte control = new ControlByte(0);
            byte[] additional = new byte[3];
            if(serial <= MAX_SERIAL_12_BITS) {
                control = control.withSerialLength(0).withSerialMsb(be[2]);
                additional[0] = be[3];
            } else if(serial <= MAX_SERIAL_20_BITS) {
                control = control.withSerialLength(1).withSerialMsb(be[1]);
                additional[0] = be[2];
                additional[1] = be[3];
            } else {
                control = control.withSerialLength(2).withSerialMsb(be[0]);
                additional[0] = be[1];
                additional[1] = be[2];
                additional[2] = be[3];
            }

            return new EncodedSerial(control, additional);
        }

        int controlByte() {
            return control.controlByte();
        }

        byte[] additionalBytes() {
            int len = control.serialLength();
            return Arrays.copyOf(additionalBytes, len);
        }

        int toAccountSerial() {
            int additionalLen = control.serialLength();
            byte[] u32Bytes = new byte[4];
            System.arraycopy(additionalBytes, 0, u32Bytes, 4 - additionalLen, additionalLen);
            int msbIndex = 4 - additionalLen - 1;
            u32Bytes[msbIndex] = (byte)(u32Bytes[msbIndex] | control.serialMsb());
            return ((u32Bytes[0] & 0xFF) << 24)
                    | ((u32Bytes[1] & 0xFF) << 16)
                    | ((u32Bytes[2] & 0xFF) << 8)
                    | (u32Bytes[3] & 0xFF);
        }
    }
}
