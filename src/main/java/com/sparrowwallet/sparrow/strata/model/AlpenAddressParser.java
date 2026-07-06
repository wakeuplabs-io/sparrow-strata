package com.sparrowwallet.sparrow.strata.model;

import com.sparrowwallet.drongo.Network;

public final class AlpenAddressParser {
    private AlpenAddressParser() {
    }

    public static AlpenAddressParseResult parse(String input, Network bitcoinNetwork) {
        if(input == null || input.isBlank()) {
            throw new IllegalArgumentException("Deposit address is required");
        }

        AlpenAddress address;
        if(Erc7930Address.looksLikeErc7930(input)) {
            Erc7930Address interoperable = Erc7930Address.parse(input);
            validateChainReference(interoperable.getChainId(), bitcoinNetwork);
            address = interoperable.getAddress();
        } else {
            address = Eip55Address.parse(input);
        }

        DepositDescriptor descriptor = DepositDescriptor.forAlpenDeposit(address);
        return new AlpenAddressParseResult(address, descriptor);
    }

    private static void validateChainReference(Integer chainId, Network bitcoinNetwork) {
        if(chainId == null) {
            return;
        }
        int expected = AlpenConstants.expectedAlpenChainId(bitcoinNetwork);
        if(chainId != expected) {
            throw new IllegalArgumentException(AlpenConstants.INVALID_ALPEN_ADDRESS_MESSAGE);
        }
    }
}
