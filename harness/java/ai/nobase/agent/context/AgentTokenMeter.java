package ai.nobase.agent.harness.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fixed text-density estimator for context budgeting.
 *
 * <p>Ports the DeepSeek Harness heuristic ({@code dsh-token-meter}): {@code ceil(chars / 4)} per text
 * block plus a small per-message overhead. Exact tokenisation is deliberately avoided — the budget
 * only needs to be conservative enough to trigger compaction before the provider rejects a request.
 */
@Component
@RequiredArgsConstructor
public class AgentTokenMeter {

    /** Per-message protocol overhead (role, separators) in tokens. */
    private static final int MESSAGE_OVERHEAD = 4;

    private final AgentContextProperties properties;

    public int estimateText(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil((double) text.length() / properties.getCharsPerToken());
    }

    public int estimateMessage(ContextMessage message) {
        if (message == null) return 0;
        return estimateText(message.content()) + MESSAGE_OVERHEAD;
    }

    public int estimateMessages(List<ContextMessage> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        int total = 0;
        for (ContextMessage message : messages) {
            total += estimateMessage(message);
        }
        return total;
    }

    /** Estimated cost of the static system prompt plus the injected tool catalogue. */
    public int estimateSystem(String systemPrompt) {
        return estimateText(systemPrompt) + MESSAGE_OVERHEAD;
    }
}
