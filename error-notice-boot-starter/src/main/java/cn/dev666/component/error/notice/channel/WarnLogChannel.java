package cn.dev666.component.error.notice.channel;

import cn.dev666.component.error.notice.content.ContentResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WarnLogChannel implements Channel {
    @Override
    public boolean notice(ContentResult cr) {
        log.warn("{} \n {}", cr.getTitle(), cr.getContent());
        return true;
    }
}
