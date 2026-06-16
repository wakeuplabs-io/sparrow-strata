package com.sparrowwallet.sparrow.strata.net;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.StrataBridgeParametersUpdatedEvent;
import com.sparrowwallet.sparrow.strata.deposit.StrataBridgeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalLong;

public class StrataBridgeParametersService {
    private static final Logger log = LoggerFactory.getLogger(StrataBridgeParametersService.class);

    private static StrataBridgeParametersService instance;

    private volatile Long depositUtxoAmountSats;

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
        Network network = Network.get();
        OptionalLong amount = StrataBridgeConstants.getDepositUtxoAmountSats(network);
        if(amount.isPresent()) {
            setDepositUtxoAmountSats(amount.getAsLong());
            if(log.isInfoEnabled()) {
                log.info("Using hardcoded Strata deposit denomination {} sats for {}", amount.getAsLong(), network);
            }
        } else {
            setDepositUtxoAmountSats(null);
        }
    }

    private void setDepositUtxoAmountSats(Long amountSats) {
        if(amountSats != null && amountSats.equals(depositUtxoAmountSats)) {
            return;
        }
        depositUtxoAmountSats = amountSats;
        EventManager.get().post(new StrataBridgeParametersUpdatedEvent(amountSats));
    }
}
