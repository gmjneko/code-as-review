package org.koaks.codereview.review.agent;

import org.koaks.codereview.review.diff.FileDiff;
import org.koaks.codereview.review.diff.GlobMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Review checklist per file type; the first matching pattern wins, otherwise {@code default.md}. */
@Component
public class RuleCatalog {

    private static final List<Map.Entry<String, String>> PATH_RULES = List.of(
            Map.entry("**/*.properties", "properties"),
            Map.entry("**/*{mapper,dao,Mapper,Dao}*.xml", "mapper_dao_xml"),
            Map.entry("**/pom.xml", "pom_xml"),
            Map.entry("**/*.{json,json5}", "json"),
            Map.entry("**/*.{yaml,yml}", "yaml"),
            Map.entry("**/*.java", "java"),
            Map.entry("**/*.go", "go"),
            Map.entry("**/*.{ts,js,tsx,jsx,mjs,cjs}", "ts_js_tsx_jsx"),
            Map.entry("**/*.{kt,kts}", "kotlin"),
            Map.entry("**/*.rs", "rust"),
            Map.entry("**/*.{cpp,cc,cxx,hpp,hxx}", "cpp"),
            Map.entry("**/*.c", "c"),
            Map.entry("**/*.{py,pyi,ipynb}", "python"));

    private final List<Map.Entry<GlobMatcher, String>> matchers = new ArrayList<>();
    private final Map<String, String> ruleTexts = new LinkedHashMap<>();

    public RuleCatalog() {
        for (Map.Entry<String, String> e : PATH_RULES) {
            matchers.add(Map.entry(GlobMatcher.of(List.of(e.getKey()), false), e.getValue()));
            ruleTexts.computeIfAbsent(e.getValue(), RuleCatalog::load);
        }
        ruleTexts.put("default", load("default"));
    }

    public String ruleFor(String path) {
        for (Map.Entry<GlobMatcher, String> m : matchers) {
            if (m.getKey().matches(path)) {
                return ruleTexts.get(m.getValue());
            }
        }
        return ruleTexts.get("default");
    }

    /**
     * One checklist for the whole change; when files need different checklists each block is tagged
     * with the files it governs so the model can tell which applies where.
     */
    public String ruleFor(List<FileDiff> files) {
        Map<String, List<String>> byRule = new LinkedHashMap<>();
        for (FileDiff f : files) {
            byRule.computeIfAbsent(ruleFor(f.path()), k -> new ArrayList<>()).add(f.path());
        }
        if (byRule.size() == 1) {
            return byRule.keySet().iterator().next().strip();
        }
        StringBuilder sb = new StringBuilder();
        byRule.forEach((rule, paths) -> {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("<rules for=\"").append(String.join(", ", paths)).append("\">\n")
                    .append(rule.strip()).append("\n</rules>");
        });
        return sb.toString();
    }

    private static String load(String name) {
        return PromptTemplates.load("review/rules/" + name + ".md");
    }
}
