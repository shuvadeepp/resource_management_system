package tech.csm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Background matching runs (POST /api/requirements?async=true) isi chhote
 * pool par chalte hain. Pool chhota rakha hai: Gemini free tier par ek saath
 * zyada calls ka koi fayda nahi.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "matchingExecutor")
    public ThreadPoolTaskExecutor matchingExecutor(@Value("${app.matching.async-pool-size:2}") int poolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("match-");
        executor.initialize();
        return executor;
    }

    // audit_logs writes request thread se hata do (har write ~250ms ki cloud DB round trip hai).
    // Ek thread: rows usi order me likhi jaati hain jisme events hue.
    @Bean(name = "auditExecutor")
    public ThreadPoolTaskExecutor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        executor.initialize();
        return executor;
    }
}
