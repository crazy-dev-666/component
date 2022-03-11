package cn.dev666.component.error.notice.event;

import lombok.Getter;
import org.springframework.lang.Nullable;

import java.util.Map;

@Getter
public class DefaultErrorEvent extends ErrorEvent {

    /**
     * 唯一错误码，用于区分同一场景中，错误是否相同及错误数累计。为空时，则根据入参区分
     */
    @Nullable
    private String uniqueErrorCode;
    /**
     * 其他参数
     */
    private Map<String,String> argsMap;

    DefaultErrorEvent(String scene, String uniqueErrorCode, Map<String,String> argsMap) {
        super(scene);
        this.uniqueErrorCode = uniqueErrorCode;
        this.argsMap = argsMap;
    }

}
