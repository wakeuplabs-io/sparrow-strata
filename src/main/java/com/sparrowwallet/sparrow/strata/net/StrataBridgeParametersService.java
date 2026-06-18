package com.sparrowwallet.sparrow.strata.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.StrataBridgeParametersUpdatedEvent;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

public class StrataBridgeParametersService {
    private static final Logger log = LoggerFactory.getLogger(StrataBridgeParametersService.class);

    private static StrataBridgeParametersService instance;
    private static String rpcUrlForTesting;

    private volatile byte[] magicBytes;
    private volatile Long depositUtxoAmountSats;
    private volatile Integer recoveryDelay;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    private StrataBridgeParametersService() {
    }

    public static StrataBridgeParametersService getInstance() {
        if(instance == null) {
            instance = new StrataBridgeParametersService();
        }
        return instance;
    }

    static void clearInstanceForTesting() {
        instance = null;
        rpcUrlForTesting = null;
    }

    static void setRpcUrlForTesting(String url) {
        rpcUrlForTesting = url;
    }

    public OptionalLong getDepositUtxoAmountSats() {
        return depositUtxoAmountSats == null ? OptionalLong.empty() : OptionalLong.of(depositUtxoAmountSats);
    }

    public byte[] getMagicBytes() {
        if(magicBytes != null) {
            return Arrays.copyOf(magicBytes, magicBytes.length);
        }
        return Arrays.copyOf(StrataBridgeConstants.getMagicBytesFallback(Network.get()), 4);
    }

    public int getRecoveryDelay() {
        return recoveryDelay != null ? recoveryDelay : StrataBridgeConstants.RECOVER_DELAY;
    }

    public void refresh() {
        if(!refreshInProgress.compareAndSet(false, true)) {
            return;
        }

        Network network = Network.get();
        applyFallback(network);

        String rpcUrl = rpcUrlForTesting != null ? rpcUrlForTesting : StrataBridgeConstants.getStrataRpcUrl(network);
        if(rpcUrl == null) {
            refreshInProgress.set(false);
            return;
        }

        Schedulers.io().scheduleDirect(() -> {
            try {
                StrataRpcClient client = new StrataRpcClient(AppServices.getHttpClientService(), rpcUrl);
                client.getRollupParams().ifPresentOrElse(
                        params -> applyRollupParams(params, network),
                        () -> applyFallback(network)
                );
            } catch(Exception e) {
                if(log.isWarnEnabled()) {
                    log.warn("Failed to fetch Strata rollup params from {}", rpcUrl, e);
                }
                applyFallback(network);
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    private void applyRollupParams(StrataRollupParams params, Network network) {
        byte[] resolvedMagic = params.getMagicBytes().orElse(StrataBridgeConstants.getMagicBytesFallback(network));
        Long resolvedDeposit = params.getDepositAmountSats().orElse(
                StrataBridgeConstants.getDepositUtxoAmountSats(network).orElse(StrataBridgeConstants.DEPOSIT_UTXO_AMOUNT_SATS));
        int resolvedRecoveryDelay = params.getRecoveryDelay().orElse(StrataBridgeConstants.RECOVER_DELAY);
        apply(resolvedMagic, resolvedDeposit, resolvedRecoveryDelay, network, false);
    }

    private void applyFallback(Network network) {
        OptionalLong amount = StrataBridgeConstants.getDepositUtxoAmountSats(network);
        byte[] fallbackMagic = StrataBridgeConstants.getMagicBytesFallback(network);
        if(amount.isPresent()) {
            apply(fallbackMagic, amount.getAsLong(), StrataBridgeConstants.RECOVER_DELAY, network, true);
        } else {
            apply(null, null, StrataBridgeConstants.RECOVER_DELAY, network, true);
        }
    }

    private void apply(byte[] newMagicBytes, Long newDepositUtxoAmountSats, int newRecoveryDelay,
                       Network network, boolean fallback) {
        boolean changed = !Arrays.equals(this.magicBytes, newMagicBytes)
                || !java.util.Objects.equals(this.depositUtxoAmountSats, newDepositUtxoAmountSats)
                || !java.util.Objects.equals(this.recoveryDelay, newRecoveryDelay);

        if(!changed) {
            return;
        }

        this.magicBytes = newMagicBytes == null ? null : Arrays.copyOf(newMagicBytes, newMagicBytes.length);
        this.depositUtxoAmountSats = newDepositUtxoAmountSats;
        this.recoveryDelay = newRecoveryDelay;

        if(log.isInfoEnabled()) {
            if(fallback) {
                log.info("Using fallback Strata bridge parameters for {} (deposit {} sats)",
                        network, newDepositUtxoAmountSats);
            } else {
                log.info("Loaded Strata rollup params for {} (deposit {} sats, magic {})",
                        network, newDepositUtxoAmountSats, new String(getMagicBytes(), StandardCharsets.US_ASCII));
            }
        }

        EventManager.get().post(new StrataBridgeParametersUpdatedEvent(newDepositUtxoAmountSats));
    }
}
