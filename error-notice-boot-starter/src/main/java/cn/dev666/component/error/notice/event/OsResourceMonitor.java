package cn.dev666.component.error.notice.event;

import cn.dev666.component.error.notice.config.ErrorNoticeProperties;
import cn.dev666.component.error.notice.listener.ErrorEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.VirtualMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.FormatUtil;
import oshi.util.Util;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static oshi.hardware.CentralProcessor.TickType.*;

@Slf4j
public class OsResourceMonitor {

    private final DecimalFormat df = new DecimalFormat("0.00%");

    private long[] lastTicks;
    private ErrorEventListener errorEventListener;
    private ErrorNoticeProperties.OsResourceProperties properties;

    public OsResourceMonitor(ThreadPoolTaskScheduler scheduler, ErrorEventListener errorEventListener,
                             ErrorNoticeProperties.OsResourceProperties properties) {
        scheduler.scheduleAtFixedRate(this::monitor, properties.getFrequency());
        this.errorEventListener = errorEventListener;
        this.properties = properties;
    }

    private void monitor() {
        SystemInfo si = new SystemInfo();
        OperatingSystem os = si.getOperatingSystem();
        HardwareAbstractionLayer hal = si.getHardware();
        CentralProcessor processor = hal.getProcessor();

        int logicalCount = processor.getLogicalProcessorCount();

        log.debug("CPU信息：型号 {}，{}核{}线程", processor.getProcessorIdentifier().getName(),
                processor.getPhysicalProcessorCount(), logicalCount);

        dealLoadAverage(processor, logicalCount);
        dealProcessor(processor);
        GlobalMemory memory = hal.getMemory();
        dealMemory(memory);
        dealSwap(memory);
        FileSystem fileSystem = os.getFileSystem();
        dealDisk(fileSystem);
        dealFileDesc(fileSystem);
    }

    private void dealFileDesc(FileSystem fileSystem) {
        long maxFileDescriptors = fileSystem.getMaxFileDescriptors();
        long openFileDescriptors = fileSystem.getOpenFileDescriptors();

        String format3 = "文件描述符：上限值 {}，使用率 {}";
        String[] args3 = {String.valueOf(maxFileDescriptors), getRate(maxFileDescriptors, maxFileDescriptors - openFileDescriptors)};

        log.debug(format3, (Object[]) args3);

        if ((double)openFileDescriptors / maxFileDescriptors >= properties.getFileDescriptorsUsedNoticeRate()){

            Map<String,String> argsMap = new LinkedHashMap<>(3);
            argsMap.put("事件描述", "文件描述符使用率过高（正常区间 0 ~ " + properties.getFileDescriptorsUsedNoticeRate() + "）");
            argsMap.put("上限值", args3[0]);
            argsMap.put("使用率", args3[1]);
            errorEventListener.onApplicationEvent(Events.newEvent("系统资源异常", "文件描述符", argsMap));
        }
    }

    private void dealDisk(FileSystem fileSystem) {
        //磁盘
        List<OSFileStore> fileStores = fileSystem.getFileStores();
        int max = 0;
        for (OSFileStore store : fileStores) {
            if (store.getMount().length() > max){
                max = store.getMount().length();
            }
        }
        for (OSFileStore store : fileStores) {
            String format2 = "磁盘信息：{}，总大小 {}，使用率 {} ";
            String[] args2 = {String.format("%-" + max + "s", store.getMount()),
                    FormatUtil.formatBytes(store.getTotalSpace()),
                    getRate(store.getTotalSpace(), store.getTotalSpace() - store.getUsableSpace())};
            log.debug(format2, (Object[]) args2);

            if ((double)(store.getTotalSpace() - store.getUsableSpace()) / store.getTotalSpace() >= properties.getDiskUsedNoticeRate()){
                log.warn(format2, (Object[]) args2);

                Map<String,String> argsMap = new LinkedHashMap<>(4);
                argsMap.put("事件描述", "磁盘使用率过高（正常区间 0 ~ " + properties.getDiskUsedNoticeRate() + "）");
                argsMap.put("磁盘挂载点", args2[0]);
                argsMap.put("磁盘空间", args2[1]);
                argsMap.put("使用率", args2[2]);
                errorEventListener.onApplicationEvent(Events.newEvent("系统资源异常", args2[0] + "磁盘使用率过高", argsMap));
            }
        }
    }

    private void dealSwap(GlobalMemory memory) {
        VirtualMemory virtualMemory = memory.getVirtualMemory();
        String total = FormatUtil.formatBytes(virtualMemory.getSwapTotal());
        String used = FormatUtil.formatBytes(virtualMemory.getSwapUsed());
        String usedRate = getRate(virtualMemory.getSwapTotal(), virtualMemory.getSwapUsed());

        log.debug("交换分区：总大小 {}，已使用 {}，使用率 {}", total, used, usedRate);

        if ((double)virtualMemory.getSwapUsed() / virtualMemory.getSwapTotal() >= properties.getSwapUsedNoticeRate()) {

            Map<String,String> argsMap = new LinkedHashMap<>(4);
            argsMap.put("事件描述", "交换分区使用率过高（正常区间 0 ~ " + properties.getSwapUsedNoticeRate() + "）");
            argsMap.put("总大小", total);
            argsMap.put("已使用", used);
            argsMap.put("使用率", usedRate);
            errorEventListener.onApplicationEvent(Events.newEvent("系统资源异常", "交换分区使用率", argsMap));
        }
    }

