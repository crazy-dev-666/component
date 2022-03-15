package cn.dev666.component.error.notice.content;

import cn.dev666.component.error.notice.event.ErrorEvent;
import cn.dev666.component.error.notice.listener.ErrorEventListener;

public interface Content<E extends ErrorEvent> {

    ContentResult get(E event, ErrorEventListener.DealEventResult result);

}
