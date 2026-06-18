package com.sparrowwallet.sparrow.strata.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sparrowwallet.sparrow.net.HttpClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public class StrataRpcClient {
    private static final Logger log = LoggerFactory.getLogger(StrataRpcClient.class);
    private static final Gson GSON = new Gson();

    private static final String GET_ROLLUP_PARAMS_METHOD = "strata_getRollupParams";
    private static final String GET_BRIDGE_OPERATOR_PUBKEY_METHOD = "strata_getBridgeOperatorPubkey";

    private final HttpClientService httpClientService;
    private final String rpcUrl;

    public StrataRpcClient(HttpClientService httpClientService, String rpcUrl) {
        this.httpClientService = httpClientService;
        this.rpcUrl = rpcUrl;
    }

    public OptionalLong getDepositUtxoAmountSats() {
        Optional<StrataRollupParams> params = getRollupParams();
        if(params.isEmpty()) {
            return OptionalLong.empty();
        }
        return params.get().getDepositAmountSats();
    }

    public Optional<StrataRollupParams> getRollupParams() {
        try {
            JsonObject result = call(GET_ROLLUP_PARAMS_METHOD, List.of());
            return StrataRollupParamsParser.parse(result);
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
        return Optional.empty();
    }

    public Optional<String> getBridgeOperatorPubkeyHex() {
        try {
            JsonObject result = call(GET_BRIDGE_OPERATOR_PUBKEY_METHOD, List.of());
            return StrataBridgeKeyParser.parseBridgePubkeyFromResult(result);
        } catch(StrataRpcException e) {
            if(e.isMethodNotFound()) {
                if(log.isDebugEnabled()) {
                    log.debug("Strata RPC method {} is unavailable at {}", GET_BRIDGE_OPERATOR_PUBKEY_METHOD, rpcUrl);
                }
            } else if(log.isDebugEnabled()) {
                log.debug("Failed to fetch bridge operator pubkey from {}", rpcUrl, e);
            }
        } catch(Exception e) {
            if(log.isDebugEnabled()) {
                log.debug("Failed to fetch bridge operator pubkey from {}", rpcUrl, e);
            }
        }
        return Optional.empty();
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
