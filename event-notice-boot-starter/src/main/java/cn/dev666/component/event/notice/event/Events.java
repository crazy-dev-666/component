package cn.dev666.component.event.notice.event;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Events {

    public static DefaultNoticeEvent newEvent(String type, String uniqueErrorCode, String key, String value){
        Map<String,String> argsMap = new HashMap<>(1);
        argsMap.put(key, value);
        return newEvent(type, uniqueErrorCode, argsMap);
    }

    public static DefaultNoticeEvent newEvent(String type, String uniqueErrorCode,
                                              String key1, String value1, String key2, String value2){
        Map<String,String> argsMap = new LinkedHashMap<>(2);
        argsMap.put(key1, value1);
        argsMap.put(key2, value2);
        return newEvent(type, uniqueErrorCode, argsMap);
    }

    /**
     * @param type             业务场景类型
     * @param uniqueErrorCode   唯一错误码，用于区分同一场景中，错误是否相同及错误数累计。为空时，则根据入参区分
     * @param argsMap           业务参数
     */
    public static DefaultNoticeEvent newEvent(String type, String uniqueErrorCode, Map<String,String> argsMap){
        return new DefaultNoticeEvent(type, uniqueErrorCode, argsMap);
    }

    /**
     * 异常事件
     *
     * @param url   请求URL
     * @param e     异常
     */
    public static DefaultNoticeEvent exceptionEvent(String url, Exception e){
        Map<String,String> argsMap = new LinkedHashMap<>(2);
        argsMap.put("请求URL", url);
        argsMap.put("异常详情", getStackTrace(e, 1024));
        return newEvent("系统异常", e.getMessage(), argsMap);
    }

    /**
     * 请求耗时过长事件
     *
     * @param url       请求URL
     * @param time      耗时
     * @param params    请求参数
     */
    public static DefaultNoticeEvent slowRequestEvent(String url, long time, String... params){
        Map<String,String> argsMap = new LinkedHashMap<>(3);
        argsMap.put("请求URL", url);
        argsMap.put("请求参数", Arrays.deepToString(params));
        argsMap.put("请求耗时", time + " ms");
        return newEvent("处理请求耗时过长", url, argsMap);
    }

    /**
     * 请求第三方耗时过长事件
     *
     * @param url       请求URL
     * @param time      耗时
     * @param params    请求参数
     */
    public static DefaultNoticeEvent slowThirdRequestEvent(String url, long time, String... params){
        Map<String,String> argsMap = new LinkedHashMap<>(3);
        argsMap.put("三方请求URL", url);
        argsMap.put("请求参数", Arrays.deepToString(params));
        argsMap.put("三方请求耗时", time + " ms");
        return newEvent("请求三方耗时过长", url, argsMap);
    }

    /**
     * 慢SQL事件
     *
     * @param sql       SQL语句
     * @param time      耗时
     * @param params    参数
     */
    public static DefaultNoticeEvent slowSqlEvent(String sql, long time, String... params){
        Map<String,String> argsMap = new LinkedHashMap<>(3);
        argsMap.put("SQL", sql);
        argsMap.put("SQL参数", Arrays.deepToString(params));
        argsMap.put("SQL耗时", time + " ms");
        return newEvent("SQL执行耗时过长", sql, argsMap);
    }

    /**
     * 任务耗时过长事件
     *
     * @param taskName  任务名
     * @param time      耗时
     * @param params    参数
     */
    public static DefaultNoticeEvent slowTaskEvent(String taskName, long time, String... params){
        Map<String,String> argsMap = new LinkedHashMap<>(3);
        argsMap.put("任务名称", taskName);
        argsMap.put("任务参数", Arrays.deepToString(params));
        argsMap.put("执行耗时", time + " ms");
        return newEvent("任务执行耗时过长", taskName, argsMap);
    }

    /**
     * 线程池任务堆积事件
     *
     * @param poolName      线程池名
     * @param totalThread   总线程数
     * @param usedThread    实时使用线程数
     * @param queueTasks    任务队列
     * @param taskNumMap    队列中任务分布
     */
    public static DefaultNoticeEvent poolTaskHeapUpEvent(String poolName, Integer totalThread,
                                                         Integer usedThread, Integer queueTasks, Map<String,Integer> taskNumMap){
        boolean flag = taskNumMap == null || taskNumMap.size() == 0;
        Map<String,String> argsMap = new LinkedHashMap<>(4 + (flag ? 0 : taskNumMap.size() + 1));
        argsMap.put("线程池名", poolName);
        argsMap.put("线程总数", String.valueOf(totalThread));
        argsMap.put("使用线程数",  String.valueOf(usedThread));
        argsMap.put("任务排队数", String.valueOf(queueTasks));

        if (!flag) {
            argsMap.put("任务分布详情", "");
            for (Map.Entry<String, Integer> entry : taskNumMap.entrySet()) {
                argsMap.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return newEvent("线程池任务堆积", poolName, argsMap);
    }


    /**
     * 获取堆栈信息
     */
    private static String getStackTrace(Throwable throwable, int maxSize){
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
            if (maxSize > 0) {
                return sw.toString().substring(0, maxSize);
            }
            return sw.toString();
        }
    }
}
