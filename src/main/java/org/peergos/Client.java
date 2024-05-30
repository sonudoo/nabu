package org.peergos;

import com.sun.net.httpserver.HttpServer;

import io.ipfs.multiaddr.MultiAddress;

import org.peergos.blockstore.metadatadb.BlockMetadataStore;
import org.peergos.config.*;
import org.peergos.net.APIHandler;
import org.peergos.protocol.dht.DatabaseRecordStore;
import org.peergos.protocol.http.*;
import org.peergos.util.JSONParser;
import org.peergos.util.JsonHelper;
import org.peergos.util.Logging;
import org.peergos.util.TraceLogger;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.peergos.EmbeddedIpfs.buildBlockStore;
import static org.peergos.EmbeddedIpfs.buildBlockMetadata;

public class Client {

    public static final String IPFS_PATH = "IPFS_PATH";
    public static Path DEFAULT_IPFS_CONFIG_PATH;
    public static Path DEFAULT_IPFS_DIR_PATH = Paths.get(System.getProperty("user.home"), ".ipfs");

    private static final Logger LOG = Logging.LOG();

    private static HttpProtocol.HttpRequestProcessor proxyHandler(MultiAddress target) {
        return (s, req, h) -> HttpProtocol.proxyRequest(req, new InetSocketAddress(target.getHost(), target.getPort()),
                h);
    }

    public Client(Args args) throws Exception {
        DEFAULT_IPFS_CONFIG_PATH = args.hasArg("local") ? Paths.get(System.getProperty("user.dir"), "local-setup")
                : Paths.get(System.getProperty("user.dir"), "setup");
        DEFAULT_IPFS_CONFIG_PATH = Paths.get(DEFAULT_IPFS_CONFIG_PATH.toAbsolutePath().toString(), args.getArg("id"));
        DEFAULT_IPFS_DIR_PATH = Paths.get(DEFAULT_IPFS_DIR_PATH.toAbsolutePath().toString(), args.getArg("id"));
        Path configPath = DEFAULT_IPFS_CONFIG_PATH;
        Path ipfsPath = DEFAULT_IPFS_DIR_PATH;
        Logging.init(ipfsPath, args.getBoolean("log-to-console", true));
        Config config = readConfig(configPath, args);
        if (config.metrics.enabled) {
            AggregatedMetrics.startExporter(config.metrics.address, config.metrics.port);
        }
        BlockRequestAuthoriser authoriser = (c, p, a) -> CompletableFuture.completedFuture(true);

        Path datastorePath = ipfsPath.resolve("datastore").resolve("h2-v2.datastore");
        DatabaseRecordStore records = new DatabaseRecordStore(datastorePath.toAbsolutePath().toString());
        BlockMetadataStore meta = buildBlockMetadata(args);
        EmbeddedIpfs ipfs = EmbeddedIpfs.build(records,
                buildBlockStore(config, ipfsPath, meta, true),
                true,
                config.addresses.getSwarmAddresses(),
                config.bootstrap.getBootstrapAddresses(),
                config.identity,
                authoriser,
                config.addresses.proxyTargetAddress.map(Client::proxyHandler));
        ipfs.start();

        System.out.println("Started client: " + args.getArg("id"));
        TraceLogger traceLogger = TraceLogger.getInstance();
        traceLogger.setIdentity(config.identity.peerId);

        String apiAddressArg = "Addresses.API";
        MultiAddress apiAddress = args.hasArg(apiAddressArg) ? new MultiAddress(args.getArg(apiAddressArg))
                : config.addresses.apiAddress;
        InetSocketAddress localAPIAddress = new InetSocketAddress(apiAddress.getHost(), apiAddress.getPort());

        int maxConnectionQueue = 500;
        int handlerThreads = 5;
        LOG.info("Starting HTTP API server at " + apiAddress.getHost() + ":" + localAPIAddress.getPort());
        HttpServer httpServer = HttpServer.create(localAPIAddress, maxConnectionQueue);

        httpServer.createContext(APIHandler.API_URL, new APIHandler(ipfs));
        httpServer.setExecutor(Executors.newFixedThreadPool(handlerThreads));
        httpServer.start();

        Thread shutdownHook = new Thread(() -> {
            LOG.info("Stopping API server...");
            try {
                httpServer.stop(3); // wait max 3 seconds
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private Config readConfig(Path configPath, Args args) throws IOException {
        Path configFilePath = configPath.resolve("config");
        File configFile = configFilePath.toFile();
        if (!configFile.exists()) {
            Optional<String> s3datastoreArgs = args.getOptionalArg("s3.datastore");
            Config config = null;
            if (s3datastoreArgs.isPresent()) {
                Map<String, Object> json = (Map) JSONParser.parse(s3datastoreArgs.get());
                Map<String, Object> blockChildMap = new LinkedHashMap<>();
                blockChildMap.put("region", JsonHelper.getStringProperty(json, "region"));
                blockChildMap.put("bucket", JsonHelper.getStringProperty(json, "bucket"));
                blockChildMap.put("rootDirectory", JsonHelper.getStringProperty(json, "rootDirectory"));
                blockChildMap.put("regionEndpoint", JsonHelper.getStringProperty(json, "regionEndpoint"));
                if (JsonHelper.getOptionalProperty(json, "accessKey").isPresent()) {
                    blockChildMap.put("accessKey", JsonHelper.getStringProperty(json, "accessKey"));
                }
                if (JsonHelper.getOptionalProperty(json, "secretKey").isPresent()) {
                    blockChildMap.put("secretKey", JsonHelper.getStringProperty(json, "secretKey"));
                }
                blockChildMap.put("type", "s3ds");
                Mount s3BlockMount = new Mount("/blocks", "s3.datastore", "measure", blockChildMap);
                config = new Config(() -> s3BlockMount);
            } else {
                config = new Config();
            }
            Files.write(configFilePath, config.toString().getBytes(), StandardOpenOption.CREATE);
            return config;
        }
        return Config.build(Files.readString(configFilePath));
    }

    public static void main(String[] args) {
        try {
            new Client(Args.parse(args, /* isClient= */ true));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "SHUTDOWN", e);
        }
    }
}