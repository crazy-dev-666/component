package cn.dev666.component.request.log.config;

import cn.dev666.component.request.log.enums.LogRequestLevel;
import cn.dev666.component.request.log.enums.LogResponseLevel;
import cn.dev666.component.request.log.filter.LogFilter;
import org.apache.commons.io.output.TeeOutputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
@ConditionalOnClass(TeeOutputStream.class)
@EnableConfigurationProperties(RequestLogProperties.class)
public class RequestLogAutoConfiguration {

    @Resource
    RequestLogProperties properties;

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dev666.request.log", value = "enabled", havingValue = "true", matchIfMissing = true)
    public LogFilter logFilter() {
        LogRequestLevel requestLevel;
        LogResponseLevel responseLevel;
        switch (properties.getScene()){
            case DEV:
            case TEST:
                requestLevel = LogRequestLevel.URL_BODY_SOME_HEADER;
                responseLevel = LogResponseLevel.ALL;
                break;
            case ONLINE:
                requestLevel = LogRequestLevel.URL;
                responseLevel = LogResponseLevel.SLOW_ERROR_NOBODY;
                break;
            case CUSTOMER:
                requestLevel = properties.getRequestLevel();
                responseLevel = properties.getResponseLevel();
                if (requestLevel == null || responseLevel == null){
                    throw new IllegalArgumentException("当为自定义场景时，需手动指定请求、响应日志级别");
                }
                break;
            default:
                throw new IllegalArgumentException("不支持未知的场景");
        }
        return new LogFilter(properties.getOrder(), requestLevel, responseLevel,
                properties.getRequestOmitLength(), properties.getResponseOmitLength(),
                properties.getHeaders(), properties.getSlowRequestThreshold());
    }
}
