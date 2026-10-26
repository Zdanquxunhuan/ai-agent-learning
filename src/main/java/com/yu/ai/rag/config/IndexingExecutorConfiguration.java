package com.yu.ai.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class IndexingExecutorConfiguration {

    @Bean(name = "indexingTaskExecutor")
    public ThreadPoolTaskExecutor indexingTaskExecutor(
            @Value("${rag.indexing.core-pool-size:2}")
            int corePoolSize,
            @Value("${rag.indexing.max-pool-size:2}")
            int maxPoolSize,
            @Value("${rag.indexing.queue-capacity:50}")
            int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("rag-index-");

        // 关闭应用时，尽量等待已提交的任务完成。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // 队列满时明确拒绝，不允许无限制创建线程。
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        return executor;
    }
}