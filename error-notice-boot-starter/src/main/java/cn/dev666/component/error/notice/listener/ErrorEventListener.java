package cn.dev666.component.error.notice.listener;

import cn.dev666.component.error.notice.channel.AggregationChannel;
import cn.dev666.component.error.notice.channel.Channel;
import cn.dev666.component.error.notice.config.ErrorNoticeProperties;
import cn.dev666.component.error.notice.content.Content;
import cn.dev666.component.error.notice.content.ContentResult;
import cn.dev666.component.error.notice.content.DefaultContentImpl;
import cn.dev666.component.error.notice.event.DefaultErrorEvent;
import cn.dev666.component.error.notice.event.ErrorEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ErrorEventListener implements InitializingBean, ApplicationContextAware {

    //累计产生1w次错误后，进行清理操作
    private static final int CLEAN_THRESHOLD = 10000;

    //最多保留错误数上限。
    private static final int ERROR_THRESHOLD = 10000;

    private AtomicInteger cleanCount = new AtomicInteger();

    //实时错误信息。
    private Map<String, ErrorInfo> errorInfoMap = new HashMap<>();

    private ApplicationContext applicationContext;

    //普通通知渠道
    private Set<Channel> channelSet = new HashSet<>();

    //聚合通知渠道
    private Set<AggregationChannel> aggregationChannelSet = new HashSet<>();

    private Queue<ContentResult> resultQueue;

    private AtomicBoolean aggregationFlag;

    private Map<Class, Content> eventContentMap = new HashMap<>();

    private List<String> profiles;


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private final ThreadPoolTaskExecutor eventExecutor;

    private final ThreadPoolTaskScheduler scheduler;

    private final ErrorNoticeProperties properties;

    private final String applicationName;


    public ErrorEventListener(ThreadPoolTaskExecutor eventExecutor, ThreadPoolTaskScheduler scheduler,
                              ErrorNoticeProperties properties, String applicationName) {
        this.eventExecutor = eventExecutor;
        this.scheduler = scheduler;
        this.properties = properties;
        this.applicationName = applicationName;
    }

    @Override
    public void afterPropertiesSet() {
        this.profiles = Arrays.asList(applicationContext.getEnvironment().getActiveProfiles());
        Map<String, Content> contentMap = applicationContext.getBeansOfType(Content.class);
        for (Map.Entry<String, Content> entry : contentMap.entrySet()) {
            Content value = entry.getValue();
            ParameterizedType type = (ParameterizedType) value.getClass().getGenericSuperclass();
            Class<?> eventClazz = (Class<?>) type.getActualTypeArguments()[0];
            eventContentMap.put(eventClazz, value);
        }

        if (!eventContentMap.containsKey(DefaultErrorEvent.class)){
            eventContentMap.put(DefaultErrorEvent.class, new DefaultContentImpl(profiles.toString(), applicationName));
        }

        Map<String, Channel> beans = applicationContext.getBeansOfType(Channel.class);

        for (Channel channel : beans.values()) {
            if (channel instanceof AggregationChannel){
                this.aggregationChannelSet.add((AggregationChannel)channel);
            }else {
                this.channelSet.add(channel);
            }
        }

        if (this.aggregationChannelSet.size() > 0){
            resultQueue = new ConcurrentLinkedQueue<>();
            aggregationFlag = new AtomicBoolean(false);
            scheduler.scheduleAtFixedRate(this::aggregationNotice, properties.getAggregationInterval());
        }
    }

    /**
     * 每一类错误，第一次出现时，立刻发提醒给相关人员。
     * 达到上限后清除。
     * 后续复现时，对应间隔时间内不再发送，只是计数，间隔时间外，则再次发送提醒。
     * 间隔时间内，累计次数达到阈值时，再次发送提醒，重置间隔时间。
     *
     * 场景：出现异常发送提醒，每小时提醒一次，如果1小时内异常出现超过阈值10次时，再次提醒
     */
    @EventListener
    public void onApplicationEvent(ErrorEvent event) {

        if (CollectionUtils.containsAny(properties.getIgnoreProfiles(), profiles)){
            return;
        }

        String uniqueKey = getKey(event);
        ErrorInfo info;
        synchronized (uniqueKey.intern()) {
            info = errorInfoMap.computeIfAbsent(uniqueKey, ErrorInfo::new);
        }

        long now = event.getTimestamp();
        DealEventResult result = info.dealEvent(now);
        if (result.remindFlag) {

            ContentResult contentResult;
            try {
                Content content = eventContentMap.get(event.getClass());

                if (content == null){
                    log.error("根据事件类型 {}，没有找到匹配的获取通知内容的 Bean", event.getClass().getName());
                    return;
                }

                contentResult = content.get(event, result);
            }catch (Exception e){
                log.error("获取通知内容异常",e);
                return;
            }


            if (aggregationChannelSet.size() > 0){
                resultQueue.add(contentResult);
            }
            for (Channel channel : channelSet) {
                channelNotice(contentResult, channel);
            }
        }
        cleanCheck();
    }

    private void aggregationNotice() {

        if (!aggregationFlag.compareAndSet(false, true)){
            return;
        }

        try {
            int size = resultQueue.size();
            if (size == 0){
                return;
            }
            ContentResult result = null;
            List<ContentResult> list = new ArrayList<>();
            if (size == 1){
                result = resultQueue.poll();
            }else {
                while (true){
                    ContentResult poll = resultQueue.poll();
                    if (poll == null){
                        break;
                    }
                    list.add(poll);
                }
            }
            String profiles = this.profiles.toString();
            for (AggregationChannel channel : aggregationChannelSet) {
                if (list.size() > 0){
                    result = channel.resultAggregation(profiles, list);
                }
                channelNotice(result, channel);
            }
        }finally {
            aggregationFlag.compareAndSet(true, false);
        }
    }

    private void channelNotice(final ContentResult result, final Channel channel) {
        eventExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                channel.notice(result);
            } catch (Exception e) {
                log.error(channel.getClass().getName() + " 渠道通知异常", e);
            } finally {
                log.info("{} 渠道通知完成，耗时 {} ms ", channel.getClass().getName(), System.currentTimeMillis() - start);
            }
        });
    }


    private String getKey(ErrorEvent event) {
        return event.getScene() + ":" + event.getUniqueErrorCode();
    }

    @Getter
    private class ErrorInfo {

        private final String uniqueErrorCode;
        private long total;
        private int intervalTotal;
        private long lastTime;

        private ErrorInfo(String uniqueErrorCode) {
            this.uniqueErrorCode = uniqueErrorCode;
            this.total = 0;
            this.intervalTotal = 0;
        }

        private DealEventResult dealEvent(long now) {
            boolean intervalFirst = false;
            boolean overThreshold = false;
            String frequency = "";

            synchronized(this) {
                total++;
                //间隔大于设定 重置
                if (lastTime + properties.getInterval().toMillis() < now) {
                    String interval = null;
                    if (lastTime > 0){
                        interval = format(Duration.ofMillis(now - lastTime));
                    }

                    frequency = (StringUtils.hasText(interval) ? interval + "内" : "") + "首次出现（总累计 " + total + " 次出现）";

                    intervalTotal = 1;
                    lastTime = now;
                    intervalFirst = true;
                }else {
                    intervalTotal++;
                    overThreshold = intervalTotal >= properties.getThreshold();
                    //间隔内累计次数达到阈值 重置
                    if (overThreshold) {
                        frequency = format(Duration.ofMillis(now - lastTime)) + "内累计 " + intervalTotal + " 次出现（总累计 " + total + " 次出现）";
                        intervalTotal = 1;
                        lastTime = now;
                    }
                }

            }

            return new DealEventResult(intervalFirst || overThreshold, frequency);
        }

        private String format(Duration duration) {
            return duration.toString().replaceFirst("PT","")
                    .replaceFirst("H", "小时").replaceFirst("M", "分钟")
                    .replaceFirst("S","秒");
        }
    }

    @Getter
    @AllArgsConstructor
    public static class DealEventResult {
        private boolean remindFlag;
        private String frequency;
    }


    private void cleanCheck() {
        int count = cleanCount.incrementAndGet();

        // 达到清理次数阈值
        if (count >= CLEAN_THRESHOLD){
            cleanCount.set(0);

            //达到清理数量阈值
            if (errorInfoMap.size() > ERROR_THRESHOLD){
                //按上次发送报警邮件时间，清理最老的。
                List<ErrorInfo> list = new ArrayList<>(errorInfoMap.values());
                list.sort(Comparator.comparing(ErrorInfo::getLastTime));
                List<ErrorInfo> removeList = list.subList(0, errorInfoMap.size() - ERROR_THRESHOLD);
                for (ErrorInfo errorInfo : removeList) {
                    synchronized (errorInfo.uniqueErrorCode.intern()) {
                        errorInfoMap.remove(errorInfo.uniqueErrorCode);
                    }
                }
            }
        }
    }
}