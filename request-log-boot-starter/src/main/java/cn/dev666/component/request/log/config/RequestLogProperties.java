package cn.dev666.component.request.log.config;

import cn.dev666.component.request.log.enums.LogRequestLevel;
import cn.dev666.component.request.log.enums.LogResponseLevel;
import cn.dev666.component.request.log.enums.LogScene;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "dev666.request.log")
public class RequestLogProperties {
    /**
     * 是否启用记录日志功能
     */
    private Boolean enabled = true;
    /**
     * 过滤器排序，默认 -10
     */
    private Integer order = -10;
    /**
     * 日志场景，默认开发场景，当为 CUSTOMER 时，需手动指定请求、响应日志级别
     */
    private LogScene scene = LogScene.DEV;
    /**
     * 请求日志级别，默认 根据日志场景决定
     */
    private LogRequestLevel requestLevel;
    /**
     * 当输出请求体时，长度超出阈值时，截取输出，默认4KB
     */
    @DataSizeUnit(DataUnit.KILOBYTES)
    private DataSize requestOmitLength = DataSize.ofKilobytes(4);
    /**
     * 输出的部分请求头，默认不输出
     */
    private Set<String> headers;
    /**
     * 响应日志级别，默认 根据日志场景决定
     */
    private LogResponseLevel responseLevel;
    /**
     * 当输出响应体时，长度超出阈值时，截取输出，默认8KB
     */
    @DataSizeUnit(DataUnit.KILOBYTES)
    private DataSize responseOmitLength = DataSize.ofKilobytes(8);
    /**
     * 慢请求阈值，请求耗时大于此值为慢请求，默认3秒
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration slowRequestThreshold = Duration.ofSeconds(3);
}
