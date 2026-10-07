package org.koaks.codereview.review.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import io.agentscope.harness.agent.sandbox.layout.LocalDirEntry;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.tools.ToolsConfig;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.repo.domain.ExecutionMode;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds the agents used by review and Issue investigation tasks. LOCAL uses the host filesystem;
 * SANDBOX projects the task checkout into a Docker-backed AgentScope sandbox.
 */
@Component
public class ReviewAgentFactory {

    static final List<String> LEGACY_DENIED_TOOLS = List.of(
            "write_file", "edit_file", "web_fetch", "web_search", "wait_async_results");

    private final AgentStateStore stateStore;
    private final ReviewTools reviewTools;
    private final CodeReviewProperties properties;

    @Autowired
    public ReviewAgentFactory(AgentStateStore stateStore, ReviewTools reviewTools,
                              CodeReviewProperties properties) {
        this.stateStore = stateStore;
        this.reviewTools = reviewTools;
        this.properties = properties;
    }

    /** Kept for focused unit tests that exercise only LOCAL mode. */
    public ReviewAgentFactory(AgentStateStore stateStore, ReviewTools reviewTools) {
        this(stateStore, reviewTools, null);
    }

    public HarnessAgent reviewer(Model model, String sysPrompt, Path codeRoot, Path scratchWorkspace,
                                 List<MiddlewareBase> middlewares, int maxIters, ExecutionMode mode) {
        return build("code-reviewer", model, sysPrompt, codeRoot, scratchWorkspace, middlewares, maxIters, mode);
    }

    /** Compatibility overload for callers that predate repository execution modes. */
    public HarnessAgent reviewer(Model model, String sysPrompt, Path codeRoot, Path scratchWorkspace,
                                 List<MiddlewareBase> middlewares, int maxIters) {
        return reviewer(model, sysPrompt, codeRoot, scratchWorkspace, middlewares, maxIters, ExecutionMode.LOCAL);
    }

    public HarnessAgent investigator(Model model, String sysPrompt, Path codeRoot, Path scratchWorkspace,
                                     List<MiddlewareBase> middlewares, int maxIters, ExecutionMode mode) {
        return build("issue-investigator", model, sysPrompt, codeRoot, scratchWorkspace, middlewares, maxIters, mode);
    }

    private HarnessAgent build(String name, Model model, String sysPrompt, Path codeRoot, Path scratchWorkspace,
                               List<MiddlewareBase> middlewares, int maxIters, ExecutionMode mode) {
        try {
            Files.createDirectories(scratchWorkspace);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(reviewTools);
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(name)
                .sysPrompt(sysPrompt)
                .model(model)
                .toolkit(toolkit)
                .workspace(scratchWorkspace)
                .middlewares(middlewares)
                .stateStore(stateStore)
                .maxIters(maxIters);
        if (mode == ExecutionMode.SANDBOX) {
            builder.filesystem(dockerFilesystem(codeRoot));
        } else {
            CodeReviewProperties.Sandbox sandbox = properties == null ? null : properties.sandbox();
            LocalFilesystemSpec local = new LocalFilesystemSpec().mode(LocalFsMode.ROOTED).project(codeRoot)
                    .projectWritable(true);
            if (properties != null && sandbox != null) {
                local.executeTimeoutSeconds(sandbox.commandTimeoutSeconds()).maxOutputBytes(sandbox.maxOutputBytes());
            }
            builder.filesystem(local);
            // The two-argument constructor is retained for old isolated unit tests. Production
            // beans always receive properties and therefore expose the full LOCAL tool surface.
            if (properties == null) {
                ToolsConfig toolsConfig = new ToolsConfig();
                toolsConfig.setDeny(LEGACY_DENIED_TOOLS);
                builder.toolsConfig(toolsConfig)
                        .disableShellTool()
                        .disableSubagents()
                        .disableDynamicSubagents()
                        .disableMemoryTools()
                        .disableMemoryHooks()
                        .disableDynamicSkills()
                        .disableDefaultWorkspaceSkills()
                        .disableWorkspaceContext()
                        .disableTranscript();
            }
        }
        return builder.build();
    }

    private SandboxFilesystemSpec dockerFilesystem(Path codeRoot) {
        if (properties == null || properties.sandbox() == null) {
            throw new IllegalStateException("sandbox settings are not configured");
        }
        CodeReviewProperties.Sandbox cfg = properties.sandbox();
        WorkspaceSpec workspace = new WorkspaceSpec();
        workspace.setRoot(cfg.workspaceRoot());
        workspace.getEntries().put(".", new LocalDirEntry(codeRoot.toAbsolutePath().normalize().toString()));
        return new DockerFilesystemSpec()
                .workspaceRoot(cfg.workspaceRoot())
                .workspaceSpec(workspace)
                .image(cfg.image())
                .memorySizeBytes(cfg.memorySizeBytes())
                .cpuCount(cfg.cpuCount())
                .network(cfg.network())
                .additionalRunArgs("--cap-drop=ALL", "--security-opt=no-new-privileges")
                .workspaceProjectionEnabled(true);
    }

    /** A tool-less single-turn agent for the plan and filter phases. */
    public ReActAgent analyst(String name, Model model, String sysPrompt, List<MiddlewareBase> middlewares) {
        return ReActAgent.builder()
                .name(name)
                .sysPrompt(sysPrompt)
                .model(model)
                .middlewares(middlewares)
                .stateStore(stateStore)
                .maxIters(3)
                .build();
    }

}
