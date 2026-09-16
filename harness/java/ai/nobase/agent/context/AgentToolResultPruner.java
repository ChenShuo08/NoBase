package ai.nobase.agent.harness.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Deterministic head/middle/tail pruning for oversized tool results.
 *
 * <p>Direct port of the DeepSeek Harness policy ({@code dsh-compaction-tool-result-pruner}):
 * a tool result longer than {@code prune-threshold-chars} keeps its first {@code prune-head-chars}
 * and last {@code prune-tail-chars} code points, with the removed span replaced by a fixed marker.
 * Slicing is by Unicode code point so a retained boundary can never split a surrogate pair.
 *
 * <p>Pruning is model-free and replay-safe: the same input always yields the same output, and a
 * result already within budget is returned untouched (via {@code null}).
 */
@Component
@RequiredArgsConstructor
public class AgentToolResultPruner {

    /** Fixed marker substituted for every removed middle span. */
    public static final String PRUNE_MARKER = "\n\n[... tool result middle pruned ...]\n\n";

    private static final int MARKER_CODE_POINTS = PRUNE_MARKER.codePointCount(0, PRUNE_MARKER.length());

    private final AgentContextProperties properties;

    /** Number of Unicode code points in {@code text}. */
    public static int codePointLength(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    /**
     * Prune the middle of {@code text} when it exceeds the configured threshold.
     *
     * @return the pruned text, or {@code null} when the input is already within budget.
     */
    public String prune(String text) {
        if (text == null) return null;
        int total = codePointLength(text);
        int threshold = properties.getPruneThresholdChars();
        if (total <= threshold) return null;

        int headChars = properties.getPruneHeadChars();
        int tailChars = properties.getPruneTailChars();
        int emitted = headChars + MARKER_CODE_POINTS + tailChars;
        if (emitted > threshold) {
            // Defensive: properties validation should already prevent this.
            throw new IllegalStateException(
                    "tool-result prune: head + marker + tail (" + emitted + ") exceeds threshold " + threshold);
        }

        int headEnd = text.offsetByCodePoints(0, Math.min(headChars, total));
        int tailStart = text.offsetByCodePoints(0, Math.max(0, total - tailChars));
        if (tailStart < headEnd) {
            // Extremely small tail budget; keep the head only.
            tailStart = headEnd;
        }
        String pruned = text.substring(0, headEnd) + PRUNE_MARKER + text.substring(tailStart);

        int after = codePointLength(pruned);
        if (after >= total || after > threshold) {
            throw new IllegalStateException("tool-result prune: replacement must be smaller and within threshold");
        }
        return pruned;
    }

    /** Prune a tool-result message, preserving its {@code [TOOL_RESULT]} prefix. */
    public ContextMessage pruneMessage(ContextMessage message) {
        if (message == null || !message.isToolResult()) return message;
        String pruned = prune(message.content());
        return pruned == null ? message : message.withContent(pruned);
    }
}
