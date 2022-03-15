package cn.dev666.component.error.notice.event;

import org.springframework.context.ApplicationEvent;


public abstract class ErrorEvent extends ApplicationEvent {

    /**
     * 错误场景
     */
    private String scene;

    /**
     * 唯一错误码，用于区分同一场景中，错误是否相同及错误数累计。
     */
    private String uniqueErrorCode;

    public ErrorEvent(String scene, String uniqueErrorCode) {
        super(ErrorEvent.class);
        this.uniqueErrorCode = uniqueErrorCode;
        this.scene = scene;
    }

    public String getScene() {
        return scene;
    }

    public String getUniqueErrorCode() {
        return uniqueErrorCode;
    }
}
