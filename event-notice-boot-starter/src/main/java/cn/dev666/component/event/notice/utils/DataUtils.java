package cn.dev666.component.event.notice.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

public class DataUtils {

    private static final String APPLICATION_TEMPLATE = "\n 机器IP  ：{0} \n 进程ID ：{1} \n 应用名称 ：{2} \n";

    private static String profiles;

    private static String applicationName;

    private static String localIp;

    private static String processId;

    public static void init(String profiles, String applicationName){
        localIp = IpUtils.getLocalIp();
        try {
            RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            processId = bean.getName().split("@")[0];
        }catch (Exception e){
            processId = "-1";
        }

        DataUtils.profiles = profiles;
        DataUtils.applicationName = applicationName;
    }

    public static String getApplicationInfo(){
        return getApplicationInfo(APPLICATION_TEMPLATE);
    }

    public static Map<String, String> getInfo(){
        Map<String, String> map = new HashMap<>();
        map.put("localIp", localIp);
        map.put("processId", processId);
        map.put("applicationName", applicationName);
        return map;
    }

    public static String getApplicationInfo(String template){
        Object[] commonArgs = new Object[]{localIp, processId, applicationName};
        return MessageFormat.format(APPLICATION_TEMPLATE, commonArgs);
    }

    public static String getProfiles() {
        return profiles;
    }
}
