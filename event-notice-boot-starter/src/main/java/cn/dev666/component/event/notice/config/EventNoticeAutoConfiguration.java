package cn.dev666.component.event.notice.config;

import cn.dev666.component.event.notice.channel.DefaultDingDingChannel;
import cn.dev666.component.event.notice.channel.DefaultMailChannel;
import cn.dev666.component.event.notice.channel.WarnLogChannel;
import cn.dev666.component.event.notice.enums.JvmType;
import cn.dev666.component.event.notice.listener.NoticeEventListener;
import cn.dev666.component.event.notice.monitor.JvmResourceMonitor;
import cn.dev666.component.event.notice.monitor.OsResourceMonitor;
import cn.dev666.component.event.notice.utils.DataUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(EventNoticeProperties.class)
@ConditionalOnProperty(prefix = "dev666.event.notice", value = "enabled", havingValue = "true", matchIfMissing = true)
public class EventNoticeAutoConfiguration {

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${spring.application.name}")
    private String applicationName;

    @Resource
    private Environment environment;

    @Resource
    private EventNoticeProperties properties;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

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
        executor.setPoolSize(10);
        executor.setThreadNamePrefix("cron-ex-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        return executor;
    }


    @Bean
    @ConditionalOnProperty(prefix = "dev666.event.notice.email", value = "enabled", havingValue = "true")
    public DefaultMailChannel mailChannel(JavaMailSender mailSender){
        return new DefaultMailChannel(mailSender, mailFrom, properties.getEmail());
    }

    @Bean
    @ConditionalOnProperty(prefix = "dev666.event.notice.ding-ding", value = "enabled", havingValue = "true")
    public DefaultDingDingChannel DingDingChannel(){
        if (objectMapper == null){
            objectMapper = new ObjectMapper();
        }
        return new DefaultDingDingChannel(properties.getDingDing(), objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "dev666.event.notice", value = "warn-log", havingValue = "true")
    public WarnLogChannel warnLogChannel(){
        return new WarnLogChannel();
    }

    @Bean
    public NoticeEventListener exceptionListener(@Qualifier("exceptionEventExecutor") ThreadPoolTaskExecutor executor,
                                                 @Qualifier("exceptionEventScheduler") ThreadPoolTaskScheduler scheduler){
        String[] profiles = environment.getActiveProfiles();
        if (profiles == null || profiles.length == 0){
            profiles = environment.getDefaultProfiles();
        }
        DataUtils.init(Arrays.toString(profiles), applicationName);
        return new NoticeEventListener(executor, scheduler, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "dev666.event.notice.os", value = "enabled", havingValue = "true", matchIfMissing = true)
    public OsResourceMonitor osMonitor(@Qualifier("exceptionEventScheduler") ThreadPoolTaskScheduler scheduler,
                                        NoticeEventListener noticeEventListener){
        return new OsResourceMonitor(scheduler, noticeEventListener, properties.getOs());
    }

    @Bean
    @ConditionalOnProperty(prefix = "dev666.event.notice.jvm", value = "enabled", havingValue = "true", matchIfMissing = true)
    public JvmResourceMonitor jvmMonitor(@Qualifier("exceptionEventScheduler") ThreadPoolTaskScheduler scheduler,
                                         NoticeEventListener noticeEventListener){

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
        return new JvmResourceMonitor(scheduler, noticeEventListener, properties.getJvm(), type);
    }



}