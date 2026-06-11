package com.sparrowwallet.sparrow.strata.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sparrowwallet.sparrow.net.HttpClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public class StrataRpcClient {
    private static final Logger log = LoggerFactory.getLogger(StrataRpcClient.class);
    private static final Gson GSON = new Gson();

    private static final String GET_ROLLUP_PARAMS_METHOD = "strata_getRollupParams";
    private static final String GET_CURRENT_DEPOSITS_METHOD = "strata_getCurrentDeposits";
    private static final String GET_CURRENT_DEPOSIT_BY_ID_METHOD = "strata_getCurrentDepositById";

    private static final int DEPOSIT_SAMPLE_SIZE = 20;

    private final HttpClientService httpClientService;
    private final String rpcUrl;

    public StrataRpcClient(HttpClientService httpClientService, String rpcUrl) {
        this.httpClientService = httpClientService;
        this.rpcUrl = rpcUrl;
    }

    public OptionalLong getDepositUtxoAmountSats() {
        OptionalLong fromRollupParams = getDepositUtxoAmountFromRollupParams();
        if(fromRollupParams.isPresent()) {
            return fromRollupParams;
        }
        return inferDepositUtxoAmountFromDeposits();
    }

    private OptionalLong getDepositUtxoAmountFromRollupParams() {
        try {
            JsonObject result = call(GET_ROLLUP_PARAMS_METHOD, List.of());
            return StrataDepositDenominationParser.parseDepositAmountFromRollupParams(result);
        } catch(StrataRpcException e) {
            if(e.isMethodNotFound()) {
                if(log.isDebugEnabled()) {
                    log.debug("Strata RPC method {} is unavailable at {}", GET_ROLLUP_PARAMS_METHOD, rpcUrl);
                }
            } else if(log.isDebugEnabled()) {
                log.debug("Failed to fetch rollup params from {}", rpcUrl, e);
            }
        } catch(Exception e) {
            if(log.isDebugEnabled()) {
                log.debug("Failed to fetch rollup params from {}", rpcUrl, e);
            }
        }
        return OptionalLong.empty();
    }

    private OptionalLong inferDepositUtxoAmountFromDeposits() {
        try {
            JsonElement depositsElement = callRaw(GET_CURRENT_DEPOSITS_METHOD, List.of());
            if(depositsElement == null || !depositsElement.isJsonArray() || depositsElement.getAsJsonArray().isEmpty()) {
                return OptionalLong.empty();
            }

            List<Long> amounts = new ArrayList<>();
            int sampled = 0;
            for(JsonElement depositIdElement : depositsElement.getAsJsonArray()) {
                if(sampled >= DEPOSIT_SAMPLE_SIZE) {
                    break;
                }
                int depositId = depositIdElement.getAsInt();
                JsonObject deposit = call(GET_CURRENT_DEPOSIT_BY_ID_METHOD, List.of(depositId));
                if(deposit == null || !deposit.has("amt")) {
                    continue;
                }
                OptionalLong amount = StrataDepositDenominationParser.parseAmountField(deposit.get("amt"));
                if(amount.isEmpty() || amount.getAsLong() <= 0) {
                    continue;
                }
                amounts.add(amount.getAsLong());
                sampled++;
            }

            return StrataDepositDenominationParser.inferDepositDenomination(amounts);
        } catch(Exception e) {
            if(log.isDebugEnabled()) {
                log.debug("Failed to infer deposit denomination from {}", rpcUrl, e);
            }
            return OptionalLong.empty();
        }
    }

    private JsonObject call(String method, List<Object> params) throws Exception {
        JsonElement result = callRaw(method, params);
        return result != null && result.isJsonObject() ? result.getAsJsonObject() : null;
    }

    @SuppressWarnings("unchecked")
    private JsonElement callRaw(String method, List<Object> params) throws Exception {
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        Map<String, Object> body = new HashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        body.put("params", params);
        body.put("id", 1);

        Optional<Map> optionalResponse = httpClientService.postJson(rpcUrl, Map.class, headers, body).blockingFirst();
        Map<String, Object> response = optionalResponse.orElse(null);
        if(response == null) {
            return null;
        }

        Object error = response.get("error");
        if(error instanceof Map<?, ?> errorMap) {
            Object code = errorMap.get("code");
            Object message = errorMap.get("message");
            throw new StrataRpcException(code instanceof Number number ? number.intValue() : null,
                    message == null ? "Strata RPC call failed" : message.toString());
        }

        Object result = response.get("result");
        if(result == null) {
            return null;
        }
        return JsonParser.parseString(GSON.toJson(result));
    }

    static final class StrataRpcException extends Exception {
        private final Integer errorCode;

        StrataRpcException(Integer errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        boolean isMethodNotFound() {
            return errorCode != null && errorCode == -32601;
        }
    }
}
