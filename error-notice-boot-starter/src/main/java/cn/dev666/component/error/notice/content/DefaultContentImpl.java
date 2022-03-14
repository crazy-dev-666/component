package cn.dev666.component.error.notice.content;

import cn.dev666.component.error.notice.event.DefaultErrorEvent;
import cn.dev666.component.error.notice.listener.ErrorEventListener;
import cn.dev666.component.error.notice.utils.IpUtils;
import lombok.AllArgsConstructor;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@AllArgsConstructor
public class DefaultContentImpl implements Content<DefaultErrorEvent> {

    private static final String BASE_TEMPLATE = "\n 机器IP  ：{0} \n 进程ID ：{1} \n 应用名称 ：{2} \n\n 错误时间 ：{3} \n 错误频次 ：{4} \n\n";

    private final String profiles;

    private final String applicationName;

    @Override
    public ContentResult get(DefaultErrorEvent event, ErrorEventListener.DealEventResult result) {
        String title = profiles + " 环境, "+ event.getType() +" 报警";
        String processId;
        try {
            RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            processId = bean.getName().split("@")[0];
        }catch (Exception e){
            processId = "-1";
        }

        Object[] commonArgs = new Object[]{IpUtils.getLocalIp(), processId, applicationName,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(event.getTimestamp())),
                result.getFrequency()};

        String baseContent = MessageFormat.format(BASE_TEMPLATE, commonArgs);

        Map<String, String> argsMap = event.getArgsMap();
        StringBuilder sb = new StringBuilder();
        if (argsMap != null && argsMap.size() > 0) {
            for (Map.Entry<String, String> entry : argsMap.entrySet()) {
                sb.append(" ").append(entry.getKey()).append(" : ").append(entry.getValue()).append(" \n");
            }
        }
        String content =  baseContent + sb.toString();
        return new DefaultContentResult(title, content);
    }
}
