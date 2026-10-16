package org.koaks.codereview.rag.service;

import java.util.List;

public interface EmbeddingClient {
    List<float[]> embed(List<String> texts);
}
