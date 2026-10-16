package org.koaks.codereview.rag.service;

import lombok.RequiredArgsConstructor;
import org.koaks.codereview.config.CodeReviewProperties;
import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
/**
 * 基于阿里 DashScope Java SDK 的 Qwen embedding 客户端。
 *
 * <p>Qwen 接口一次最多接收 20 条文本，所以这里把较大的输入拆成批次，并按接口返回的
 * textIndex 排序后拼回原顺序。apiKey 只从配置注入，实际值由环境变量提供，代码和配置文件
 * 都不保存密钥。</p>
 */
public class QwenEmbeddingClient implements EmbeddingClient {
    private final CodeReviewProperties properties;

    @Override
    public synchronized List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        CodeReviewProperties.Embedding cfg = properties.rag().embedding();
        if (!StringUtils.hasText(cfg.baseUrl()) || !StringUtils.hasText(cfg.apiKey())) {
            throw new IllegalStateException("RAG embedding endpoint and API key are not configured");
        }
        List<float[]> all = new ArrayList<>();
        // DashScope 的批量上限是 20；配置值仍允许调小，以便控制单次请求体大小。
        int batchSize = Math.max(1, Math.min(cfg.batchSize(), 20));
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(texts.size(), i + batchSize));
            all.addAll(call(batch, cfg));
        }
        return all;
    }

    private List<float[]> call(List<String> texts, CodeReviewProperties.Embedding cfg) {
        try {
            // DashScope SDK 通过全局变量设置地域 endpoint；外层 synchronized 避免并发请求互相覆盖。
            Constants.baseHttpApiUrl = cfg.baseUrl().replaceAll("/$", "");
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(cfg.model())
                    .texts(texts)
                    .dimension(cfg.dimension())
                    .outputType(TextEmbeddingParam.OutputType.DENSE)
                    .build();
            TextEmbeddingResult response = new TextEmbedding(cfg.apiKey()).call(param);
            if (response == null || response.getStatusCode() == null || response.getStatusCode() >= 300
                    || response.getOutput() == null || response.getOutput().getEmbeddings() == null) {
                throw new IllegalStateException("embedding endpoint failed: " + (response == null ? "empty response" : response.getMessage()));
            }
            var embeddings = response.getOutput().getEmbeddings().stream()
                    // API 返回的数组不把顺序当作稳定契约，因此显式按 textIndex 还原输入顺序。
                    .sorted(Comparator.comparing(item -> item.getTextIndex() == null ? 0 : item.getTextIndex()))
                    .toList();
            if (embeddings.size() != texts.size())
                throw new IllegalStateException("embedding endpoint returned an unexpected number of vectors");
            List<float[]> result = new ArrayList<>();
            for (var item : embeddings) {
                if (item.getEmbedding() == null)
                    throw new IllegalStateException("embedding response contains no vector");
                float[] row = new float[item.getEmbedding().size()];
                for (int i = 0; i < row.length; i++) row[i] = item.getEmbedding().get(i).floatValue();
                result.add(row);
            }
            return result;
        } catch (ApiException | NoApiKeyException e) {
            throw new IllegalStateException("embedding request failed: " + e.getMessage(), e);
        }
    }
}
