package com.substring.agent.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

// Wraps a tool to log what the model called it with, what it returned and how long it took
public record LoggingToolCallback(ToolCallback delegate) implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(LoggingToolCallback.class);
    private static final int MAX_RESULT_CHARS_INFO = 500;

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return logged(toolInput, () -> delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return logged(toolInput, () -> delegate.call(toolInput, toolContext));
    }

    private String logged(String toolInput, java.util.function.Supplier<String> call) {
        String name = delegate.getToolDefinition().name();
        log.info("Tool {} called with {}", name, toolInput);
        long start = System.nanoTime();
        try {
            String result = call.get();
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("Tool {} returned in {} ms, {} chars: {}", name, ms, result.length(),
                    ApiLogging.truncate(result, MAX_RESULT_CHARS_INFO));
            log.debug("Tool {} full result: {}", name, result);
            return result;
        } catch (RuntimeException e) {
            log.error("Tool {} failed after {} ms", name, (System.nanoTime() - start) / 1_000_000, e);
            throw e;
        }
    }
}
