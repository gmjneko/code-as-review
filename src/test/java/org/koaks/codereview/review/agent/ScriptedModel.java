package org.koaks.codereview.review.agent;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Test model that answers each request from a script keyed on the request's messages. */
class ScriptedModel implements Model {

    record Request(List<Msg> messages, List<String> toolNames) {

        String allText() {
            StringBuilder sb = new StringBuilder();
            messages.forEach(m -> sb.append(m.getTextContent()).append('\n'));
            return sb.toString();
        }

        List<ToolResultBlock> toolResults() {
            return messages.stream().flatMap(m -> m.getContent().stream())
                    .filter(ToolResultBlock.class::isInstance).map(ToolResultBlock.class::cast).toList();
        }

        String lastUserText() {
            for (int i = messages.size() - 1; i >= 0; i--) {
                Msg m = messages.get(i);
                if (m.getRole() == io.agentscope.core.message.MsgRole.USER && !m.getTextContent().isBlank()) {
                    return m.getTextContent();
                }
            }
            return "";
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Function<Request, ContentBlock> script;

    ScriptedModel(Function<Request, ContentBlock> script) {
        this.script = script;
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        Request request = new Request(List.copyOf(messages),
                tools == null ? List.of() : tools.stream().map(ToolSchema::getName).toList());
        requests.add(request);
        return Flux.just(ChatResponse.builder()
                .id(UUID.randomUUID().toString())
                .content(List.of(script.apply(request)))
                .usage(new ChatUsage(100, 10, 0))
                .build());
    }

    @Override
    public String getModelName() {
        return "scripted";
    }

    static ContentBlock text(String text) {
        return TextBlock.builder().text(text).build();
    }

    static ContentBlock tool(String name, Map<String, Object> input) {
        return ToolUseBlock.builder().id("call-" + UUID.randomUUID()).name(name).input(input)
                .content(JSON.writeValueAsString(input)).build();
    }
}
