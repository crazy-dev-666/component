package cn.dev666.component.error.notice.event;

import cn.dev666.component.error.notice.config.ErrorNoticeProperties;
import cn.dev666.component.error.notice.enums.JvmType;
import cn.dev666.component.error.notice.listener.ErrorEventListener;
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

    private ErrorNoticeProperties.JvmResourceProperties properties;

    private ErrorEventListener errorEventListener;

    public JvmResourceMonitor(ThreadPoolTaskScheduler scheduler, ErrorEventListener errorEventListener,
                              ErrorNoticeProperties.JvmResourceProperties properties, JvmType type) {
        scheduler.scheduleAtFixedRate(this::monitor, properties.getFrequency());
        this.errorEventListener = errorEventListener;
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
        log.debug("JVM CPU信息：系统总使用率 {}，当前进程使用率 {}", systemCpuLoad, processCpuLoad);

        double processCpuLoadNoticeRate = properties.getCpuLoadNoticeRate();
        if (processCpuLoadNoticeRate > 0) {
            if (processCpuLoad >= processCpuLoadNoticeRate){
                if (cpuLoadCount.incrementAndGet() >= properties.getCpuLoadNoticeFrequency()){
                    cpuLoadCount.set(0);

                    Map<String,String> argsMap = new LinkedHashMap<>(3);
                    argsMap.put("事件描述", "当前进程CPU使用率过高");
                    argsMap.put("系统CPU总使用率", String.valueOf(systemCpuLoad));
                    argsMap.put("进程CPU使用率", String.valueOf(processCpuLoad));
                    errorEventListener.onApplicationEvent(Events.newEvent("进程资源异常", "当前进程CPU使用率过高", argsMap));
                }
            }else {
                cpuLoadCount.set(0);
            }
        }
    }

    public void dealThread(){
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        int threadCount = threadBean.getThreadCount();

        log.debug("JVM 线程信息：实时线程数 {}", threadCount);

        if (properties.getThreadNoticeCount() > 0){
            if (threadCount >= properties.getThreadNoticeCount()){
                Map<String,String> argsMap = new LinkedHashMap<>(2);
                argsMap.put("事件描述", "实时线程数过多");
                argsMap.put("实时线程数", String.valueOf(threadCount));
                errorEventListener.onApplicationEvent(Events.newEvent("进程资源异常", "实时线程数过多", argsMap));
            }
        }

        /*
         *  TODO 线程死锁检测，昂贵操作，暂不支持
         *  long[] deadlockedThreads = threadBean.findDeadlockedThreads();
         *  long[] monitorDeadlockedThreads = threadBean.findMonitorDeadlockedThreads();
         */

        /*
         * TODO 单个线程CPU使用率过高预警
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
            String memoryMax = usage.getMax() > 0 ? FormatUtil.formatBytes(usage.getMax()) : "无上限";
            String memoryCommitted = FormatUtil.formatBytes(usage.getCommitted());
            log.debug("JVM 内存池：{}，最大内存 {}，已分配内存 {}，使用率 {}，GC方式 {}", String.format("%-"+max+"s",bean.getName()),
                    memoryMax, memoryCommitted,
                    getRate(usage.getCommitted(), usage.getUsed()), String.join(",", bean.getMemoryManagerNames()));

            if (properties.getMemoryUsedNoticeRate() > 0) {
                //非新生代和幸存区
                if (usage.getMax() > 0 && !poolNameSet.contains(bean.getName())) {

                    AtomicInteger num;
                    synchronized (bean.getName().intern()) {
                        num = memoryUsedCountMap.computeIfAbsent(bean.getName(), k -> new AtomicInteger(0));
                    }
                    //内存占用N次连续超过阈值时，报警
                    if ((double) usage.getUsed() / usage.getMax() >= properties.getMemoryUsedNoticeRate()) {

                        if (num.incrementAndGet() >= properties.getMemoryUsedNoticeFrequency()) {
                            num.set(0);

                            Map<String, String> argsMap = new LinkedHashMap<>(3);
                            String name = "当前JVM 内存池 " + bean.getName() + "使用率过高";
                            argsMap.put("事件描述", name);
                            argsMap.put("最大内存", memoryMax);
                            argsMap.put("已分配内存", memoryCommitted);
                            argsMap.put("已使用内存", FormatUtil.formatBytes(usage.getUsed()));
                            errorEventListener.onApplicationEvent(Events.newEvent("进程资源异常", name, argsMap));
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
            log.debug("JVM 垃圾回收：{}，总次数 {}，总耗时 {} ms，平均耗时 {} ms", String.format("%-"+nameLength+"s",bean.getName()),
                    bean.getCollectionCount(),bean.getCollectionTime(),
                    String.format("%.2f", (float)bean.getCollectionTime()/bean.getCollectionCount()));
        }

        if (fullGcBean == null){
            return;
        }


        long count = fullGcBean.getCollectionCount();
        long lastCount = fullGcCount.getAndSet(count);

        long num = count - lastCount;
        if (num >= properties.getFullGcNoticeCount()){
            Map<String,String> argsMap = new LinkedHashMap<>(3);
            argsMap.put("事件描述", "Full GC 过于频繁");
            argsMap.put("Full GC 频率",  String.format("%s 内出现 %d 次", format(properties.getFrequency()), num));
            errorEventListener.onApplicationEvent(Events.newEvent("进程资源异常", "Full GC 过于频繁", argsMap));
        }
    }

    private String format(Duration duration) {
        return duration.toString().replaceFirst("PT","")
                .replaceFirst("H", "小时").replaceFirst("M", "分钟")
                .replaceFirst("S","秒");
    }

    private String getRate(double total, double value) {
        return df.format(value / total);
    }
}
