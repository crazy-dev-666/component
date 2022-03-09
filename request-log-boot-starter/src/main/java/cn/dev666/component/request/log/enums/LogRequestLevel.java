package cn.dev666.component.request.log.enums;

public enum LogRequestLevel {
    /**
     * 什么都不输出
     */
    NOTHING,
    /**
     * 仅输出URL
     */
    URL,
    /**
     * 输出URL和请求体
     */
    URL_BODY,
    /**
     * 输出URL和请求体，及部分请求头
     */
    URL_BODY_SOME_HEADER,
    /**
     * 输出请求全部信息
     */
    ALL
}
