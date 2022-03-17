package cn.dev666.component.event.notice.channel;

import cn.dev666.component.event.notice.event.NoticeEvent;

/**
 * 通知渠道方式
 */
public interface Channel<E extends NoticeEvent> {

    boolean notice(E event) throws Exception;
}
