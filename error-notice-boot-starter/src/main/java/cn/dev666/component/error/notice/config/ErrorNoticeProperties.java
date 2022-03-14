package cn.dev666.component.error.notice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;

@Data
@ConfigurationProperties("error.notice")
public class ErrorNoticeProperties {

    private boolean enabled = true;

    /**
     * 发送间隔
     */
    @DurationUnit(ChronoUnit.MINUTES)
    private Duration interval = Duration.ofMinutes(60);
    /**
     * 发送阈值，当间隔时间内，次数突破阈值时，再次发送。
     */
    private Integer threshold = 5;

    /**
     *  不发送提醒的环境集合
     */
    private Set<String> ignoreProfiles = Collections.emptySet();

    /**
     * 系统资源监控配置
     */
    private OsResourceProperties os;

    /**
     * JVM资源监控配置
     */
    private JvmResourceProperties jvm;

    /**
     * 警告日志通知方式配置，默认不启用
     */
    private boolean warnLog = false;

    /**
     * 邮件通知方式配置
     */
    private EmailProperties email;

    /**
     * 企业微信通知方式配置
     */
    private WorkWxProperties workWx;

    /**
     * 钉钉通知方式配置
     */
    private DingDingProperties dingDing;

    /**
     * 公众号通知方式配置
     */
    private WxProperties wx;

    @Data
    public static class EmailProperties {

        /**
         * 发送人列表
         */
        private String[] to;
        /**
         * 抄送人列表
         */
        private String[] cc;
    }

    @Data
    public static class WorkWxProperties {
    }

    @Data
    public static class DingDingProperties {
    }

    @Data
    public static class WxProperties {
    }

    @Data
    public static class JvmResourceProperties {

        private boolean enabled = true;
        /**
         * 监控频率，默认1分钟一次
         */
        private Duration frequency = Duration.ofMinutes(1);

        /**
         *  进程占用总CPU资源报警阈值比率，小于等于0不报警，默认 0.6，即jvm进程占用总CPU资源达到60%
         */
        private double cpuLoadNoticeRate = 0.6;

        /**
         * 连续出现CPU资源达到报警阈值的次数时进行报警，默认 3次
         */
        private int cpuLoadNoticeFrequency = 3;

        /**
         * 线程数报警阈值，小于等于0不报警，默认2000
         */
        private int threadNoticeCount = 2000;
        /**
         *  内存池使用率报警阈值比率，小于等于0不报警，默认 0.95，即内存池占用达到95%
         */
        private double memoryUsedNoticeRate = 0.95;
        /**
         * 连续出现内存池使用率达到报警阈值的次数时进行报警，默认 3次
         */
        private int memoryUsedNoticeFrequency = 3;
        /**
         * 两次监控之间，出现 Full GC 次数达到此报警阈值进行报警，默认 5次
         */
        private int fullGcNoticeCount = 5;
    }

    @Data
    public static class OsResourceProperties {

        private boolean enabled = true;
        /**
         *  监控频率，默认1分钟一次
         */
        private Duration frequency = Duration.ofMinutes(1);

        /**
         *  CPU负载报警阈值比率，小于等于0不报警
         *
         *  默认 0.7，即1分钟、5分钟、15分钟负载都达到 CPU数 * 0.7时，进行报警
         */
        private double cpuLoadAverageNoticeRate = 0.7;

        /**
         *  CPU使用率报警阈值比率，小于等于0不报警，默认 0.9
         */
        private double cpuUsedNoticeRate = 0.9;

        /**
         *  内存使用率报警阈值比率，小于等于0不报警，默认 0.9
         */
        private double memoryUsedNoticeRate = 0.9;
        /**
         *  交换分区使用率报警阈值比率，小于等于0不报警
         *
         *  默认最小正数，即只要使用就报警
         */
        private double swapUsedNoticeRate = Double.MIN_VALUE;
        /**
         *  单块磁盘使用率报警阈值比率，小于等于0不报警，默认 0.9
         */
        private double diskUsedNoticeRate = 0.9;
        /**
         *  文件描述符使用率报警阈值比率，小于等于0不报警，默认 0.9
         */
        private double fileDescriptorsUsedNoticeRate = 0.9;
    }
}
