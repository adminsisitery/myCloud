package com.ff.mycloud.ai.agent;

/** Stable, non-sensitive failure codes for the API and persisted run. */
public final class AgentExecutionException extends IllegalStateException {
    public AgentExecutionException(String code) { super(code); }
}
