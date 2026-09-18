package com.ff.mycloud.ai.agent;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties("ai.agent")
public class AgentProperties {
    @Min(1) @Max(64) private int maxConcurrentRuns = 8;
    @Min(10) @Max(900) private int timeoutSeconds = 300;
    @Min(1) @Max(8) private int maxSteps = 6;
    @Min(1) @Max(40) private int maxToolCalls = 20;
    @Min(1) @Max(40) private int maxModelCalls = 20;
    @Min(1024) @Max(65536) private int maxOutputTokens = 2048;
    @Min(4096) @Max(200000) private int maxContextChars = 48000;
    @Min(4096) @Max(200000) private int maxContextTokens = 24000;
    @Min(16000) @Max(2000000) private int maxEvidenceChars = 512000;
    @Min(1000) @Max(1000000) private int maxTotalTokens = 60000;
    @Min(1) @Max(60) private int actionExpiryMinutes = 15;
}
