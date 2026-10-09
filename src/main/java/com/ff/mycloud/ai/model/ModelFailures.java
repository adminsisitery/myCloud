package com.ff.mycloud.ai.model;

import dev.langchain4j.exception.HttpException;

/**
 * Classify provider failures without exposing response bodies, keys or internal URLs.
 */
public final class ModelFailures {
    private ModelFailures() {
    }

    public static boolean retryable(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpException http && (http.statusCode() == 429 || http.statusCode() == 502 || http.statusCode() == 503 || http.statusCode() == 504))
                return true;
            if (cause instanceof java.net.SocketTimeoutException) return true;
        }
        return false;
    }

    public static String code(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpException http) {
                if (http.statusCode() == 401 || http.statusCode() == 403) return "MODEL_AUTH_FAILED";
                if (http.statusCode() == 429) return "MODEL_RATE_LIMITED";
            }
        }
        return retryable(error) ? "MODEL_TEMPORARILY_UNAVAILABLE" : "MODEL_CALL_FAILED";
    }
}
