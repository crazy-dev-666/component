package cn.dev666.component.error.email.notice.config;

import cn.dev666.component.error.email.notice.listener.ExceptionListener;
import cn.dev666.component.error.email.notice.mail.MailService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@ConditionalOnBean(MailSender.class)
@EnableConfigurationProperties(ExceptionMailProperties.class)
public class ErrorEmailNoticeAutoConfiguration {

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${spring.application.name}")
    private String applicationName;

    @Resource
    private Environment environment;

    @Resource
    private ExceptionMailProperties properties;

    @Bean("exceptionEventExecutor")
    public ThreadPoolTaskExecutor exceptionEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadGroupName("async-exception");
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("async-ex-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        return executor;
    }

    @Bean
    @ConditionalOnBean(MailSender.class)
    public MailService mailService(MailSender mailSender){
        return new MailService(mailSender, Arrays.toString(environment.getActiveProfiles()), mailFrom, applicationName);
    }

    @Bean
    public ExceptionListener exceptionListener(@Qualifier("exceptionEventExecutor") ThreadPoolTaskExecutor executor,
                                               MailService mailService){
        return new ExceptionListener(executor, properties, Arrays.asList(environment.getActiveProfiles()), mailService);
    }
}