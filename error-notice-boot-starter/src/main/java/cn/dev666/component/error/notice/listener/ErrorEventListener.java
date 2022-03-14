package cn.dev666.component.error.notice.listener;

import cn.dev666.component.error.notice.config.ErrorNoticeProperties;
import cn.dev666.component.error.notice.event.DefaultErrorEvent;
import cn.dev666.component.error.notice.mail.MailService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ErrorEventListener {

    //累计产生1w次错误后，进行清理操作
    private static final int CLEAN_THRESHOLD = 10000;

    //最多保留错误数上限。
    private static final int ERROR_THRESHOLD = 10000;

    private AtomicInteger cleanCount = new AtomicInteger();

    //实时错误信息。
    private Map<String, ErrorInfo> errorInfoMap = new HashMap<>();

    private final ThreadPoolTaskExecutor eventExecutor;

    private final ErrorNoticeProperties properties;

    private final List<String> profiles;

    private final MailService mailService;

    private final String applicationName;

    public ErrorEventListener(ThreadPoolTaskExecutor eventExecutor, ErrorNoticeProperties properties, List<String> profiles, MailService mailService, String applicationName) {
        this.eventExecutor = eventExecutor;
        this.properties = properties;
        this.profiles = profiles;
        this.mailService = mailService;
        this.applicationName = applicationName;
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
    public void onApplicationEvent(DefaultErrorEvent event) {

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

            String title = profiles + " 环境, "+ event.getType() +" 报警";
            String finalContext = getContext(event, result);
            sendEmail(event.getType(), title, finalContext);

            //TODO 增加多种提醒方式
        }
        cleanCheck();
    }

    private String getContext(DefaultErrorEvent event, DealEventResult result) {

        String processId;
        try {
            RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            processId = bean.getVmName().split("@")[0];
        }catch (Exception e){
            processId = "-1";
        }

        Object[] commonArgs = new Object[]{getLocalIp(), applicationName, processId,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(event.getTimestamp())),
                result.frequency};

        String baseTemplate = "\n 机器IP  ：{0} \n 应用名称 ：{1} \n 进程ID  ：{2} \n 错误时间 ：{3} \n 错误频次 ：{4} \n\n";
        String baseContext = MessageFormat.format(baseTemplate, commonArgs);

        Map<String, String> argsMap = event.getArgsMap();
        StringBuilder sb = new StringBuilder();
        if (argsMap != null && argsMap.size() > 0) {
            for (Map.Entry<String, String> entry : argsMap.entrySet()) {
                sb.append(" ").append(entry.getKey()).append(" : ").append(entry.getValue()).append(" \n ");
            }
        }
        return baseContext + sb.toString();
    }

    private boolean sendEmail(String scene, String title, String context) {
        String[] to = properties.getEmail() == null ? null : properties.getEmail().getTo();
        if (to != null && to.length == 0) {
            log.debug("未发送场景 {} 错误邮件提醒，没有配置收件人", scene);
            return false;
        }
        eventExecutor.execute(()-> mailService.send(to, properties.getEmail().getCc(),
                title, context));
        return true;
    }

    private String getKey(DefaultErrorEvent event) {
        return event.getType() + ":" + event.getUniqueErrorCode();
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


    @AllArgsConstructor
    private static class DealEventResult {
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

    /**
     * 获取当前机器的IP
     */
    private static String getLocalIp() {
        try {
            InetAddress candidateAddress = null;
            // 遍历所有的网络接口
            for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements();) {
                NetworkInterface anInterface = interfaces.nextElement();
                // 在所有的接口下再遍历IP
                for (Enumeration<InetAddress> addresses = anInterface.getInetAddresses(); addresses.hasMoreElements();) {
                    InetAddress address = addresses.nextElement();
                    // 排除loopback类型地址
                    if (!address.isLoopbackAddress()) {
                        if (address.isSiteLocalAddress()) {
                            // 如果是site-local地址，就是它了
                            return address.getHostAddress();
                        } else if (candidateAddress == null) {
                            // site-local类型的地址未被发现，先记录候选地址
                            candidateAddress = address;
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                return candidateAddress.getHostAddress();
            }
            // 如果没有发现 non-loopback地址.只能用最次选的方案
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null) {
                return "";
            }
            return jdkSuppliedAddress.getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }

}