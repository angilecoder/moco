package com.github.dreamhead.moco.internal;

import com.github.dreamhead.moco.MocoMonitor;
import com.github.dreamhead.moco.SocketRequest;
import com.github.dreamhead.moco.SocketResponse;
import com.github.dreamhead.moco.SocketResponseSetting;
import com.github.dreamhead.moco.model.DefaultSocketRequest;
import com.github.dreamhead.moco.model.DefaultSocketResponse;
import com.github.dreamhead.moco.setting.Setting;
import com.google.common.collect.ImmutableList;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import static java.lang.String.format;

public class MocoSocketHandler extends SimpleChannelInboundHandler<String> {
    private final ImmutableList<Setting<SocketResponseSetting>> settings;
    private final Setting<SocketResponseSetting> anySetting;
    private final MocoMonitor monitor;

    public MocoSocketHandler(ActualSocketServer server) {
        this.settings = server.getSettings();
        this.anySetting = server.getAnySetting();
        this.monitor = server.getMonitor();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        try {
            SocketRequest request = new DefaultSocketRequest(msg);
            this.monitor.onMessageArrived(request);
            SocketResponse response = new DefaultSocketResponse();
            handleSession(new SessionContext(request, response));
            this.monitor.onMessageLeave(response);
            ctx.writeAndFlush(response.getContent());
        } catch (Exception e) {
            this.monitor.onException(e);
        }
    }

    private void handleSession(SessionContext context) {
        for (Setting setting : settings) {
            if (setting.match(context.getRequest())) {
                setting.writeToResponse(context);
                return;
            }
        }

        if (anySetting.match(context.getRequest())) {
            anySetting.writeToResponse(context);
            return;
        }

        this.monitor.onUnexpectedMessage(context.getRequest());
        throw new RuntimeException(format("No handler found for request: %s", context.getRequest().getContent()));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        monitor.onException(cause);
    }
}
