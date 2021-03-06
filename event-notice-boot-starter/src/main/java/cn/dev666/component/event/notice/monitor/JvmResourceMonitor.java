package cn.dev666.component.event.notice.monitor;

import cn.dev666.component.event.notice.config.EventNoticeProperties;
import cn.dev666.component.event.notice.enums.JvmType;
import cn.dev666.component.event.notice.event.Events;
import cn.dev666.component.event.notice.listener.NoticeEventListener;
import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.CollectionUtils;
import oshi.util.FormatUtil;

import java.lang.management.*;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
public class JvmResourceMonitor {

    private final DecimalFormat df = new DecimalFormat("0.00%");

    private final AtomicInteger cpuLoadCount = new AtomicInteger(0);

    private final Map<String,AtomicInteger> memoryUsedCountMap = new ConcurrentHashMap<>(6);

    private final AtomicLong fullGcCount = new AtomicLong(0);

    private EventNoticeProperties.JvmResourceProperties properties;

    private NoticeEventListener noticeEventListener;

    public JvmResourceMonitor(ThreadPoolTaskScheduler scheduler, NoticeEventListener noticeEventListener,
                              EventNoticeProperties.JvmResourceProperties properties, JvmType type) {
        scheduler.scheduleAtFixedRate(this::monitor, properties.getFrequency());
        this.noticeEventListener = noticeEventListener;
        this.properties = properties;
    }

    private void monitor() {
        dealCpu();
        dealThread();
        dealMemory();
        dealGc();
    }

    public void dealCpu(){
        OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        double systemCpuLoad = os.getSystemCpuLoad();
        double processCpuLoad = os.getProcessCpuLoad();
        if (systemCpuLoad < 0 || processCpuLoad < 0){
            return;
        }

        processCpuLoad = processCpuLoad * systemCpuLoad;
        log.debug("JVM CPU??????????????????????????? {}???????????????????????? {}", systemCpuLoad, processCpuLoad);

        double processCpuLoadNoticeRate = properties.getCpuLoadNoticeRate();
        if (processCpuLoadNoticeRate > 0) {
            if (processCpuLoad >= processCpuLoadNoticeRate){
                if (cpuLoadCount.incrementAndGet() >= properties.getCpuLoadNoticeFrequency()){
                    cpuLoadCount.set(0);

                    Map<String,String> argsMap = new LinkedHashMap<>(3);
                    argsMap.put("????????????", "?????? JVM CPU ?????????????????????????????? 0 ~ " + processCpuLoadNoticeRate + "???");
                    argsMap.put("??????CPU?????????", df.format(systemCpuLoad));
                    argsMap.put("??????CPU?????????", df.format(processCpuLoad));
                    noticeEventListener.onApplicationEvent(Events.newEvent("??????????????????", "????????????CPU???????????????", argsMap));
                }
            }else {
                cpuLoadCount.set(0);
            }
        }
    }

    public void dealThread(){
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        int threadCount = threadBean.getThreadCount();

        log.debug("JVM ?????????????????????????????? {}", threadCount);

        if (properties.getThreadNoticeCount() > 0){
            if (threadCount >= properties.getThreadNoticeCount()){
                Map<String,String> argsMap = new LinkedHashMap<>(2);
                argsMap.put("????????????", "?????? JVM ?????????????????????????????? 0 ~ " + properties.getThreadNoticeCount() + "???");
                argsMap.put("???????????????", String.valueOf(threadCount));
                noticeEventListener.onApplicationEvent(Events.newEvent("??????????????????", "?????????????????????", argsMap));
            }
        }

        /*
         *  TODO ????????????????????????????????????????????????
         *  long[] deadlockedThreads = threadBean.findDeadlockedThreads();
         *  long[] monitorDeadlockedThreads = threadBean.findMonitorDeadlockedThreads();
         */

        /*
         * TODO ????????????CPU?????????????????????
         */

    }

    public void dealMemory(){
        List<MemoryPoolMXBean> memoryPoolList = ManagementFactory.getMemoryPoolMXBeans();

        int max = 0;
        for (MemoryPoolMXBean bean : memoryPoolList) {
            if (bean.getName().length() > max){
                max = bean.getName().length();
            }
        }

        GarbageCollectorMXBean gcBean = null;
        int managerPoolCount = Integer.MAX_VALUE;
        List<GarbageCollectorMXBean> list = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean bean : list) {
            if (bean.getMemoryPoolNames().length <= managerPoolCount){
                managerPoolCount = bean.getMemoryPoolNames().length;
                gcBean = bean;
            }
        }

