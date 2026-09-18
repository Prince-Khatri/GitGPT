package com.genai.gitgpt.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gitgpt.index")
public class IndexProperties {

    private int maxFiles = 400;
    private int maxFileBytes = 256 * 1024;
    private int maxTotalBytes = 8 * 1024 * 1024;
    private int embedBatchSize = 32;
    private int chunkLines = 100;
    private int chunkOverlapLines = 20;
    private int embedMaxAttempts = 4;
}
