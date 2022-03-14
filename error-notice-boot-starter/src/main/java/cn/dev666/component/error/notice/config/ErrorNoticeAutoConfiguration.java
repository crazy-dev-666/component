package cn.dev666.component.error.notice.config;

import cn.dev666.component.error.notice.channel.DefaultMailChannel;
import cn.dev666.component.error.notice.enums.JvmType;
import cn.dev666.component.error.notice.event.JvmResourceMonitor;
import cn.dev666.component.error.notice.event.OsResourceMonitor;
import cn.dev666.component.error.notice.listener.ErrorEventListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
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
    @ConditionalOnBean(JavaMailSender.class)
    @ConditionalOnProperty(value = "error.notice.email.to")
    public DefaultMailChannel mailChannel(JavaMailSender mailSender){
        return new DefaultMailChannel(mailSender, mailFrom, properties.getEmail());
    }

    @Bean
    public ErrorEventListener exceptionListener(@Qualifier("exceptionEventExecutor") ThreadPoolTaskExecutor executor){
        return new ErrorEventListener(executor, properties, applicationName);
    }

    @Bean
    @ConditionalOnProperty(prefix = "error.notice.osMonitor", value = "enabled", havingValue = "true", matchIfMissing = true)
    public OsResourceMonitor osMonitor(@Qualifier("exceptionEventScheduler") ThreadPoolTaskScheduler scheduler,
                                        ErrorEventListener errorEventListener){
        return new OsResourceMonitor(scheduler, errorEventListener, properties.getOs());
    }

    @Bean
    @ConditionalOnProperty(prefix = "error.notice.jvmMonitor", value = "enabled", havingValue = "true", matchIfMissing = true)
    public JvmResourceMonitor jvmMonitor(@Qualifier("exceptionEventScheduler") ThreadPoolTaskScheduler scheduler,
                                         ErrorEventListener errorEventListener){

        RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        String vmName = bean.getVmName();
        JvmType type = JvmType.UNSUPPORTED;
        if (StringUtils.hasText(vmName)){
            if (vmName.toLowerCase().contains("OpenJDK")){
                type = JvmType.OPEN_JDK;
            }else if (vmName.toLowerCase().contains("OpenJ9")){
                type = JvmType.Open_J9;
            }else if (vmName.toLowerCase().contains("Java HotSpot")){
                type = JvmType.ORACLE;
            }
        }
        return new JvmResourceMonitor(scheduler, errorEventListener, properties.getJvm(), type);
    }



}