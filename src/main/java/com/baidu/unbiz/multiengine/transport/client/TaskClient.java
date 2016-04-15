package com.baidu.unbiz.multiengine.transport.client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.baidu.unbiz.multiengine.dto.Signal;
import com.baidu.unbiz.multiengine.dto.TaskCommand;
import com.baidu.unbiz.multiengine.exception.MultiEngineException;

import io.netty.channel.Channel;

public final class TaskClient extends AbstractTaskClient {

    private static final Log LOG = LogFactory.getLog(TaskClient.class);

    private CountDownLatch initDone = new CountDownLatch(1);

    public void stop() {
        Channel channel = TaskClientContext.sessionChannelMap.get(sessionKey);
        if (channel == null) {
            return;
        }
        channel.close();
    }

    public void start() {
        final AbstractTaskClient client = this;
        new Thread() {
            @Override
            public void run() {
                try {
                    client.doStart();
                } catch (Exception e) {
                    LOG.error("client run fail:", e);
                }
            }
        }.start();
        try {
            initDone.await();
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    public void callbackPostInit() {
        initDone.countDown();
    }

    public <T> T call(TaskCommand command) {
        SendFuture future = asynCall(command);
        if (future instanceof IdentitySendFuture) {
            return waitResult(((IdentitySendFuture) future).getId());
        }
        throw new MultiEngineException("support IdentitySendFuture only");
    }

    public SendFuture asynCall(TaskCommand command) {
        long seqId = idGen.genId();

        Signal<TaskCommand> signal = new Signal<TaskCommand>(command);
        signal.setSeqId(seqId);

        Channel channel = TaskClientContext.sessionChannelMap.get(sessionKey);
        channel.writeAndFlush(signal);

        SendFuture sendFutrue = new IdentitySendFuture(seqId);

        TaskClientContext.placeSessionResult(sessionKey, seqId, sendFutrue);
        return sendFutrue;
    }

    private <T> T waitResult(long seqId) {
        SendFuture sendFutrue = TaskClientContext.getSessionResult(sessionKey, seqId);
        T result = (T) sendFutrue.get();
        TaskClientContext.removeSessionResult(sessionKey, seqId);
        return result;
    }

}