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
        int batchSize = Math.max(1, Math.min(cfg.batchSize(), 20));
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(texts.size(), i + batchSize));
            all.addAll(call(batch, cfg));
        }
        return all;
    }

    private List<float[]> call(List<String> texts, CodeReviewProperties.Embedding cfg) {
        try {
            // DashScope exposes the regional base URL through this SDK-wide setting.
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
                    .sorted(Comparator.comparing(item -> item.getTextIndex() == null ? 0 : item.getTextIndex()))
                    .toList();
            if (embeddings.size() != texts.size()) throw new IllegalStateException("embedding endpoint returned an unexpected number of vectors");
            List<float[]> result = new ArrayList<>();
            for (var item : embeddings) {
                if (item.getEmbedding() == null) throw new IllegalStateException("embedding response contains no vector");
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
