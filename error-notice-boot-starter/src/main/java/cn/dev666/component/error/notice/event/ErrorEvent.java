package cn.dev666.component.error.notice.event;

import org.springframework.context.ApplicationEvent;


public abstract class ErrorEvent extends ApplicationEvent {

    /**
     * 类型
     */
    private String type;

    /**
     * 唯一错误码，用于区分同一场景中，错误是否相同及错误数累计。
     */
    private String uniqueErrorCode;

    public ErrorEvent(String type, String uniqueErrorCode) {
        super(ErrorEvent.class);
        this.uniqueErrorCode = uniqueErrorCode;
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public String getUniqueErrorCode() {
        return uniqueErrorCode;
    }
}
