package com.genai.gitgpt.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gitgpt.ask")
public class AskProperties {

    private int vectorTopK = 12;
    private int keywordLimit = 40;
    private int packedChunks = 5;
    private int maxContextTokens = 2500;
    private int maxQuestionChars = 2000;
    private int maxHistoryMessages = 8;
    private int maxHistoryChars = 400;
}
