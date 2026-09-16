package ai.nobase.agent.harness.service;

import ai.nubase.common.context.MultiTenancyContext;
import ai.nubase.postgrest.multidb.DatabaseConfig;
import ai.nubase.postgrest.multidb.DatabaseConfigRepository;
import ai.nubase.postgrest.multidb.RoutingDataSource;
import ai.nubase.metadata.repository.PlatformUserProjectRepository;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;

/**
 * Runs arbitrary work inside one project's {@code MultiTenancyContext} with the service role.
 *
 * <p>The MCP tools ({@code ai.nubase.mcp.tools.*}) authorise and route through
 * {@link MultiTenancyContext} — the same ThreadLocal the HTTP multi-tenancy filter populates from a
 * project apikey. An in-process Agent call has no such request, so without this helper every
 * mutating MCP tool (create function, deploy, set secrets, create bucket, cron, SQL…) would answer
 * "service_role MCP apikey is required". This service supplies exactly that context for the
 * duration of one tool call and always clears it afterwards.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentProjectContext {

    private final DatabaseConfigRepository databaseConfigRepository;
    private final PlatformUserProjectRepository platformUserProjectRepository;
    private final RoutingDataSource routingDataSource;

    /**
     * Execute {@code work} with the given project's tenant context.
     *
     * @throws IllegalStateException when the project is missing, disabled, or not owned by the caller
     */
    public <T> T run(String ref, UUID userId, boolean superAdmin, Function<DatabaseConfig, T> work) {
        if (ref == null || ref.isBlank()) {
            throw new IllegalStateException("No project selected. Pick a project first (projectRef).");
        }
        DatabaseConfig config = databaseConfigRepository.findByAppCode(ref);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new IllegalStateException("Project " + ref + " was not found or is disabled.");
        }
        if (!superAdmin && platformUserProjectRepository.findByUserIdAndDbKey(userId, config.getDbKey()).isEmpty()) {
            throw new IllegalStateException("You do not have access to project " + ref + ".");
        }
        if (!routingDataSource.hasDataSource(config.getDbKey())) {
            routingDataSource.initializeDataSource(config);
        }
        MultiTenancyContext.ContextData context = MultiTenancyContext.ContextData.builder()
                .appCode(config.getAppCode())
                .schemaName(config.getSchemaName())
                .jwtSecret(config.getJwtSecret())
                .jwtSecretKey(Keys.hmacShaKeyFor(config.getJwtSecret().getBytes(StandardCharsets.UTF_8)))
                .databaseKey(config.getDbKey())
                .databaseConfig(config)
                .apikey(config.getServiceRoleToken())
                .serviceRole(true)
                .build();
        MultiTenancyContext.setContext(context);
        try {
            return work.apply(config);
        } finally {
            MultiTenancyContext.clear();
        }
    }
}
