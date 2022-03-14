package cn.dev666.component.error.notice.channel;

import cn.dev666.component.error.notice.content.ContentResult;

/**
 * 通知渠道方式
 */
public interface Channel {

    boolean notice(ContentResult cr) throws Exception;
}
