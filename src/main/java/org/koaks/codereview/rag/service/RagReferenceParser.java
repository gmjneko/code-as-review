package org.koaks.codereview.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 GitHub 文本中的显式 Issue/PR 引用。
 *
 * <p>只返回当前仓库的引用，跨仓库 owner/repo#12 和完整 URL 会被过滤。显式引用会绕过
 * 相似度阈值直接建立关系，但仍然会进入索引，便于后续混合检索合并证据。</p>
 */
public final class RagReferenceParser {
    private static final Pattern FULL_URL = Pattern.compile("https?://github\\.com/([^/\\s]+)/([^/#\\s]+)/(?:issues|pull)/(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OWNER_REF = Pattern.compile("(?<![\\w/])([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)#(\\d+)");
    private static final Pattern LOCAL_REF = Pattern.compile("(?<![\\w/])#(\\d+)");

    private RagReferenceParser() {}

    public static List<Reference> parse(String text, String repositoryFullName) {
        if (text == null || text.isBlank()) return List.of();
        Set<Reference> references = new LinkedHashSet<>();
        // 先解析信息更完整的 URL/owner-repo 形式，再解析当前仓库的简写 #12。
        add(text, FULL_URL.matcher(text), repositoryFullName, true, references);
        add(text, OWNER_REF.matcher(text), repositoryFullName, false, references);
        Matcher local = LOCAL_REF.matcher(text);
        while (local.find()) references.add(new Reference(repositoryFullName, local.group(1), local.group(0)));
        var unique = new java.util.LinkedHashMap<String, Reference>();
        // 同一引用可能同时被多个正则命中，按仓库（忽略大小写）+编号做最终去重。
        references.forEach(r -> unique.putIfAbsent(r.repositoryFullName().toLowerCase() + ":" + r.number(), r));
        return new ArrayList<>(unique.values());
    }

    private static void add(String text, Matcher matcher, String repository, boolean url, Set<Reference> out) {
        while (matcher.find()) {
            String ownerRepo = url ? matcher.group(1) + "/" + matcher.group(2) : matcher.group(1);
            if (repository.equalsIgnoreCase(ownerRepo)) out.add(new Reference(ownerRepo, url ? matcher.group(3) : matcher.group(2), matcher.group(0)));
        }
    }

    public record Reference(String repositoryFullName, String number, String matchedText) {}
}
