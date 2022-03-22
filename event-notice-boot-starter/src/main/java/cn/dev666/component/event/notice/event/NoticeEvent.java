package cn.dev666.component.event.notice.event;

import org.springframework.context.ApplicationEvent;

import java.text.SimpleDateFormat;
import java.util.Date;


public abstract class NoticeEvent extends ApplicationEvent {

    /**
     * 场景
     */
    protected String scene;

    /**
     * 唯一码，用于区分同一场景中，通知是否相同及通知数累计。
     */
    protected String uniqueCode;

    private DealEventResult result;

    public NoticeEvent(String scene, String uniqueCode) {
        super(NoticeEvent.class);
        this.uniqueCode = uniqueCode;
        this.scene = scene;
    }

    public void setResult(DealEventResult result) {
        this.result = result;
    }

    public String getTitle(){
        return getScene() + " 报警";
    }

    public StringBuilder getContent(){
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(getTimestamp()));
        StringBuilder sb = new StringBuilder();
        sb.append("\n 通知时间 ：").append(time).append("\n 通知频次 ：").append(result.getFrequency());
        return sb;
    }

    public String getScene() {
        return scene;
    }

    public String getUniqueCode() {
        return uniqueCode;
    }
}
