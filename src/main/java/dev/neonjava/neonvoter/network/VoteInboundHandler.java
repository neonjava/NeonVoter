package dev.neonjava.neonvoter.network;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Final Netty handler that receives decoded {@link Vote} objects and passes them
 * to the plugin's vote consumer (RewardManager).
 */
@ChannelHandler.Sharable
public class VoteInboundHandler extends SimpleChannelInboundHandler<Vote> {

    private final Consumer<Vote> voteConsumer;
    private final Logger logger;

    public VoteInboundHandler(Consumer<Vote> voteConsumer, Logger logger) {
        this.voteConsumer = voteConsumer;
        this.logger = logger;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Vote vote) {
        logger.info("[NeonVoter] Received vote from " + ctx.channel().remoteAddress() +
                " — " + vote);
        try {
            voteConsumer.accept(vote);
        } catch (Exception e) {
            logger.severe("[NeonVoter] Error processing vote: " + e.getMessage());
        } finally {
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warning("[NeonVoter] Network error from " + ctx.channel().remoteAddress() +
                ": " + cause.getMessage());
        ctx.close();
    }
}
