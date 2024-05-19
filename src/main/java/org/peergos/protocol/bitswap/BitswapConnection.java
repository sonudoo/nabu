package org.peergos.protocol.bitswap;

import io.libp2p.core.Stream;
import io.prometheus.client.*;
import kotlin.*;
import org.peergos.protocol.bitswap.pb.*;
import org.peergos.util.TraceLogger;

import java.util.concurrent.*;

public class BitswapConnection implements BitswapController {

    private final Stream conn;
    private final Counter sentBytes;

    public BitswapConnection(Stream conn, Counter sentBytes) {
        this.conn = conn;
        this.sentBytes = sentBytes;
    }

    @Override
    public void send(MessageOuterClass.Message msg) {
        msg = TraceLogger.getInstance().HandleBitswapClientStart(msg, conn.remotePeerId());
        conn.writeAndFlush(msg);
        sentBytes.inc(msg.getSerializedSize());
    }

    @Override
    public CompletableFuture<Unit> close() {
        return conn.close();
    }
}
