package com.genai.gitgpt.user.gemini;

import com.genai.gitgpt.exception.AppException;
import com.genai.gitgpt.exception.SecretRedactor;
import com.google.genai.Client;
import com.google.genai.types.EmbedContentConfig;
import org.springframework.stereotype.Component;

@Component
public class GeminiKeyVerifier {

    public void verify(String apiKey) {
        try {
            Client client = Client.builder().apiKey(apiKey).vertexAI(false).build();
            client.models.embedContent(
                    "gemini-embedding-001",
                    "ok",
                    EmbedContentConfig.builder().outputDimensionality(1536).build()
            );
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(
                    "That Gemini API key was rejected. Check it in Google AI Studio. "
                            + SecretRedactor.redact(ex.getMessage())
            );
        }
    }
}
