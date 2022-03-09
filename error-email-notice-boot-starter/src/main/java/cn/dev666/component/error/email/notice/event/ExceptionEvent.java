package cn.dev666.component.error.email.notice.event;

import org.springframework.context.ApplicationEvent;


public class ExceptionEvent extends ApplicationEvent {

    private Exception exception;

    private String url;

    public ExceptionEvent(Object source) {
        super(source);
    }

    public static ExceptionEvent newInstance(Exception e, String url){
        ExceptionEvent event = new ExceptionEvent(ExceptionEvent.class);
        event.setException(e);
        event.setUrl(url);
        return event;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }


    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
