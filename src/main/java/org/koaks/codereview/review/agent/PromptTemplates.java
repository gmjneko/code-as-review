package org.koaks.codereview.review.agent;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Markdown prompt templates from {@code classpath:prompts/} with {@code {{name}}} placeholders. */
@Component
public class PromptTemplates {

    public static final String MAIN_SYSTEM = "main_system";
    public static final String MAIN_USER = "main_user";
    public static final String PLAN_SYSTEM = "plan_system";
    public static final String PLAN_USER = "plan_user";
    public static final String FILTER_SYSTEM = "filter_system";
    public static final String FILTER_USER = "filter_user";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z_]+)}}");

    private final Map<String, String> templates = new HashMap<>();

    public PromptTemplates() {
        for (String name : List.of(MAIN_SYSTEM, MAIN_USER, PLAN_SYSTEM, PLAN_USER, FILTER_SYSTEM, FILTER_USER)) {
            templates.put(name, load("prompts/" + name + ".md"));
        }
    }

    public String get(String name) {
        return templates.get(name);
    }

    /** Substitutes in one pass so values that themselves contain {@code {{...}}} are left verbatim. */
    public String render(String name, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(templates.get(name));
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = vars.getOrDefault(m.group(1), "");
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String load(String resource) {
        try (InputStream in = PromptTemplates.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
