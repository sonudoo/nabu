package org.peergos.util;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import org.peergos.Client;
import org.peergos.protocol.bitswap.pb.MessageOuterClass;
import org.peergos.protocol.dht.pb.Dht;

import io.ipfs.cid.Cid;
import io.libp2p.core.PeerId;

/**
 * Logs thread traces to the trace file. This is a singleton class.
 */
public class TraceLogger {
    /**
     * Returns the singleton instance of the TraceLogger.
     */
    public static TraceLogger getInstance() {
        if (traceLogger == null) {
            traceLogger = new TraceLogger();
        }
        return traceLogger;
    }

    /**
     * Sets the identity of the current node.
     * 
     * @param peerId Identity of the current node.
     */
    public void setIdentity(PeerId peerId) {
        currentNodeId = nodeIdMap.get(peerId.toString());
    }

    /**
     * Starts recording the traces for all subsequent trace points on the current
     * thread and child threads.
     * Also propagates the trace context to servers.
     */
    public void startTrace() {
        TraceContext.setTraceId(UUID.randomUUID().toString());
    }

    /**
     * Ends recording the traces for the current thread.
     */
    public void endTrace() {
        TraceContext.clearTraceId();
    }

    /**
     * Returns the current trace id.
     */
    public String getTraceId() {
        return TraceContext.getTraceId();
    }

    /**
     * Logs (if trace context is available) the client start of a kademlia lookup.
     */
    public Dht.Message HandleKademliaClientStart(Dht.Message msg, PeerId remotePeerId) {
        if (msg.getType() != Dht.Message.MessageType.GET_PROVIDERS || !TraceContext.isSet()) {
            return msg;
        }
        writeLog(TraceType.GET_PROVIDERS_CLIENT_START, "Peer nodeId: " + nodeIdMap.get(remotePeerId.toString()));
        return msg.toBuilder().setTraceId(TraceContext.getTraceId()).build();
    }

    /**
     * Logs (if trace context is available) the server start of a kademlia lookup.
     */
    public void HandleKademliaServerStart(Dht.Message msg, PeerId remotePeerId) {
        if (msg.getType() != Dht.Message.MessageType.GET_PROVIDERS || msg.getTraceId().isEmpty()) {
            return;
        }
        TraceContext.setTraceId(msg.getTraceId());
        writeLog(TraceType.GET_PROVIDERS_SERVER_START, "Peer nodeId: " + nodeIdMap.get(remotePeerId.toString()));
    }

    /**
     * Logs (if trace context is available) the server end of a kademlia lookup.
     */
    public void HandleKademliaServerEnd(Dht.Message msg, PeerId remotePeerId) {
        if (msg.getType() != Dht.Message.MessageType.GET_PROVIDERS || !TraceContext.isSet()) {
            return;
        }
        writeLog(TraceType.GET_PROVIDERS_SERVER_END, "Peer nodeId: " + nodeIdMap.get(remotePeerId.toString()));
        TraceContext.clearTraceId();
    }

    /**
     * Logs (if trace context is available) the client end of a kademlia lookup.
     */
    public void HandleKademliaClientEnd(Dht.Message msg, PeerId remotePeerId) {
        if (msg.getType() != Dht.Message.MessageType.GET_PROVIDERS || !TraceContext.isSet()) {
            return;
        }
        writeLog(TraceType.GET_PROVIDERS_CLIENT_END, "Peer nodeId: " + nodeIdMap.get(remotePeerId.toString()));
    }

    /**
     * Logs (if trace context is available) the client start of a bitswap protocol.
     */
    public MessageOuterClass.Message HandleBitswapClientStart(MessageOuterClass.Message msg, PeerId remotePeerId) {
        if (!TraceContext.isSet()) {
            return msg;
        }
        writeLog(TraceType.BITSWAP_CLIENT_START,
                "Want " + msg.getWantlist().getEntriesCount() + " hashes. Peer nodeId: "
                        + nodeIdMap.get(remotePeerId.toString()));
        return msg.toBuilder().setTraceId(TraceContext.getTraceId()).build();
    }

    /**
     * Logs (if trace context is available) the server start or client end of a
     * bitswap protocol.
     */
    public void HandleBitswapReceive(MessageOuterClass.Message msg, PeerId remotePeerId) {
        if (msg.getTraceId().isEmpty()) {
            return;
        }
        TraceContext.setTraceId(msg.getTraceId());
        boolean isServer = msg.hasWantlist();
        writeLog(isServer ? TraceType.BITSWAP_SERVER_START : TraceType.BITSWAP_CLIENT_END,
                isServer ? "Want " + msg.getWantlist().getEntriesCount() + " hashes. Peer nodeId: "
                        + nodeIdMap.get(remotePeerId.toString())
                        : msg.getPayloadCount() + " blocks. Peer nodeId: " + nodeIdMap.get(remotePeerId.toString()));
        if (!isServer) {
            TraceContext.clearTraceId();
        }
    }

    /**
     * Logs (if trace context is available) the server end of a bitswap protocol.
     */
    public void HandleBitswapServerEnd(MessageOuterClass.Message msg, PeerId remotePeerId) {
        if (!TraceContext.isSet()) {
            return;
        }
        writeLog(TraceType.BITSWAP_SERVER_END,
                msg.getPayloadCount() + " blocks. Peer nodeId: " + nodeIdMap.get(remotePeerId.toString()));
        TraceContext.clearTraceId();
    }