    private void dealMemory(GlobalMemory memory) {

        String format = "内存信息：总内存 {}，使用率 {}";
        String[] args = {FormatUtil.formatBytes(memory.getTotal()), getRate(memory.getTotal(), memory.getTotal() - memory.getAvailable())};
        log.debug(format, (Object[]) args);

        if ((double)(memory.getTotal() - memory.getAvailable()) / memory.getTotal() >= properties.getMemoryUsedNoticeRate()){

            Map<String,String> argsMap = new LinkedHashMap<>(3);
            argsMap.put("事件描述", "内存使用率过高（正常区间 0 ~ " + properties.getMemoryUsedNoticeRate() + "）");
            argsMap.put("总内存", args[0]);
            argsMap.put("使用率", args[1]);
            errorEventListener.onApplicationEvent(Events.newEvent("系统资源异常", "内存使用率", argsMap));
        }
    }

    private void dealProcessor(CentralProcessor processor) {
        // CPU使用率
        long[] ticks = processor.getSystemCpuLoadTicks();

        if (lastTicks == null){
            Util.sleep(1000);
            lastTicks = ticks;
            ticks = processor.getSystemCpuLoadTicks();
        }

        long user = ticks[USER.getIndex()] - lastTicks[USER.getIndex()];
        long nice = ticks[NICE.getIndex()] - lastTicks[NICE.getIndex()];
        long sys = ticks[SYSTEM.getIndex()] - lastTicks[SYSTEM.getIndex()];
        long idle = ticks[IDLE.getIndex()] - lastTicks[IDLE.getIndex()];
        long iowait = ticks[IOWAIT.getIndex()] - lastTicks[IOWAIT.getIndex()];
        long irq = ticks[IRQ.getIndex()] - lastTicks[IRQ.getIndex()];
        long softirq = ticks[SOFTIRQ.getIndex()] - lastTicks[SOFTIRQ.getIndex()];
        long steal = ticks[STEAL.getIndex()] - lastTicks[STEAL.getIndex()];
        double sum = user + nice + sys + idle + iowait + irq + softirq + steal;
        lastTicks = ticks;

        String format = "CPU使用分布：空闲 {}，内核态 {}，用户态 {}，低优先级用户态 {}，IO等待 {}，中断处理 {}，软中断处理 {}，虚拟系统占用 {}";
        String[] args = {getRate(sum, idle), getRate(sum, sys), getRate(sum, user), getRate(sum, nice),
                getRate(sum, iowait), getRate(sum, irq), getRate(sum, softirq), getRate(sum, steal)};

        log.debug(format, (Object[]) args);

        if (properties.getCpuUsedNoticeRate() > 0) {

            if (sum == 0){
                return;
            }

            //CPU 使用率达到90%时，报警
            if ((sum - idle) / sum >= properties.getCpuUsedNoticeRate()) {
                Map<String, String> argsMap = new LinkedHashMap<>(9);
                argsMap.put("事件描述", "CPU使用率过高（正常区间 0 ~ " + properties.getCpuUsedNoticeRate() + "）");
                argsMap.put("空闲", args[0]);
                argsMap.put("内核态", args[1]);
                argsMap.put("用户态", args[2]);
                argsMap.put("低优先级用户态", args[3]);
                argsMap.put("IO等待", args[4]);
                argsMap.put("中断处理", args[5]);
                argsMap.put("软中断处理", args[6]);
                argsMap.put("虚拟系统占用", args[7]);
                errorEventListener.onApplicationEvent(Events.newEvent("系统资源异常", "CPU使用率", argsMap));
            }
        }
    }

    private void dealLoadAverage(CentralProcessor processor, int logicalCount) {
        // 1、5和15分钟
        double[] loadAverage = processor.getSystemLoadAverage(3);
        if (loadAverage[0] > 0) {

            log.debug("CPU负载：1分钟 {}，5分钟 {}，15分钟 {}", loadAverage[0], loadAverage[1], loadAverage[2]);

            if (properties.getCpuLoadAverageNoticeRate() > 0) {
                double value = logicalCount * properties.getCpuLoadAverageNoticeRate();
                boolean flag = true;
                for (double v : loadAverage) {
                    if (v < value) {
                        flag = false;
                        break;
                    }
                }

                if (flag) {
                    Map<String, String> argsMap = new LinkedHashMap<>(4);
                    argsMap.put("事件描述", "CPU负载过高（正常区间 0 ~ " + value + "）");
                    argsMap.put("1分钟CPU平均负载", String.valueOf(loadAverage[0]));
                    argsMap.put("5分钟CPU平均负载", String.valueOf(loadAverage[1]));
                    argsMap.put("15分钟CPU平均负载", String.valueOf(loadAverage[2]));
                    errorEventListener.onApplicationEvent(Events.newEvent("系统资源异常", "CPU负载", argsMap));
                }
            }
        }
    }

    private String getRate(double total, double value) {
        return df.format(value / total);
    }
}
