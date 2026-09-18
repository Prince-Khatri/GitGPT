package com.genai.gitgpt.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gitgpt.ask")
public class AskProperties {

    private int vectorTopK = 20;
    private int keywordLimit = 40;
    private int packedChunks = 8;
    private int maxContextTokens = 4000;
    private int maxQuestionChars = 2000;
}
