package cn.dev666.component.error.notice.event;

import org.springframework.context.ApplicationEvent;


public abstract class ErrorEvent extends ApplicationEvent {

    private String type;

    public ErrorEvent(String type) {
        super(ErrorEvent.class);
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
