package org.peergos;

import identify.pb.*;
import io.ipfs.api.*;
import io.ipfs.cid.*;
import io.libp2p.core.*;
import io.libp2p.core.multiformats.*;
import io.libp2p.protocol.*;
import org.junit.*;
import org.peergos.blockstore.*;
import org.peergos.protocol.bitswap.*;
import org.peergos.protocol.dht.*;
import org.peergos.protocol.ipns.*;

import java.time.*;
import java.util.*;

public class KademliaTest {

    @Test
    public void dhtMessages() throws Exception {
        RamBlockstore blockstore1 = new RamBlockstore();
        Bitswap bitswap1 = new Bitswap(new BitswapEngine(blockstore1));
        Kademlia lanDht = new Kademlia(new KademliaEngine(), true);
        Kademlia wanDht = new Kademlia(new KademliaEngine(), false);
        Ping ping = new Ping();
        Host node1 = Server.buildHost(10000 + new Random().nextInt(50000),
                List.of(ping, bitswap1, lanDht, wanDht));
        node1.start().join();
        Cid node1Id = Cid.cast(node1.getPeerId().getBytes());

        // connect node 2 to kubo, but not node 1
        Bitswap bitswap2 = new Bitswap(new BitswapEngine(new RamBlockstore()));
        Host node2 = Server.buildHost(10000 + new Random().nextInt(50000),
                List.of(ping, bitswap2,
                        new Kademlia(new KademliaEngine(), true),
                        new Kademlia(new KademliaEngine(), false)));
        node2.start().join();

        try {
            IPFS kubo = new IPFS("localhost", 5001);
            String kuboID = (String)kubo.id().get("ID");
            io.ipfs.multihash.Multihash kuboId = Cid.fromBase58(kuboID);
            Multiaddr address2 = Multiaddr.fromString("/ip4/127.0.0.1/tcp/4001/p2p/" + kuboID);
            bitswap2.dial(node2, address2).getController().join();

            IdentifyOuterClass.Identify id = new Identify().dial(node1, address2).getController().join().id().join();
            Kademlia dht = id.getProtocolsList().contains("/ipfs/lan/kad/1.0.0") ? lanDht : wanDht;
            KademliaController bootstrap1 = dht.dial(node1, address2).getController().join();
            List<PeerAddresses> peers = bootstrap1.closerPeers(Cid.cast(node2.getPeerId().getBytes())).join();
            Optional<PeerAddresses> matching = peers.stream()
                    .filter(p -> Arrays.equals(p.peerId.toBytes(), node2.getPeerId().getBytes()))
                    .findFirst();
            if (matching.isEmpty())
                throw new IllegalStateException("Couldn't find node2 from kubo!");

            Cid block = blockstore1.put("Provide me.".getBytes(), Cid.Codec.Raw).join();
            bootstrap1.provide(block, PeerAddresses.fromHost(node1)).join();

            Providers providers = bootstrap1.getProviders(block).join();
            Optional<PeerAddresses> matchingProvider = providers.providers.stream()
                    .filter(p -> Arrays.equals(p.peerId.toBytes(), node1.getPeerId().getBytes()))
                    .findFirst();
            if (matchingProvider.isEmpty())
                throw new IllegalStateException("Node1 is not a provider for block!");

            // publish a cid in kubo ipns
//            Map publish = kubo.name.publish(block);
            Cid kuboPeerId = new Cid(1, Cid.Codec.Libp2pKey, kuboId.getType(), kuboId.getHash());
            GetResult kuboIpnsGet = wanDht.dial(node1, address2).getController().join().getValue(kuboId).join();
//            LinkedBlockingDeque<PeerAddresses> queue = new LinkedBlockingDeque<>();
//            queue.addAll(kuboIpnsGet.closerPeers);
//            outer: for (int i=0; i < 1000; i++) {
//                if (kuboIpnsGet.record.isPresent())
//                    break;
//                PeerAddresses closer = queue.poll();
//                List<String> candidates = closer.addresses.stream()
//                        .map(MultiAddress::toString)
//                        .filter(a -> a.contains("tcp") && a.contains("ip4") && !a.contains("127.0.0.1") && !a.contains("/172."))
//                        .collect(Collectors.toList());
//                for (String candidate: candidates) {
//                    try {
//                        kuboIpnsGet = wanDht.dial(node1, Multiaddr.fromString(candidate + "/p2p/" + closer.peerId)).getController().join()
//                                .getValue(kuboId).join();
//                        queue.addAll(kuboIpnsGet.closerPeers);
//                        continue outer;
//                    } catch (Exception e) {
//                        System.out.println(e.getMessage());
//                    }
//                }
//            }

//            GetResult join = dht.dial(node1, node2.listenAddresses().get(0)).getController().join().getValue(kuboPeerId).join();

            // sign an ipns record to publish
            String pathToPublish = "/ipfs/" + block;
            LocalDateTime expiry = LocalDateTime.now().plusHours(1);
            int sequence = 1;
            long ttl = 3600_000_000_000L;

            System.out.println("Sending put value...");
//            boolean success = bootstrap1.putValue(pathToPublish, expiry, sequence, ttl, node1Id, node1.getPrivKey()).join();
            GetResult getresult = bootstrap1.getValue(node1Id).join();
            System.out.println();
        } finally {
            node1.stop();
            node2.stop();
        }
    }
}
