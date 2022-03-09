package cn.dev666.component.error.email.notice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Data
@ConfigurationProperties("exception.mail")
public class ExceptionMailProperties {
    /**
     * 异常及发送人列表
     */
    private Map<String, String[]> to;
    /**
     * 抄送人列表
     */
    private String[] cc;
    /**
     * 邮件发送间隔
     */
    @DurationUnit(ChronoUnit.MINUTES)
    private Duration interval = Duration.ofMinutes(60);
    /**
     * 邮件发送阈值，当间隔时间内，异常次数突破阈值时，再次发送邮件。
     */
    private Integer threshold = 5;

    /**
     *  不发送异常提醒的环境集合
     */
    private Set<String> ignoreProfiles = Collections.emptySet();
}
