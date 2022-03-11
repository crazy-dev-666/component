package cn.dev666.component.error.notice.config;

import cn.dev666.component.error.notice.event.OsResourceMonitor;
import cn.dev666.component.error.notice.listener.ErrorEventListener;
import cn.dev666.component.error.notice.mail.MailService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(ErrorNoticeProperties.class)
@ConditionalOnProperty(prefix = "error.notice", value = "enabled", havingValue = "true", matchIfMissing = true)
public class ErrorNoticeAutoConfiguration {

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${spring.application.name}")
    private String applicationName;

    @Resource
    private Environment environment;

    @Resource
    private ErrorNoticeProperties properties;

    @Bean("exceptionEventExecutor")
    public ThreadPoolTaskExecutor exceptionEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadGroupName("async-ex-pool");
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("async-ex-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        return executor;
    }

    @Bean("exceptionEventScheduler")
    public ThreadPoolTaskScheduler exceptionEventScheduler() {
        ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
        executor.setThreadGroupName("cron-ex-pool");
        executor.setPoolSize(1);
        executor.setThreadNamePrefix("cron-ex-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        return executor;
    }


    @Bean
    public MailService mailService(JavaMailSender mailSender){
        return new MailService(mailSender, mailFrom);
    }

    @Bean
    public ErrorEventListener exceptionListener(@Qualifier("exceptionEventExecutor") ThreadPoolTaskExecutor executor,
                                                MailService mailService){
        return new ErrorEventListener(executor, properties, Arrays.asList(environment.getActiveProfiles()), mailService, applicationName);
    }

    @Bean
    @ConditionalOnProperty(prefix = "error.notice.jvmMonitor", value = "enabled", havingValue = "true", matchIfMissing = true)
    public OsResourceMonitor jvmMonitor(@Qualifier("exceptionEventScheduler") ThreadPoolTaskScheduler scheduler,
                                        ErrorEventListener errorEventListener){
        return new OsResourceMonitor(scheduler, errorEventListener, properties.getOs());
    }

}