package dev.neonjava.neonvoter.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.security.KeyPair;

/**
 * Reads the first bytes from the client to decide v1 vs v2 protocol,
 * then replaces itself with the appropriate decoder.
 *
 * v2 clients send '{' (JSON) as first byte.
 * v1 clients immediately send the 256-byte RSA-encrypted block.
 */
public class ProtocolDifferentiator extends ChannelInboundHandlerAdapter {

    private final KeyPair keyPair;
    private final String token;
    private final boolean disableV1;

    public ProtocolDifferentiator(KeyPair keyPair, String token, boolean disableV1) {
        this.keyPair = keyPair;
        this.token = token;
        this.disableV1 = disableV1;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        if (!buf.isReadable()) {
            buf.release();
            return;
        }

        byte firstByte = buf.getByte(buf.readerIndex());

        if (firstByte == '{') {
            // NuVotifier v2 — JSON payload
            ctx.pipeline().replace(this, "v2decoder", new VoteProtocolV2Decoder(token));
        } else {
            // Votifier v1 — RSA-encrypted block
            if (disableV1) {
                ctx.close();
                buf.release();
                return;
            }
            ctx.pipeline().replace(this, "v1decoder", new VoteProtocolV1Decoder(keyPair));
        }

        // Pass the buffered bytes downstream to the new decoder
        ctx.fireChannelRead(buf);
    }
}
