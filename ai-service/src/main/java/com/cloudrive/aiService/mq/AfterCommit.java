package com.cloudrive.aiService.mq;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后再执行旁路动作。
 * <p>
 * 索引同步属于旁路能力，必须在本地事务成功提交之后再做：
 * 这两个写文件的服务里都带 {@code userSpaceClient} 的远程调用，它失败会回滚事务；
 * 如果消息先发出去，就会出现「索引删了但行还在」「索引改名了但库里还是旧名」——
 * 一个指向错误状态的索引，比一个落后的索引更难排查。
 * </p>
 * <p>
 * 最坏情况变成「索引落后」，可由索引重建任务补齐，方向是对的。
 * </p>
 *
 * @author 飞飞
 * @date 2026/11/9
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    /**
     * 当前存在事务则注册为提交后回调，否则立即执行。
     *
     * @param action 待执行动作
     */
    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
