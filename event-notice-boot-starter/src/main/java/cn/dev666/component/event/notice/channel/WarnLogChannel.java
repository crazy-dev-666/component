package cn.dev666.component.event.notice.channel;

import cn.dev666.component.event.notice.event.DefaultNoticeEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WarnLogChannel implements Channel<DefaultNoticeEvent> {

    @Override
    public boolean notice(DefaultNoticeEvent event) {
        log.warn("{} \n {}", event.getTitle(), event.getContent());
        return true;
    }
}
