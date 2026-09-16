package ai.nobase.agent.harness.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation for in-flight Agent turns.
 *
 * <p>A turn is one long SSE request, so the client cannot simply "cancel" it: the loop must be told to
 * stop between steps. The UI posts a stop for its conversation id, the loop checks the flag before
 * every model call and every tool execution, and unwinds with a normal "stopped" message instead of
 * continuing to emit confirmation cards.
 */
@Service
@Slf4j
public class AgentRunControl {

    private final Map<UUID, AtomicBoolean> cancelled = new ConcurrentHashMap<>();

    /** Mark a conversation as running again; clears any stale stop from a previous turn. */
    public void begin(UUID sessionId) {
        if (sessionId == null) return;
        cancelled.put(sessionId, new AtomicBoolean(false));
    }

    /** Request cancellation of the turn in flight for this conversation. */
    public boolean cancel(UUID sessionId) {
        if (sessionId == null) return false;
        AtomicBoolean flag = cancelled.computeIfAbsent(sessionId, key -> new AtomicBoolean(false));
        boolean wasRunning = !flag.getAndSet(true);
        if (wasRunning) {
            log.info("Agent turn cancellation requested for session {}", sessionId);
        }
        return wasRunning;
    }

    public boolean isCancelled(UUID sessionId) {
        if (sessionId == null) return false;
        AtomicBoolean flag = cancelled.get(sessionId);
        return flag != null && flag.get();
    }

    /** Release the slot once the turn has unwound. */
    public void clear(UUID sessionId) {
        if (sessionId != null) {
            cancelled.remove(sessionId);
        }
    }
}
