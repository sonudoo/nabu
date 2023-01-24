package org.peergos;

import io.ipfs.cid.*;
import io.ipfs.multihash.*;

import java.util.*;
import java.util.concurrent.*;

public class RamBlockstore implements Blockstore {

    private final ConcurrentHashMap<Cid, byte[]> blocks = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Boolean> has(Cid c) {
        return CompletableFuture.completedFuture(blocks.containsKey(c));
    }

    @Override
    public CompletableFuture<Optional<byte[]>> get(Cid c) {
        return CompletableFuture.completedFuture(Optional.ofNullable(blocks.get(c)));
    }

    @Override
    public CompletableFuture<Cid> put(byte[] block, Cid.Codec codec) {
        Cid cid = new Cid(1, codec, Multihash.Type.sha2_256, Hash.sha256(block));
        blocks.put(cid, block);
        return CompletableFuture.completedFuture(cid);
    }
}