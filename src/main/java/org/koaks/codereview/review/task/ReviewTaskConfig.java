package org.koaks.codereview.review.task;

import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.review.diff.FileSelector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReviewTaskConfig {

    public static final String EXECUTOR = "reviewTaskExecutor";

    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor reviewTaskExecutor(CodeReviewProperties properties) {
        CodeReviewProperties.Executor cfg = properties.executor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.taskConcurrency());
        executor.setMaxPoolSize(cfg.taskConcurrency());
        executor.setQueueCapacity(cfg.queueCapacity());
        executor.setThreadNamePrefix("review-task-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }

    @Bean
    public FileSelector fileSelector(CodeReviewProperties properties) {
        return FileSelector.fromClasspath(properties.review().maxFileDiffTokens());
    }
}
