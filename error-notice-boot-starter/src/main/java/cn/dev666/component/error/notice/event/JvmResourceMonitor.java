package cn.dev666.component.error.notice.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.VirtualMemory;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.FormatUtil;
import oshi.util.Util;

import java.lang.management.*;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.List;

import static oshi.hardware.CentralProcessor.TickType.*;

@Slf4j
public class JvmResourceMonitor {

    private final DecimalFormat df = new DecimalFormat("0.00%");

    private long lastTime;
    private long[] lastTicks;

    public JvmResourceMonitor(ThreadPoolTaskScheduler scheduler) {
        scheduler.scheduleAtFixedRate(this::monitor, Duration.ofMinutes(1));
    }

    private void monitor() {
        /*
         * JVM
         */
        MemoryMXBean mxBean = ManagementFactory.getMemoryMXBean();
        List<MemoryPoolMXBean> memoryPoolList = ManagementFactory.getMemoryPoolMXBeans();

        int max = 0;
        for (MemoryPoolMXBean bean : memoryPoolList) {
            if (bean.getName().length() > max){
                max = bean.getName().length();
            }
        }
        for (MemoryPoolMXBean bean : memoryPoolList) {
            MemoryUsage usage = bean.getUsage();

            if (bean.getName().startsWith("tenured")){

            }

            log.debug("JVM 内存池：{}，最大内存 {}，已分配内存 {}，使用率 {}，GC方式 {}", String.format("%-"+max+"s",bean.getName()),
                    FormatUtil.formatBytes(usage.getMax()), FormatUtil.formatBytes(usage.getCommitted()),
                    getRate(usage.getCommitted(), usage.getUsed()), String.join(",", bean.getMemoryManagerNames()));
        }

        List<GarbageCollectorMXBean> beans = ManagementFactory.getGarbageCollectorMXBeans();
        max = 0;
        for (GarbageCollectorMXBean bean : beans) {
            if (bean.getName().length() > max){
                max = bean.getName().length();
            }
        }
        for (GarbageCollectorMXBean bean : beans) {
            log.debug("JVM 垃圾回收：{}，总次数 {}，总耗时 {} ms，平均耗时 {} ms", String.format("%-"+max+"s",bean.getName()),
                    bean.getCollectionCount(),bean.getCollectionTime(),
                    String.format("%.2f", (float)bean.getCollectionTime()/bean.getCollectionCount()));
        }

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        long[] monitorDeadlockedThreads = threadBean.findMonitorDeadlockedThreads();
    }

    private String getRate(double total, double value) {
        return df.format(value / total);
    }
}
