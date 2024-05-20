package org.peergos.net;

import io.ipfs.cid.Cid;

import org.apache.commons.io.IOUtils;
import org.peergos.*;
import org.peergos.util.*;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class APIHandler extends Handler {
    public static final String API_URL = "/api/v0/";
    public static final Version CURRENT_VERSION = Version.parse("0.7.7");
    public static final String GET = "block/get";
    public static final String PUT = "block/put";

    private final EmbeddedIpfs ipfs;
    private final int maxBlockSize;
    private final TraceLogger traceLogger;

    public APIHandler(EmbeddedIpfs ipfs) {
        this.ipfs = ipfs;
        this.maxBlockSize = ipfs.maxBlockSize();
        this.traceLogger = TraceLogger.getInstance();
    }

    public void handleCallToAPI(HttpExchange httpExchange) {
        String path = httpExchange.getRequestURI().getPath();
        try {
            if (!path.startsWith(API_URL))
                throw new IllegalStateException("Unsupported api version, required: " + API_URL);
            path = path.substring(API_URL.length());
            Map<String, List<String>> params = HttpUtil.parseQuery(httpExchange.getRequestURI().getQuery());

            switch (path) {
                case GET: {
                    List<String> cid = params.get("cid");
                    if (cid == null || cid.size() != 1) {
                        throw new APIException("argument \"cid\" is required");
                    }
                    List<String> trace = params.get("trace");
                    boolean shouldTrace = trace != null && !trace.isEmpty();
                    if (shouldTrace)
                        traceLogger.startTrace();
                    List<HashedBlock> block = ipfs.getBlocks(List.of(new Want(Cid.decode(cid.get(0)))),
                            new HashSet<>(),
                            /* addToLocal= */ false);
                    if (shouldTrace)
                        traceLogger.endTrace();
                    if (!block.isEmpty()) {
                        replyBytes(httpExchange, block.get(0).block);
                    } else {
                        try {
                            httpExchange.sendResponseHeaders(400, 0);
                        } catch (IOException ioe) {
                            HttpUtil.replyError(httpExchange, ioe);
                        }
                    }
                    break;
                }
                case PUT: {
                    String result = IOUtils.toString(httpExchange.getRequestBody(), StandardCharsets.UTF_8);
                    byte[] block = result.getBytes();
                    if (block.length == 0 || block.length > maxBlockSize) {
                        throw new APIException("Block missing or too large");
                    }
                    Cid cid = ipfs.blockstore.put(block, Cid.Codec.Raw).join();
                    Map res = new HashMap<>();
                    res.put("cid", cid.toString());
                    replyJson(httpExchange, JSONParser.toString(res));
                    break;
                }
                default: {
                    httpExchange.sendResponseHeaders(404, 0);
                    break;
                }
            }
        } catch (Exception e) {
            HttpUtil.replyError(httpExchange, e);
        } finally {
            httpExchange.close();
        }
    }
}
