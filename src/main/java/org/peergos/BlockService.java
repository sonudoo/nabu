package org.peergos;

import io.libp2p.core.*;

import java.util.*;

public interface BlockService {

    List<HashedBlock> get(List<Want> hashes, Set<PeerId> peers);

    default HashedBlock get(Want c, Set<PeerId> peers) {
        return get(Collections.singletonList(c), peers).get(0);
    }
}
