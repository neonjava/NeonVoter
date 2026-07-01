package dev.neonjava.neonvoter.network;

import dev.neonjava.neonvoter.NeonVoter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringEncoder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The embedded Netty vote server.
 *
 * Listens on a configurable port for vote notifications from voting sites.
 * Supports both Votifier v1 (RSA encrypted) and v2 (JSON + HMAC) protocols.
 *
 * On connect, sends the v1 greeting: "VOTIFIER <version> <pubKeyBase64>\n"
 * which tells the client which protocol version to use.
 *
 * This runs on dedicated Netty threads, completely separate from the main server thread.
 * Thread-safe to use with Folia.
 */
public class VoteServer {

    private static final boolean USE_EPOLL = Epoll.isAvailable();

    private final String host;
    private final int port;
    private final KeyPair keyPair;
    private final String token;
    private final boolean disableV1;
    private final Consumer<Vote> voteConsumer;
    private final Logger logger;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public VoteServer(String host, int port, KeyPair keyPair, String token,
                      boolean disableV1, Consumer<Vote> voteConsumer, Logger logger) {
        this.host = host;
        this.port = port;
        this.keyPair = keyPair;
        this.token = token;
        this.disableV1 = disableV1;
        this.voteConsumer = voteConsumer;
        this.logger = logger;
    }

    /** Start the server. Calls onStart with null on success or the error on failure. */
    public void start(Consumer<Throwable> onStart) {
        ThreadFactory bossFactory   = r -> { Thread t = new Thread(r, "NeonVoter-Boss");   t.setDaemon(true); return t; };
        ThreadFactory workerFactory = r -> { Thread t = new Thread(r, "NeonVoter-Worker"); t.setDaemon(true); return t; };

        if (USE_EPOLL) {
            bossGroup   = new EpollEventLoopGroup(1, bossFactory);
            workerGroup = new EpollEventLoopGroup(3, workerFactory);
            logger.info("[NeonVoter] Using epoll transport.");
        } else {
            bossGroup   = new NioEventLoopGroup(1, bossFactory);
            workerGroup = new NioEventLoopGroup(3, workerFactory);
            logger.info("[NeonVoter] Using NIO transport.");
        }

        // Build the greeting string sent to connecting clients
        String pubKeyBase64 = java.util.Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded());
        String greeting = "VOTIFIER 2 " + pubKeyBase64 + "\n";

        VoteInboundHandler inboundHandler = new VoteInboundHandler(voteConsumer, logger);
        KeyPair kp = this.keyPair;
        String tok = this.token;
        boolean noV1 = this.disableV1;

        new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(USE_EPOLL ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 1. On connect, send the greeting
                        ch.pipeline().addLast("greeter",
                                new GreetingHandler(greeting));
                        // 2. Differentiate v1 vs v2 based on client response
                        ch.pipeline().addLast("differentiator",
                                new ProtocolDifferentiator(kp, tok, noV1));
                        // 3. Handle decoded Vote objects
                        ch.pipeline().addLast("voteHandler", inboundHandler);
                    }
                })
                .bind(host, port)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        serverChannel = future.channel();
                        logger.info("[NeonVoter] Vote server listening on " + host + ":" + port);
                        onStart.accept(null);
                    } else {
                        InetSocketAddress addr = new InetSocketAddress(host, port);
                        logger.severe("[NeonVoter] Could not bind vote server to " + addr + ": " +
                                future.cause().getMessage());
                        onStart.accept(future.cause());
                    }
                });
    }

    /** Gracefully shut down the server. */
    public void shutdown() {
        if (serverChannel != null) {
            try { serverChannel.close().syncUninterruptibly(); } catch (Exception ignored) {}
        }
        if (bossGroup   != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        logger.info("[NeonVoter] Vote server shut down.");
    }

    public boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }
}
