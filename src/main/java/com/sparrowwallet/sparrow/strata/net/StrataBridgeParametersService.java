package com.sparrowwallet.sparrow.strata.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.StrataBridgeParametersUpdatedEvent;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

public class StrataBridgeParametersService {
    private static final Logger log = LoggerFactory.getLogger(StrataBridgeParametersService.class);

    private static StrataBridgeParametersService instance;

    private volatile Long depositUtxoAmountSats;
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);

    private StrataBridgeParametersService() {
    }

    public static StrataBridgeParametersService getInstance() {
        if(instance == null) {
            instance = new StrataBridgeParametersService();
        }
        return instance;
    }

    public OptionalLong getDepositUtxoAmountSats() {
        return depositUtxoAmountSats == null ? OptionalLong.empty() : OptionalLong.of(depositUtxoAmountSats);
    }

    public void refresh() {
        if(!refreshInProgress.compareAndSet(false, true)) {
            return;
        }

        Network network = Network.get();
        if(network == Network.SIGNET) {
            setDepositUtxoAmountSats(StrataBridgeConstants.SIGNET_MOCK_DEPOSIT_UTXO_AMOUNT_SATS);
            refreshInProgress.set(false);
            return;
        }

        String rpcUrl = StrataBridgeConstants.getStrataRpcUrl(network);
        if(rpcUrl == null) {
            setDepositUtxoAmountSats(null);
            refreshInProgress.set(false);
            return;
        }

        Schedulers.io().scheduleDirect(() -> {
            try {
                StrataRpcClient client = new StrataRpcClient(AppServices.getHttpClientService(), rpcUrl);
                OptionalLong amount = client.getDepositUtxoAmountSats();
                setDepositUtxoAmountSats(amount.isPresent() ? amount.getAsLong() : null);
                if(amount.isPresent()) {
                    if(log.isInfoEnabled()) {
                        log.info("Loaded Strata deposit denomination {} sats from {}", amount.getAsLong(), rpcUrl);
                    }
                } else if(log.isWarnEnabled()) {
                    log.warn("Unable to load Strata deposit denomination from {}", rpcUrl);
                }
            } catch(Exception e) {
                setDepositUtxoAmountSats(null);
                if(log.isWarnEnabled()) {
                    log.warn("Failed to refresh Strata bridge parameters from {}", rpcUrl, e);
                }
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    private void setDepositUtxoAmountSats(Long amountSats) {
        if(amountSats != null && amountSats.equals(depositUtxoAmountSats)) {
            return;
        }
        depositUtxoAmountSats = amountSats;
        EventManager.get().post(new StrataBridgeParametersUpdatedEvent(amountSats));
    }
}
