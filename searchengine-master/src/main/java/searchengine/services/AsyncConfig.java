package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync

@Configuration

@Slf4j

public class AsyncConfig {

    @Bean(name = "taskExecutor")

    public Executor getAsyncExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int coreSize = Runtime.getRuntime().availableProcessors();

        executor.setCorePoolSize(coreSize);

        executor.setMaxPoolSize(coreSize * 2);

        executor.setQueueCapacity(50);

        executor.setThreadNamePrefix("AsyncThread-");

        executor.initialize();

        return executor;

    }

}