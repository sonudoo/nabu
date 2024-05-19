package org.peergos;

import io.ipfs.cid.Cid;
import io.ipfs.cid.Cid.Codec;
import io.ipfs.multiaddr.MultiAddress;
import io.libp2p.core.PeerId;

import org.peergos.blockstore.metadatadb.BlockMetadataStore;
import org.peergos.config.*;
import org.peergos.protocol.dht.DatabaseRecordStore;
import org.peergos.protocol.http.*;
import org.peergos.util.JSONParser;
import org.peergos.util.JsonHelper;
import org.peergos.util.Logging;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
    public static Path DEFAULT_IPFS_CONFIG_PATH = Paths.get(System.getProperty("user.dir"), "setup");
    public static Path DEFAULT_IPFS_DIR_PATH = Paths.get(System.getProperty("user.home"), ".ipfs");

    private static final Logger LOG = Logging.LOG();

    private static HttpProtocol.HttpRequestProcessor proxyHandler(MultiAddress target) {
        return (s, req, h) -> HttpProtocol.proxyRequest(req, new InetSocketAddress(target.getHost(), target.getPort()),
                h);
    }

    public Client(Args args) throws Exception {
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

        System.out.println("Swarm addresses = " + config.addresses.getSwarmAddresses());
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
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Publish (P) or Retrieve (R)? ");
            String opt = scanner.nextLine();
            if (opt.toUpperCase().equals("P")) {
                System.out.print("File? or Text?");
                String inputOpt = scanner.nextLine();
                if (inputOpt.toUpperCase().equals("FILE")) {
                    System.out.print("Enter a path to the content:");
                    String contentFilePath = scanner.nextLine();
                    File contentFile = new File(contentFilePath);
                    byte[] fileContent = Files.readAllBytes(contentFile.toPath());
                    System.out.println("Contents:" + fileContent);
                    Cid cid = ipfs.blockstore.put(fileContent, Codec.Raw).join();
                    System.out.println("Cid: " + cid.toString());
                } else {
                    System.out.println("Enter");
                    System.out.print("Enter contents to publish: ");
                    String content = scanner.nextLine();
                    byte[] contentBytes = content.getBytes();
                    Cid cid = ipfs.blockstore.put(contentBytes, Codec.Raw).join();
                    System.out.println("Cid: " + cid.toString());
                }
            } else if (opt.toUpperCase().equals("R")) {
                System.out.print("Enter Cid to retrieve: ");
                String cid = scanner.nextLine();
                Want want = new Want(Cid.decode(cid));
                List<HashedBlock> blocks = ipfs.getBlocks(List.of(want), new HashSet<PeerId>(), false);
                Path tempFile = Files.createTempFile(blocks.get(0).hash.toString(), ".tmp");
                Files.write(tempFile, blocks.get(0).block, StandardOpenOption.WRITE);
                System.out.println("Wrote content to " + tempFile.toString());
                //System.out.println("Content: " + new String(blocks.get(0).block, StandardCharsets.UTF_8));
            } else {
                System.out.println("Try again");
            }
        }
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