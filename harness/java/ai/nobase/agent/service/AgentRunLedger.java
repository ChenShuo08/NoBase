package ai.nobase.agent.harness.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-run ledger of tool calls already executed during one Agent turn.
 *
 * <p>The model can re-issue the same {@code CONTROL} after seeing its own {@code [TOOL_RESULT]}
 * (and across turns it may repeat work it already did). The ledger makes the platform — not the
 * model — responsible for idempotency: an identical action + arguments pair that already succeeded
 * in this run is answered from the ledger instead of executed a second time.
 *
 * <p>Only successful calls are recorded, so a retry after a genuine failure still runs.
 */
public final class AgentRunLedger {

    private final Map<String, Map<String, Object>> executed = new LinkedHashMap<>();

    /** Stable identity of a call: upper-cased action plus its serialized arguments. */
    public static String fingerprint(String action, String canonicalArguments) {
        String normalizedAction = action == null ? "" : action.trim().toUpperCase(java.util.Locale.ROOT);
        String arguments = canonicalArguments == null || canonicalArguments.isBlank() ? "{}" : canonicalArguments;
        return normalizedAction + "|" + arguments;
    }

    /** Previous successful result for this exact call, or {@code null} when it has not run. */
    public Map<String, Object> previous(String fingerprint) {
        return fingerprint == null ? null : executed.get(fingerprint);
    }

    public void record(String fingerprint, Map<String, Object> result) {
        if (fingerprint != null && result != null) {
            executed.put(fingerprint, result);
        }
    }

    public int size() {
        return executed.size();
    }

    public boolean isEmpty() {
        return executed.isEmpty();
    }
}
