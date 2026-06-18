package com.sparrowwallet.sparrow.strata.net;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public final class StrataRollupParams {
    private final byte[] magicBytes;
    private final Long depositAmountSats;
    private final Integer recoveryDelay;

    private StrataRollupParams(byte[] magicBytes, Long depositAmountSats, Integer recoveryDelay) {
        this.magicBytes = magicBytes;
        this.depositAmountSats = depositAmountSats;
        this.recoveryDelay = recoveryDelay;
    }

    public static StrataRollupParams of(byte[] magicBytes, Long depositAmountSats, Integer recoveryDelay) {
        if(magicBytes == null && depositAmountSats == null && recoveryDelay == null) {
            return null;
        }
        return new StrataRollupParams(magicBytes, depositAmountSats, recoveryDelay);
    }

    public Optional<byte[]> getMagicBytes() {
        return magicBytes == null ? Optional.empty() : Optional.of(Arrays.copyOf(magicBytes, magicBytes.length));
    }

    public OptionalLong getDepositAmountSats() {
        return depositAmountSats == null ? OptionalLong.empty() : OptionalLong.of(depositAmountSats);
    }

    public OptionalInt getRecoveryDelay() {
        return recoveryDelay == null ? OptionalInt.empty() : OptionalInt.of(recoveryDelay);
    }

    public boolean isEmpty() {
        return magicBytes == null && depositAmountSats == null && recoveryDelay == null;
    }
}
