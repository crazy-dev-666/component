package cn.dev666.component.error.notice.event;

import lombok.Getter;

import java.util.Map;

@Getter
public class DefaultErrorEvent extends ErrorEvent {

    /**
     * 其他参数
     */
    private Map<String,String> argsMap;

    DefaultErrorEvent(String scene, String uniqueErrorCode, Map<String,String> argsMap) {
        super(scene, uniqueErrorCode);
        this.argsMap = argsMap;
    }

}
