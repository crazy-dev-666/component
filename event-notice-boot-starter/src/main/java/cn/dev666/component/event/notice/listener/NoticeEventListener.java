package cn.dev666.component.event.notice.listener;

import cn.dev666.component.event.notice.channel.AggregationChannel;
import cn.dev666.component.event.notice.channel.Channel;
import cn.dev666.component.event.notice.config.EventNoticeProperties;
import cn.dev666.component.event.notice.event.DealEventResult;
import cn.dev666.component.event.notice.event.NoticeEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class NoticeEventListener implements InitializingBean, ApplicationContextAware {

    //累计产生1w次事件后，进行清理操作
    private static final int CLEAN_THRESHOLD = 10000;

    //最多保留事件数上限。
    private static final int ERROR_THRESHOLD = 10000;

    private AtomicInteger cleanCount = new AtomicInteger();

    //实时事件信息。
    private Map<String, NoticeEventInfo> errorInfoMap = new HashMap<>();

    private ApplicationContext applicationContext;

    //普通通知渠道
    private Map<Class, Set<Channel>> eventChannelMap = new HashMap<>();

    //聚合通知渠道
    private Map<Class, Set<AggregationChannel>> eventAggregationChannelMap = new HashMap<>();

    private Queue<NoticeEvent> eventQueue;

    private AtomicBoolean aggregationFlag;

    private List<String> profiles;


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private final ThreadPoolTaskExecutor eventExecutor;

    private final ThreadPoolTaskScheduler scheduler;

    private final EventNoticeProperties properties;

    public NoticeEventListener(ThreadPoolTaskExecutor eventExecutor, ThreadPoolTaskScheduler scheduler,
                               EventNoticeProperties properties) {
        this.eventExecutor = eventExecutor;
        this.scheduler = scheduler;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        Environment environment = applicationContext.getEnvironment();
        String[] profiles = environment.getActiveProfiles();
        if (profiles == null || profiles.length == 0){
            profiles = environment.getDefaultProfiles();
        }
        this.profiles = Arrays.asList(profiles);
        Map<String, Channel> beans = applicationContext.getBeansOfType(Channel.class);

        for (Channel channel : beans.values()) {
            ParameterizedType type = (ParameterizedType) channel.getClass().getGenericInterfaces()[0];
            Class<?> eventClazz = (Class<?>) type.getActualTypeArguments()[0];
            if (channel instanceof AggregationChannel){
                Set<AggregationChannel> channels = this.eventAggregationChannelMap.computeIfAbsent(eventClazz, k -> new HashSet<>());
                channels.add((AggregationChannel)channel);
            }else {
                Set<Channel> channels = this.eventChannelMap.computeIfAbsent(eventClazz, k -> new HashSet<>());
                channels.add(channel);
            }
        }

        if (this.eventAggregationChannelMap.size() > 0){
            eventQueue = new ConcurrentLinkedQueue<>();
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
    @EventListener(NoticeEvent.class)
    @SuppressWarnings("unchecked")
    public void onApplicationEvent(NoticeEvent event) {

        if (CollectionUtils.containsAny(properties.getIgnoreProfiles(), profiles)){
            return;
        }

        String uniqueKey = getKey(event);
        NoticeEventInfo info;
        synchronized (uniqueKey.intern()) {
            info = errorInfoMap.computeIfAbsent(uniqueKey, NoticeEventInfo::new);
        }

        long now = event.getTimestamp();
        DealEventResult result = info.dealEvent(now);
        if (result.isRemindFlag()) {

            event.setResult(result);

            boolean isNotice = false;

            if (eventAggregationChannelMap.containsKey(event.getClass())){
                eventQueue.add(event);
                isNotice = true;
            }
            Set<Channel> channels = eventChannelMap.get(event.getClass());
            if (channels != null) {
                isNotice = true;
                for (Channel channel : channels) {
                    channelNotice(new Notice(){

                        @Override
                        public boolean doNotice()throws Exception {
                            return  channel.notice(event);
                        }

                        @Override
                        public String getChannelName() {
                            return channel.getClass().getName();
                        }
                    });
                }
            }

            if (!isNotice){
                log.error("根据事件类型 {}，没有找到匹配的通知渠道", event.getClass().getName());
            }
        }
        cleanCheck();
    }

    @SuppressWarnings("unchecked")
    private void aggregationNotice() {

        if (!aggregationFlag.compareAndSet(false, true)){
            return;
        }

        try {
            int size = eventQueue.size();
            if (size == 0){
                return;
            }
            Map<Class,List<NoticeEvent>> map = new HashMap<>();
            while (true){
                NoticeEvent event = eventQueue.poll();
                if (event == null){
                    break;
                }
                List<NoticeEvent> list = map.computeIfAbsent(event.getClass(), k -> new ArrayList<>());
                list.add(event);
            }
            for (Map.Entry<Class, List<NoticeEvent>> entry : map.entrySet()) {
                Set<AggregationChannel> channels = eventAggregationChannelMap.get(entry.getKey());
                for (AggregationChannel channel : channels) {
                    channelNotice(new Notice(){

                        @Override
                        public boolean doNotice()throws Exception {
                            return channel.notice(entry.getValue());
                        }

                        @Override
                        public String getChannelName() {
                            return channel.getClass().getName();
                        }
                    });
                }
            }
        }finally {
            aggregationFlag.compareAndSet(true, false);
        }
    }

    private interface Notice {
        boolean doNotice() throws Exception;
        String getChannelName();
    }

    private void channelNotice(Notice notice) {
        eventExecutor.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                boolean result = notice.doNotice();
                if (!result){
                    log.warn("{} 渠道通知发送失败", notice.getChannelName());
                }
            } catch (Exception e) {
                log.error(notice.getChannelName() + " 渠道通知异常", e);
            } finally {
                log.info("{} 渠道通知完成，耗时 {} ms ", notice.getChannelName(), System.currentTimeMillis() - start);
            }
        });
    }


    private String getKey(NoticeEvent event) {
        return event.getScene() + ":" + event.getUniqueCode();
    }

    @Getter
    private class NoticeEventInfo {

        private final String uniqueCode;
        private long total;
        private int intervalTotal;
        private long lastTime;

        private NoticeEventInfo(String uniqueCode) {
            this.uniqueCode = uniqueCode;
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
                        frequency = interval + "内累计 "+ intervalTotal + 1  + " 次出现（总累计 " + total + " 次出现）";
                    }else {
                        frequency = "首次出现（总累计 " + total + " 次出现）";
                    }

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

    private void cleanCheck() {
        int count = cleanCount.incrementAndGet();

        // 达到清理次数阈值
        if (count >= CLEAN_THRESHOLD){
            cleanCount.set(0);

            //达到清理数量阈值
            if (errorInfoMap.size() > ERROR_THRESHOLD){
                //按上次发送通知时间，清理最老的。
                List<NoticeEventInfo> list = new ArrayList<>(errorInfoMap.values());
                list.sort(Comparator.comparing(NoticeEventInfo::getLastTime));
                List<NoticeEventInfo> removeList = list.subList(0, errorInfoMap.size() - ERROR_THRESHOLD);
                for (NoticeEventInfo noticeEventInfo : removeList) {
                    synchronized (noticeEventInfo.uniqueCode.intern()) {
                        errorInfoMap.remove(noticeEventInfo.uniqueCode);
                    }
                }
            }
        }
    }
}