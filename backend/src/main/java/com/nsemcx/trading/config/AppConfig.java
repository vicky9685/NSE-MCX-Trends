package com.nsemcx.trading.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableConfigurationProperties({BrokerProperties.class, YahooFinanceProperties.class})
public class AppConfig {

    /**
     * OkHttpClient for external API calls (Yahoo Finance, broker APIs).
     * Connection pool of 10, all timeouts at 30 seconds.
     */
    @Bean
    public OkHttpClient okHttpClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(log::debug);
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .addInterceptor(loggingInterceptor)
                .build();
    }

    /**
     * Jackson ObjectMapper with JSR-310 (Java 8 date/time) module and SNAKE_CASE naming.
     * Marked @Primary so Spring uses this instance over the auto-configured one.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Platform-thread executor for @Async tasks.
     * corePoolSize=10, maxPoolSize=50, queueCapacity=500.
     */
    @Bean(name = "tradingExecutor")
    public ThreadPoolTaskExecutor tradingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("trading-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler((runnable, exec) ->
                log.error("Trading executor rejected task: {}", runnable.toString()));
        executor.initialize();
        return executor;
    }

    /**
     * Virtual-thread executor using Java 21 Thread.ofVirtual().factory().
     * Ideal for high-concurrency I/O-bound tasks (Yahoo Finance fetches, AI calls).
     */
    @Bean(name = "virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                      .name("vt-trading-", 0)
                      .factory()
        );
    }

    /**
     * Async executor alias - points to the platform-thread pool by default.
     * Override with @Qualifier("virtualThreadExecutor") for virtual-thread tasks.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        return tradingExecutor();
    }
}
