package cn.dev666.component.event.notice.channel;

import cn.dev666.component.event.notice.config.EventNoticeProperties;
import cn.dev666.component.event.notice.event.DefaultNoticeEvent;
import cn.dev666.component.event.notice.utils.HttpClientUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Template;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 官方文档 https://open.dingtalk.com/document/group/custom-robot-access
 */
@Slf4j
@AllArgsConstructor
public class DefaultDingDingChannel extends AbstractAggregationChannel<DefaultNoticeEvent> {

    private final EventNoticeProperties.DingDingProperties properties;

    private final ObjectMapper objectMapper;

    @Override
    public Template getMultipleTemplate() throws Exception {
        return configuration.getTemplate("multipleEventDingDing.ftl");
    }

    @Override
    public Template getSingleTemplate() throws Exception {
        return configuration.getTemplate("singleEventDingDing.ftl");
    }

    @Override
    public boolean sendNotice(String title, String content) throws IOException {
        String noticeUrl = properties.getNoticeUrl();
        if (!StringUtils.hasText(noticeUrl)) {
            log.debug("{} 钉钉消息发送失败，没有配置消息通知URL", title);
            return false;
        }

        At at = new At(properties.getAtMobiles(), properties.getAtUserIds(), properties.isAtAll());
        RequestData data = new RequestData(new Markdown(title, content), at);
        String result = HttpClientUtils.postJson(noticeUrl, objectMapper.writeValueAsString(data));
        Result value = objectMapper.readValue(result, Result.class);
        if (value == null || value.errcode == null || value.errcode != 0){
            return false;
        }
        return true;
    }


    @Getter
    @Setter
    protected static class Result {
        private Integer errcode;
        private String errmsg;
    }


    @Getter
    protected static class RequestData {
        private String msgtype = "markdown";
        private Markdown markdown;
        private At at;

        public RequestData(Markdown markdown, At at) {
            this.markdown = markdown;
            this.at = at;
        }
    }

    @Getter
    @AllArgsConstructor
    protected static class Markdown {
        private String title;
        private String text;
    }

    @Getter
    @AllArgsConstructor
    protected static class At {
        private String[] atMobiles;
        private String[] atUserIds;
        private boolean isAtAll;
    }
}
