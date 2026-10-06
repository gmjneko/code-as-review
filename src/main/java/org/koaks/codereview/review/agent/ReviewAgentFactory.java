package org.koaks.codereview.review.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.tools.ToolsConfig;
import io.agentscope.harness.agent.workspace.LocalFsMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds the agents of a review. The reviewer is a {@link HarnessAgent} whose filesystem
 * tools are jailed to the checkout: the project is mounted read-only (writes would land in the
 * scratch workspace), and every tool that writes, runs commands, reaches the network or spawns
 * agents is removed. Swapping {@link LocalFilesystemSpec} for a sandbox spec later keeps the
 * tool surface unchanged.
 */
@Component
@RequiredArgsConstructor
public class ReviewAgentFactory {

    static final List<String> DENIED_TOOLS = List.of(
            "write_file", "edit_file", "web_fetch", "web_search", "wait_async_results");

    private final AgentStateStore stateStore;
    private final ReviewTools reviewTools;

    public HarnessAgent reviewer(Model model, String sysPrompt, Path codeRoot, Path scratchWorkspace,
                                 List<MiddlewareBase> middlewares, int maxIters) {
        try {
            Files.createDirectories(scratchWorkspace);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(reviewTools);
        ToolsConfig toolsConfig = new ToolsConfig();
        toolsConfig.setDeny(DENIED_TOOLS);
        return HarnessAgent.builder()
                .name("code-reviewer")
                .sysPrompt(sysPrompt)
                .model(model)
                .toolkit(toolkit)
                .workspace(scratchWorkspace)
                .filesystem(new LocalFilesystemSpec().mode(LocalFsMode.SANDBOXED).project(codeRoot))
                .toolsConfig(toolsConfig)
                .disableShellTool()
                .disableSubagents()
                .disableDynamicSubagents()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableWorkspaceContext()
                .disableTranscript()
                .middlewares(middlewares)
                .stateStore(stateStore)
                .maxIters(maxIters)
                .build();
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
