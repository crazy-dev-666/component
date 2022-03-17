package cn.dev666.component.error.notice.channel;

import cn.dev666.component.error.notice.event.NoticeEvent;

/**
 * 通知渠道方式
 */
public interface Channel<E extends NoticeEvent> {

    boolean notice(E event) throws Exception;
}
