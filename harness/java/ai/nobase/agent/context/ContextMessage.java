package ai.nobase.agent.harness.context;

/**
 * One conversation message as seen by the context layer.
 *
 * <p>Kept independent of the orchestration service's own history type so budgeting, pruning and
 * compaction stay unit-testable without the LLM wiring.
 */
public record ContextMessage(String role, String content) {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public boolean isToolResult() {
        return ROLE_USER.equals(role) && content != null && content.startsWith("[TOOL_RESULT]");
    }

    public ContextMessage withContent(String newContent) {
        return new ContextMessage(role, newContent);
    }
}
