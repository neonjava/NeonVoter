package dev.neonjava.neonvoter.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.nio.charset.StandardCharsets;

/**
 * Sends the Votifier greeting as soon as a client connects, then removes itself from the pipeline.
 */
@io.netty.channel.ChannelHandler.Sharable
public class GreetingHandler extends ChannelInboundHandlerAdapter {

    private final byte[] greeting;

    public GreetingHandler(String greeting) {
        this.greeting = greeting.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ByteBuf buf = Unpooled.wrappedBuffer(greeting);
        ctx.writeAndFlush(buf);
        ctx.pipeline().remove(this);
        ctx.fireChannelActive();
    }
}
