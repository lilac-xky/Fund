package com.lilac.task.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 单个时点产出物的执行结果，供编排层决定写什么留痕
 */
@Data
public class PointOutcome implements Serializable {

    /**
     * 产出是否成功
     */
    private boolean success = true;

    /**
     * 产出摘要，成功时写进页面提示
     */
    private String detail;

    /**
     * 内容留存路径，邮件等有正文的时点填写，相对 data 目录
     */
    private String contentPath;

    /**
     * 产出失败的说明
     */
    private String failReason;

    private static final long serialVersionUID = 1L;

    /**
     * 构造成功结果
     *
     * @param detail 产出摘要
     * @return 成功结果
     */
    public static PointOutcome ok(String detail) {
        PointOutcome outcome = new PointOutcome();
        outcome.setSuccess(true);
        outcome.setDetail(detail);
        return outcome;
    }

    /**
     * 构造失败结果
     *
     * @param failReason 失败说明
     * @return 失败结果
     */
    public static PointOutcome fail(String failReason) {
        PointOutcome outcome = new PointOutcome();
        outcome.setSuccess(false);
        outcome.setFailReason(failReason);
        return outcome;
    }
}
