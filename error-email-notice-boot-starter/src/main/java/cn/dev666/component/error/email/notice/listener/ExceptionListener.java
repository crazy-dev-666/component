package cn.dev666.component.error.email.notice.listener;

import cn.dev666.component.error.email.notice.config.ExceptionMailProperties;
import cn.dev666.component.error.email.notice.event.ExceptionEvent;
import cn.dev666.component.error.email.notice.mail.MailService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ExceptionListener {

    //累计产生1w次异常后，进行清理操作
    private static final int CLEAN_THRESHOLD = 10000;

    //最多保留异常上限。
    private static final int EXCEPTION_THRESHOLD = 10000;

    private AtomicInteger cleanCount = new AtomicInteger();
    /**
     * 实时异常信息
     */
    private Map<String, ExceptionInfo> exceptionInfoMap = new HashMap<>();

    private final ThreadPoolTaskExecutor eventExecutor;

    private final ExceptionMailProperties properties;

    private final List<String> profiles;

    private final MailService mailService;

    public ExceptionListener(ThreadPoolTaskExecutor eventExecutor, ExceptionMailProperties properties, List<String> profiles, MailService mailService) {
        this.eventExecutor = eventExecutor;
        this.properties = properties;
        this.profiles = profiles;
        this.mailService = mailService;
    }

    /**
     * 每一类异常，第一次出现时，立刻发邮件给相关人员。
     * 达到上限后清除。
     * 后续复现时，对应间隔时间内不再发送，只是计数，间隔时间外，则再次发送邮箱。
     * 间隔时间内，累计次数达到阈值时，再次发送邮件，重置间隔时间。
     *
     * 场景：出现异常发送提醒，每小时提醒一次，如果1小时内异常出现超过阈值10次时，再次提醒
     */
    @EventListener
    public void onApplicationEvent(ExceptionEvent event) {

        if (CollectionUtils.containsAny(properties.getIgnoreProfiles(), profiles)){
            return;
        }

        Exception exception = event.getException();
        String infoKey = getKey(exception);
        ExceptionInfo info;
        synchronized (infoKey.intern()) {
            info = exceptionInfoMap.computeIfAbsent(infoKey, ExceptionInfo::new);
        }
        info.dealEvent(event);

        cleanCheck();
    }
    private void sendEmail(ExceptionEvent event, String frequency) {
        Exception exception = event.getException();
        String[] to;
        Class<?> clazz = exception.getClass();
        do {
            to = properties.getTo().get(clazz.getName());
            clazz = clazz.getSuperclass();
        }while ((to == null || to.length == 0) && clazz != null);

        if (to != null && to.length > 0) {
            String[] finalTo = to;
            eventExecutor.execute(()-> mailService.exceptionSend(exception, event.getUrl(), event.getTimestamp(),
                    frequency, properties.getCc(), finalTo));
        }
    }

    private String getKey(Exception exception) {
        return exception.getClass().getName() + ":" + exception.getMessage();
    }

    @Getter
    private class ExceptionInfo {

        private final String exception;
        private long total;
        private int intervalTotal;
        private long lastTime;

        private ExceptionInfo(String exception) {
            this.exception = exception;
            this.total = 0;
            this.intervalTotal = 0;
        }

        private void dealEvent(ExceptionEvent event) {
            long now = event.getTimestamp();
            boolean intervalFirst;
            boolean overThreshold;
            String frequency = "";

            synchronized(this) {

                //间隔大于设定 重置
                if (lastTime + properties.getInterval().toMillis() < now) {
                    intervalTotal = 0;
                    lastTime = now;
                }

                intervalTotal++;
                total++;

                intervalFirst = intervalTotal == 1;
                overThreshold = intervalTotal >= properties.getThreshold();

                if (intervalFirst) {
                    frequency = format(properties.getInterval()) + "内第一次出现（总累计 " + total + " 次出现）";
                }

                //间隔内累计次数达到阈值 重置
                if (overThreshold) {
                    frequency = format(Duration.ofMillis(now - lastTime)) + "内累计 " + intervalTotal + " 次出现（总累计 " + total + " 次出现）";
                    intervalTotal = 1;
                    lastTime = now;
                }
            }

            if (intervalFirst || overThreshold){
                sendEmail(event, frequency);
            }
        }

        private String format(Duration duration) {
            return duration.toString().replaceFirst("PT","")
                    .replaceFirst("H", "小时").replaceFirst("M", "分钟")
                    .replaceFirst("S","秒");
        }
    }


    private void cleanCheck() {
        int count = cleanCount.incrementAndGet();

        // 达到清理次数阈值
        if (count >= CLEAN_THRESHOLD){
            cleanCount.set(0);

            //达到清理数量阈值
            if (exceptionInfoMap.size() > EXCEPTION_THRESHOLD){
                //按上次发送报警邮件时间，清理最老的。
                List<ExceptionInfo> list = new ArrayList<>(exceptionInfoMap.values());
                list.sort(Comparator.comparing(ExceptionInfo::getLastTime));
                List<ExceptionInfo> removeList = list.subList(0, exceptionInfoMap.size() - EXCEPTION_THRESHOLD);
                for (ExceptionInfo exceptionInfo : removeList) {
                    synchronized (exceptionInfo.exception.intern()) {
                        exceptionInfoMap.remove(exceptionInfo.exception);
                    }
                }
            }
        }
    }
}