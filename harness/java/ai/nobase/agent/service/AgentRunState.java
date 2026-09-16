package ai.nobase.agent.harness.service;

import java.util.UUID;

/**
 * Per-turn state for one Agent request: which conversation it belongs to, plus the in-run ledger of
 * calls already executed.
 *
 * <p>Held in a {@link ThreadLocal} because the whole turn — model streaming, tool execution, audit
 * writes — runs synchronously on the servlet/async thread that started the SSE response. This mirrors
 * the existing {@code MultiTenancyContext} convention in this codebase and keeps the session id and
 * ledger out of every intermediate method signature.
 *
 * <p>{@link #clear()} is always called in a {@code finally} block by {@code stream()}.
 */
final class AgentRunState {

    private static final ThreadLocal<AgentRunState> CURRENT = new ThreadLocal<>();

    private final UUID sessionId;
    private final String apiBaseUrl;
    private final AgentRunLedger ledger = new AgentRunLedger();

    private AgentRunState(UUID sessionId, String apiBaseUrl) {
        this.sessionId = sessionId;
        this.apiBaseUrl = apiBaseUrl;
    }

    static AgentRunState begin(UUID sessionId, String apiBaseUrl) {
        AgentRunState state = new AgentRunState(sessionId, apiBaseUrl);
        CURRENT.set(state);
        return state;
    }

    /** Public base URL of this deployment, used to write real API examples instead of placeholders. */
    static String currentApiBaseUrl() {
        AgentRunState state = CURRENT.get();
        return state == null ? null : state.apiBaseUrl;
    }

    /** Session id of the turn in flight, or {@code null} when called outside a turn. */
    static UUID currentSessionId() {
        AgentRunState state = CURRENT.get();
        return state == null ? null : state.sessionId;
    }

    /** Ledger of calls already executed in the turn in flight, or {@code null} outside a turn. */
    static AgentRunLedger currentLedger() {
        AgentRunState state = CURRENT.get();
        return state == null ? null : state.ledger;
    }

    static void clear() {
        CURRENT.remove();
    }
}
