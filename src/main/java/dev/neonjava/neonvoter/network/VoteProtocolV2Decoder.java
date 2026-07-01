package dev.neonjava.neonvoter.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.neonjava.neonvoter.NeonVoter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Decodes NuVotifier v2 protocol packets.
 *
 * v2 Protocol (line-framed JSON with HMAC-SHA256 signature):
 *  First 4 bytes: payload length (int, big-endian)
 *  Next N bytes:  JSON payload
 *  Next 4 bytes:  signature length
 *  Final M bytes: HMAC-SHA256 signature (Base64)
 *
 * The JSON payload contains: "payload" (nested JSON string) with vote fields.
 * Signature is HMAC-SHA256 of the payload bytes using the shared token.
 */
public class VoteProtocolV2Decoder extends ByteToMessageDecoder {

    private static final Gson GSON = new Gson();
    private final String token;

    // Decoder states
    private enum State { WAIT_PAYLOAD_LEN, WAIT_PAYLOAD, WAIT_SIG_LEN, WAIT_SIG }
    private State state = State.WAIT_PAYLOAD_LEN;
    private int payloadLen;
    private byte[] payloadBytes;
    private int sigLen;

    public VoteProtocolV2Decoder(String token) {
        this.token = token;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        while (true) {
            switch (state) {
                case WAIT_PAYLOAD_LEN:
                    if (in.readableBytes() < 4) return;
                    payloadLen = in.readInt();
                    state = State.WAIT_PAYLOAD;
                    break;

                case WAIT_PAYLOAD:
                    if (in.readableBytes() < payloadLen) return;
                    payloadBytes = new byte[payloadLen];
                    in.readBytes(payloadBytes);
                    state = State.WAIT_SIG_LEN;
                    break;

                case WAIT_SIG_LEN:
                    if (in.readableBytes() < 4) return;
                    sigLen = in.readInt();
                    state = State.WAIT_SIG;
                    break;

                case WAIT_SIG:
                    if (in.readableBytes() < sigLen) return;
                    byte[] sigBytes = new byte[sigLen];
                    in.readBytes(sigBytes);

                    // Verify HMAC-SHA256 signature
                    if (!verifySignature(payloadBytes, sigBytes)) {
                        NeonVoter.getInstance().getLogger().warning(
                                "[NeonVoter] V2 vote signature verification FAILED from " +
                                ctx.channel().remoteAddress() + ". Check your token.");
                        ctx.close();
                        return;
                    }

                    // Parse payload JSON
                    String payloadStr = new String(payloadBytes, StandardCharsets.UTF_8);
                    JsonObject outer = GSON.fromJson(payloadStr, JsonObject.class);

                    // Inner "payload" is a JSON string (double-encoded)
                    String innerStr = outer.has("payload") ? outer.get("payload").getAsString() : payloadStr;
                    JsonObject inner = GSON.fromJson(innerStr, JsonObject.class);

                    String service   = getStr(inner, "serviceName");
                    String username  = getStr(inner, "username");
                    String address   = getStr(inner, "address");
                    long   timestamp = inner.has("timeStamp") ? inner.get("timeStamp").getAsLong()
                                                              : System.currentTimeMillis();

                    out.add(new Vote(service, username, address, timestamp));
                    // Reset for next packet on same connection
                    state = State.WAIT_PAYLOAD_LEN;
                    break;
            }
        }
    }

    private boolean verifySignature(byte[] payload, byte[] sig) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload);
            byte[] received = Base64.getDecoder().decode(sig);
            if (expected.length != received.length) return false;
            int diff = 0;
            for (int i = 0; i < expected.length; i++) diff |= expected[i] ^ received[i];
            return diff == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getStr(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsString() : "";
    }
}
