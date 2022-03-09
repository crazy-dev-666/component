package cn.dev666.component.request.log.enums;

public enum LogResponseLevel {
    /**
     * 什么都不输出
     */
    NOTHING,
    /**
     * 输出错误响应
     */
    ERROR,
    /**
     * 输出错误响应
     */
    ERROR_NOBODY,
    /**
     * 输出慢响应和错误响应
     */
    SLOW_ERROR,
    /**
     * 输出慢响应和错误响应
     */
    SLOW_ERROR_NOBODY,
    /**
     * 输出所有响应体，适合开发环境
     */
    ALL
}
