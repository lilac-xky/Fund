package com.lilac.task;

/**
 * 收盘前提醒发送失败，携带内容留存路径，供留痕记下「失败内容在哪」
 */
public class ReminderDeliveryException extends RuntimeException {

    private final String contentPath;

    /**
     * 按原因与留存路径构造
     *
     * @param message     失败原因
     * @param contentPath 内容留存路径，无留存为空
     */
    public ReminderDeliveryException(String message, String contentPath) {
        super(message);
        this.contentPath = contentPath;
    }

    /**
     * 取内容留存路径
     *
     * @return 留存路径，无则 null
     */
    public String getContentPath() {
        return contentPath;
    }
}
