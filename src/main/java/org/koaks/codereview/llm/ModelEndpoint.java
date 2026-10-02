package org.koaks.codereview.llm;

/** A resolved, decrypted OpenAI-compatible endpoint. Never serialise this type. */
public record ModelEndpoint(String baseUrl, String apiKey, String modelName) {

    @Override
    public String toString() {
        return "ModelEndpoint[baseUrl=" + baseUrl + ", modelName=" + modelName + "]";
    }
}
