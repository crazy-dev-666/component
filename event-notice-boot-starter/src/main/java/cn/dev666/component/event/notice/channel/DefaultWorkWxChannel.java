package cn.dev666.component.event.notice.channel;

import cn.dev666.component.event.notice.config.EventNoticeProperties;
import cn.dev666.component.event.notice.event.DefaultNoticeEvent;
import cn.dev666.component.event.notice.utils.DataUtils;
import cn.dev666.component.event.notice.utils.HttpClientUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

/**
 * 官方文档 https://developer.work.weixin.qq.com/document/path/91770
 */
@Slf4j
@AllArgsConstructor
public class DefaultWorkWxChannel implements AggregationChannel<DefaultNoticeEvent> {

    private final EventNoticeProperties.WorkWxProperties properties;

    private final ObjectMapper objectMapper;

    @Override
    public boolean notice(DefaultNoticeEvent event) throws IOException {
        String content = "### " + event.getTitle() + " \n\n " + event.getContentWithApplcationInfo();
        content = content.replaceAll("\\n", "\n> ");
        return sendNotice(event.getTitle(), content);
    }

    @Override
    public boolean notice(List<DefaultNoticeEvent> list) throws IOException {

        if (list.size() == 1){
            return notice(list.get(0));
        }

        int num = 0;
        StringBuilder sb = new StringBuilder();
        for (DefaultNoticeEvent event : list) {
            num++;
            sb.append(" \n ------------------------------------- \n ")
                    .append(event.getTitle()).append(" \n ")
                    .append(event.getContent());
        }

        String title = DataUtils.getProfiles() + "环境，<font color=\"warning\">" + num + "</font>条报警信息聚合";
        String content = "## " + title + " \n " + DataUtils.getApplicationInfo() + sb;
        content = content.replaceAll("\\n", "\n> ");
        return sendNotice(title, content);
    }

    protected boolean sendNotice(String title, String content) throws IOException {
        String noticeUrl = properties.getNoticeUrl();
        if (!StringUtils.hasText(noticeUrl)) {
            log.debug("{} 企业微信消息发送失败，没有配置消息通知URL", title);
            return false;
        }
        if (properties.isAtAll()){
            content += " <@all>";
        }else {
            if (properties.getAtUserIds() != null && properties.getAtUserIds().length > 0) {
                StringBuilder at = new StringBuilder();
                for (String userId : properties.getAtUserIds()) {
                    at.append(" <@").append(userId).append(">");
                }
                content += at;
            }
        }
        RequestData data = new RequestData(new Markdown(content));
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

        public RequestData(Markdown markdown) {
            this.markdown = markdown;
        }
    }

    @Getter
    @AllArgsConstructor
    protected static class Markdown {
        private String content;
    }
}
