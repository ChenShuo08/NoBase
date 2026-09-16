package ai.nobase.agent.harness.service;

/**
 * Declarative contract for one platform action the Agent may invoke.
 *
 * <p>{@code riskLevel} drives how the UI presents a call (badge colour, warning icon) while
 * {@code requiresConfirm} is the authoritative gate: when true the platform pauses the agent loop
 * and persists a confirmation card instead of executing. Keeping the gate explicit (rather than
 * deriving it from {@code riskLevel} alone) lets a read-only tool stay one-click while an
 * irreversible write always asks first.
 */
public record AgentToolDefinition(
        String action,
        String name,
        String title,
        String description,
        String riskLevel,
        boolean reversible,
        boolean requiresConfirm,
        /**
         * Fallback confirmation copy used when the model supplies none. A read-only action that needs
         * consent (reading API keys) must not be described as "this will modify platform state".
         */
        String confirmationBody
) {

    public static final String RISK_READ = "READ";
    public static final String RISK_WRITE = "WRITE";
    public static final String RISK_DESTRUCTIVE = "DESTRUCTIVE";

    /** Default copy for actions that change platform state. */
    public static final String DEFAULT_CONFIRMATION_BODY = "该操作会修改平台状态，请确认执行。";

    /** Every action that mutates state keeps the original seven-field shape. */
    public AgentToolDefinition(String action, String name, String title, String description,
                               String riskLevel, boolean reversible, boolean requiresConfirm) {
        this(action, name, title, description, riskLevel, reversible, requiresConfirm, null);
    }

    /** Copy for the confirmation card, falling back to the mutating-state wording. */
    public String confirmationBodyOrDefault() {
        return confirmationBody == null || confirmationBody.isBlank()
                ? DEFAULT_CONFIRMATION_BODY
                : confirmationBody;
    }

    /** True when the call changes platform state at all. */
    public boolean mutates() {
        return !RISK_READ.equals(riskLevel);
    }
}
