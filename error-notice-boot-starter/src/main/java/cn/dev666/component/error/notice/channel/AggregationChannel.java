package cn.dev666.component.error.notice.channel;

import cn.dev666.component.error.notice.content.ContentResult;

/**
 * 避免发送过于频繁，将报警信息进行聚合发送。可设置聚合时段
 * 存在延迟情况
 */
public abstract class AggregationChannel implements Channel {

    @Override
    public boolean notice(ContentResult cr) {
        return false;
    }
}
