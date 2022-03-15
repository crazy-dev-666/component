package cn.dev666.component.error.notice.channel;


import cn.dev666.component.error.notice.content.ContentResult;

import java.util.List;

/**
 * 避免发送过于频繁，间隔时间内，将报警信息进行聚合发送，存在一定延迟
 */
public interface AggregationChannel<R extends ContentResult> extends Channel<R> {


    R resultAggregation(String profiles, List<ContentResult> list);
}
