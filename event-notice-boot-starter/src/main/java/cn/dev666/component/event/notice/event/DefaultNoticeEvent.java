package cn.dev666.component.event.notice.event;

import lombok.Getter;

import java.util.Map;

@Getter
public class DefaultNoticeEvent extends NoticeEvent {

    /**
     * 其他参数
     */
    private Map<String,String> argsMap;

    DefaultNoticeEvent(String scene, String uniqueErrorCode, Map<String,String> argsMap) {
        super(scene, uniqueErrorCode);
        this.argsMap = argsMap;
    }

    @Override
    public String getTitle() {
        return getScene() + " 报警，错误码：" + getUniqueCode();
    }

    @Override
    public StringBuilder getContent() {
        StringBuilder sb = super.getContent();
        if (argsMap != null && argsMap.size() > 0) {
            for (Map.Entry<String, String> entry : argsMap.entrySet()) {
                sb.append("\n ").append(entry.getKey()).append(" : ").append(entry.getValue());
            }
        }
        return sb;
    }
}
