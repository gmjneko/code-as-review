package org.koaks.codereview.llm.domain;

import org.jspecify.annotations.NonNull;

/** A resolved, decrypted OpenAI-compatible endpoint. Never serialise this type. */
public record ModelEndpoint(String baseUrl, String apiKey, String modelName) {

    @Override
    public @NonNull String toString() {
        return "ModelEndpoint[baseUrl=" + baseUrl + ", modelName=" + modelName + "]";
    }

}
