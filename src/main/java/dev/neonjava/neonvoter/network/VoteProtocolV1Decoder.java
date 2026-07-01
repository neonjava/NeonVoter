package dev.neonjava.neonvoter.network;

import dev.neonjava.neonvoter.NeonVoter;
import dev.neonjava.neonvoter.crypto.RSAUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.List;

/**
 * Decodes Votifier v1 protocol packets.
 *
 * v1 Protocol: voting site sends a 256-byte RSA-encrypted block containing:
 *   "VOTE\n<serviceName>\n<username>\n<address>\n<timestamp>\n"
 *
 * The block is decrypted with the server's RSA private key.
 */
public class VoteProtocolV1Decoder extends ByteToMessageDecoder {

    private static final int BLOCK_SIZE = 256;
    private final KeyPair keyPair;

    public VoteProtocolV1Decoder(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < BLOCK_SIZE) return;

        byte[] block = new byte[BLOCK_SIZE];
        in.readBytes(block);

        byte[] decrypted;
        try {
            decrypted = RSAUtils.decrypt(block, keyPair.getPrivate());
        } catch (Exception e) {
            NeonVoter.getInstance().getLogger().warning(
                    "[NeonVoter] Failed to decrypt v1 vote from " + ctx.channel().remoteAddress() +
                    " — wrong RSA key or corrupted packet.");
            ctx.close();
            return;
        }

        String data = new String(decrypted, StandardCharsets.UTF_8).trim();
        String[] parts = data.split("\n");

        // Expected: VOTE, serviceName, username, address, timestamp
        if (parts.length < 5 || !"VOTE".equals(parts[0])) {
            NeonVoter.getInstance().getLogger().warning(
                    "[NeonVoter] Malformed v1 vote packet from " + ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        String service   = parts[1].trim();
        String username  = parts[2].trim();
        String address   = parts[3].trim();
        long   timestamp;
        try {
            timestamp = Long.parseLong(parts[4].trim());
        } catch (NumberFormatException e) {
            timestamp = System.currentTimeMillis();
        }

        out.add(new Vote(service, username, address, timestamp));
    }
}