    /**
     * Logs (if trace context is available) the start of a file read.
     * 
     * @param cid Content id of the file.
     */
    public void HandleFileReadStart(Cid cid) {
        if (TraceContext.isSet()) {
            writeLog(TraceType.READ_FROM_FILE_STORE_START, "Cid: " + cid.toString());
        }
    }

    /**
     * Logs (if trace context is available) the end of a file read.
     * 
     * @param cid Content id of the file.
     */
    public void HandleFileReadEnd(Cid cid) {
        if (TraceContext.isSet()) {
            writeLog(TraceType.READ_FROM_FILE_STORE_END, "Cid: " + cid.toString());
        }
    }

    // -------------- PRIVATE MEMBERS -------------

    /**
     * Holds the context for the current thread trace. The context contains the
     * trace Id.
     */
    private static class TraceContext {
        private static final ThreadLocal<String> traceId = new InheritableThreadLocal<String>() {
            @Override
            protected String initialValue() {
                return "";
            }
        };

        public static boolean isSet() {
            return !traceId.get().isEmpty();
        }

        public static String getTraceId() {
            return traceId.get();
        }

        public static void setTraceId(String newTraceId) {
            traceId.set(newTraceId);
        }

        public static void clearTraceId() {
            traceId.set("");
        }
    }

    /**
     * Represents different types of log points that are captured by the trace
     * logger.
     */
    private enum TraceType {
        // Catch all.
        UNKNOWN,

        // Kademlia lookup.
        GET_PROVIDERS_CLIENT_START,
        GET_PROVIDERS_SERVER_START,
        GET_PROVIDERS_SERVER_END,
        GET_PROVIDERS_CLIENT_END,

        // Bitswap.
        BITSWAP_CLIENT_START,
        BITSWAP_SERVER_START,
        BITSWAP_SERVER_END,
        BITSWAP_CLIENT_END,

        // File read.
        READ_FROM_FILE_STORE_START,
        READ_FROM_FILE_STORE_END,
    };

    private static TraceLogger traceLogger = null;

    // Maps peer Id (as string) to node Id.
    private HashMap<String, Integer> nodeIdMap;
    private int currentNodeId;

    FileOutputStream outFile = null;
    BufferedOutputStream output = null;
    Thread flusher;

    private TraceLogger() {
        // TODO(sonudoo): Remove a hardcoded mapping of peer Id to node Id.
        nodeIdMap = new HashMap<>();
        nodeIdMap.put("12D3KooWMDHFtZjTZ6Hdi5WEQPBxJp7gT4yqtQkne6DCqiQCBP73", 0);
        nodeIdMap.put("12D3KooWNdeb2dTYZ9X4T1PREUoFgR8f3yrGbMjMsd8PSZgcBZ3s", 1);
        nodeIdMap.put("12D3KooWNvMgnv9iHJDcEKRriGc7zT5NcTwffaKE1XgZRUZ1BDPe", 2);
        nodeIdMap.put("12D3KooWHA6vxTu6F2grKnErGsZMMJiBXVJvAoCQzhKM5KcFgrDR", 3);
        nodeIdMap.put("12D3KooWDbhwoVhREeDsZ8HHj6LfsykRJnG7TA8wEfXGQfnhiSBf", 4);
        nodeIdMap.put("12D3KooWT1gBjWRTJuyqGa4dRuHyexGUBAR2Psoh6hqqAGej6Bc5", 5);
        nodeIdMap.put("12D3KooWH8vCRRoH73tV7KaXf6o1dzJms7FxH7hd3M3eYSjF9zC7", 6);
        nodeIdMap.put("12D3KooWHyor6CQ21vMhtSJ2qb1YEBfv9vgpe1VgA5iMYT4DTd9T", 7);
        nodeIdMap.put("12D3KooWJWcBwDHBo7ecoBCsT8FJWvQax4Wmn2iKTCvG6uhLKZN6", 8);
        nodeIdMap.put("12D3KooWCfWmdJYdAUwm1pTxFKsRDRzGmsbeUMpriNNocMDVMmum", 9);
        currentNodeId = -1;

        try {
            outFile = new FileOutputStream(Client.DEFAULT_IPFS_DIR_PATH.toAbsolutePath().toString() + "/trace.log",
                    /* append= */ true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
        // An output stream with 100 MB buffer.
        output = new BufferedOutputStream(outFile, 100 * 1024 * 1024);
        flusher = new Thread(new Thread(new Runnable() {
            public void run() {
                try {
                    flushLogs();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }));
        flusher.start();
    }

    private void writeLog(TraceType type, String debugDetails) {
        StringBuilder builder = new StringBuilder();
        builder.append(TraceContext.getTraceId() + "\t");
        builder.append(currentNodeId + "\t");
        builder.append(Thread.currentThread().getId() + "\t");
        long currentTimeMillis = System.currentTimeMillis();
        builder.append(currentTimeMillis + "\t");
        builder.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(currentTimeMillis)) + "\t");
        builder.append(type.name() + "\t");
        builder.append(debugDetails);
        builder.append("\n");
        String log = builder.toString();
        // TODO(sonudoo): Turn off logging to stdout.
        System.out.print(log);

        synchronized (this) {
            try {
                // TODO(millerm): Rollover to a new log file after some limit.
                output.write(log.getBytes(Charset.forName("UTF-8")));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private void flushLogs() throws IOException {
        while (true) {
            try {
                // Flush every 100 seconds.
                Thread.sleep(1000);
                synchronized (this) {
                    output.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
        synchronized (this) {
            output.close();
            outFile.close();
        }
    }
}
