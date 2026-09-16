package ai.nobase.agent.harness.demo;

import ai.nobase.agent.harness.context.AgentContextProperties;
import ai.nobase.agent.harness.context.AgentTokenMeter;
import ai.nobase.agent.harness.context.AgentToolResultPruner;
import ai.nobase.agent.harness.context.ContextMessage;
import ai.nobase.agent.harness.service.AgentProjectContext;
import ai.nobase.agent.harness.service.AgentRunControl;
import ai.nobase.agent.harness.service.AgentRunLedger;
import ai.nobase.agent.harness.service.AgentRunState;
import ai.nobase.agent.harness.service.AgentToolDefinition;

import java.util.List;

public class AgentToolRegistryExample {

    public static void main(String[] args) {
        AgentToolDefinition listProjects = new AgentToolDefinition(
                "list_projects",
                "列出当前可操作的项目",
                "project",
                AgentToolDefinition.Risk.READ,
                false,
                List.of(new AgentToolDefinition.Argument("project", "string", "项目标识")),
                AgentProjectContext.class::isInstance
        );

        AgentRunState state = new AgentRunState();
        AgentRunControl control = new AgentRunControl(state);
        AgentRunLedger ledger = new AgentRunLedger(control);
        AgentContextProperties properties = new AgentContextProperties();
        AgentTokenMeter meter = new AgentTokenMeter(properties);
        AgentToolResultPruner pruner = new AgentToolResultPruner(properties);

        ContextMessage live = new ContextMessage("system", "[LIVE_CONTEXT] 当前项目：demo");
        System.out.println("tool=" + listProjects.action());
        System.out.println("risk=" + listProjects.risk());
        System.out.println("estimatedTokens=" + meter.estimateTokens(List.of(live, new ContextMessage("user", "hello"))));
        System.out.println("pruned=" + pruner.prune("x".repeat(10000), "tool-result-1").content());
    }
}
