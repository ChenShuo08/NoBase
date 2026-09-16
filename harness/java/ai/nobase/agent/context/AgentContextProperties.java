package ai.nobase.agent.harness.context;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Budgets for the Agent context layer.
 *
 * <p>Defaults mirror the policies proven in the DeepSeek Harness runtime
 * ({@code dsh-compaction-basic}, {@code dsh-compaction-tool-result-pruner},
 * {@code dsh-token-meter}): compact at 80% of the window, retain 16%, prune tool-result middles
 * above 8192 characters keeping 4096 head / 1024 tail, and estimate 4 characters per token.
 */
@Data
@Component
@ConfigurationProperties(prefix = "nubase.agent.context")
public class AgentContextProperties {

    /** Model context window in tokens. */
    private int contextWindowTokens = 128_000;

    /** Fraction of the window that triggers compaction. */
    private double thresholdRatio = 0.8;

    /** Fraction of the window retained after compaction; must be below {@link #thresholdRatio}. */
    private double retainRatio = 0.16;

    /** Characters per token used by the estimator. */
    private int charsPerToken = 4;

    /** Tool results longer than this (code points) get their middle pruned. */
    private int pruneThresholdChars = 8_192;

    /** Head kept when pruning a tool result. */
    private int pruneHeadChars = 4_096;

    /** Tail kept when pruning a tool result. */
    private int pruneTailChars = 1_024;

    /** Hard cap for any single message kept in context; longer content is truncated. */
    private int maxMessageChars = 32_000;

    /** Never drop below this many recent messages, even when over budget. */
    private int minRecentMessages = 6;

    @PostConstruct
    void validate() {
        if (contextWindowTokens <= 0) {
            throw new IllegalStateException("nubase.agent.context.context-window-tokens must be positive");
        }
        if (thresholdRatio <= 0 || thresholdRatio > 1) {
            throw new IllegalStateException("nubase.agent.context.threshold-ratio must be in (0, 1]");
        }
        if (retainRatio <= 0 || retainRatio >= thresholdRatio) {
            throw new IllegalStateException(
                    "nubase.agent.context.retain-ratio must be in (0, threshold-ratio)");
        }
        if (charsPerToken <= 0) {
            throw new IllegalStateException("nubase.agent.context.chars-per-token must be positive");
        }
        if (pruneHeadChars < 0 || pruneTailChars < 0) {
            throw new IllegalStateException("nubase.agent.context prune budgets must be non-negative");
        }
        if (pruneHeadChars + pruneTailChars > pruneThresholdChars) {
            throw new IllegalStateException(
                    "nubase.agent.context: prune head + tail must not exceed prune-threshold-chars");
        }
        if (minRecentMessages < 0) {
            throw new IllegalStateException("nubase.agent.context.min-recent-messages must be non-negative");
        }
    }

    public int thresholdTokens() {
        return (int) Math.floor(contextWindowTokens * thresholdRatio);
    }

    public int retainTokens() {
        return (int) Math.floor(contextWindowTokens * retainRatio);
    }
}