        Set<String> poolNameSet = gcBean == null ? Collections.emptySet() :
                Arrays.stream(gcBean.getMemoryPoolNames()).collect(Collectors.toSet());


        for (MemoryPoolMXBean bean : memoryPoolList) {
            bean.getCollectionUsage();
            MemoryUsage usage = bean.getUsage();
            String memoryMax = usage.getMax() > 0 ? FormatUtil.formatBytes(usage.getMax()) : "?????????";
            String memoryCommitted = FormatUtil.formatBytes(usage.getCommitted());
            String rate = getRate(usage.getMax() > 0 ? usage.getMax() : usage.getCommitted(), usage.getUsed());
            log.debug("JVM ????????????{}??????????????? {}?????????????????? {}???????????? {}???GC?????? {}", String.format("%-"+max+"s",bean.getName()),
                    memoryMax, memoryCommitted, rate, String.join(",", bean.getMemoryManagerNames()));

            if (properties.getMemoryUsedNoticeRate() > 0) {
                //???????????????????????????????????????
                if (usage.getMax() > 0 && !poolNameSet.contains(bean.getName())) {

                    AtomicInteger num;
                    synchronized (bean.getName().intern()) {
                        num = memoryUsedCountMap.computeIfAbsent(bean.getName(), k -> new AtomicInteger(0));
                    }
                    //????????????N?????????????????????????????????
                    if ((double) usage.getUsed() / usage.getMax() >= properties.getMemoryUsedNoticeRate()) {

                        if (num.incrementAndGet() >= properties.getMemoryUsedNoticeFrequency()) {
                            num.set(0);

                            Map<String, String> argsMap = new LinkedHashMap<>(3);
                            String name = "??????JVM ????????? " + bean.getName() + " ?????????????????????????????? 0 ~ " + properties.getMemoryUsedNoticeRate() + "???";
                            argsMap.put("????????????", name);
                            argsMap.put("????????????", memoryMax);
                            argsMap.put("?????????", memoryCommitted);
                            argsMap.put("?????????", FormatUtil.formatBytes(usage.getUsed()));
                            argsMap.put("?????????", rate);
                            noticeEventListener.onApplicationEvent(Events.newEvent("??????????????????", name, argsMap));
                        }
                    }else {
                        num.set(0);
                    }
                }
            }
        }


    }

    public void dealGc(){
        List<GarbageCollectorMXBean> list = ManagementFactory.getGarbageCollectorMXBeans();

        if (CollectionUtils.isEmpty(list)){
            return;
        }

        int nameLength = 0;
        int managerPoolCount = 0;
        GarbageCollectorMXBean fullGcBean = null;
        for (GarbageCollectorMXBean bean : list) {
            if (bean.getName().length() > nameLength){
                nameLength = bean.getName().length();
            }
            if (bean.getMemoryPoolNames().length > managerPoolCount){
                managerPoolCount = bean.getMemoryPoolNames().length;
                fullGcBean = bean;
            }
        }
        for (GarbageCollectorMXBean bean : list) {
            log.debug("JVM ???????????????{}???????????? {}???????????? {} ms??????????????? {} ms", String.format("%-"+nameLength+"s",bean.getName()),
                    bean.getCollectionCount(),bean.getCollectionTime(),
                    String.format("%.2f", (float)bean.getCollectionTime()/bean.getCollectionCount()));
        }

        if (fullGcBean == null){
            return;
        }


        long count = fullGcBean.getCollectionCount();
        long lastCount = fullGcCount.getAndSet(count);

        //???????????????
        if (lastCount == 0){
            return;
        }

        long num = count - lastCount;

        if (num >= properties.getFullGcNoticeCount()){
            Map<String,String> argsMap = new LinkedHashMap<>(3);
            argsMap.put("????????????", "?????? JVM Full GC ??????????????????????????? 0 ~ " + properties.getFullGcNoticeCount() + "???");
            argsMap.put("Full GC ??????",  String.format("%s ????????? %d ???", format(properties.getFrequency()), num));
            noticeEventListener.onApplicationEvent(Events.newEvent("??????????????????", "Full GC ????????????", argsMap));
        }
    }

    private String format(Duration duration) {
        return duration.toString().replaceFirst("PT","")
                .replaceFirst("H", "??????").replaceFirst("M", "??????")
                .replaceFirst("S","???");
    }

    private String getRate(double total, double value) {
        return df.format(value / total);
    }
}
