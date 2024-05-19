
## Investigating tracing for IPFS with a minimal implementation

This project explores adding tracing functionality to IPFS. To create a prototype, we started with [Nabu](https://github.com/Peergos/nabu), a minimal implementation of IPFS.

To focus on the tracing aspect, several features of Nabu were disabled:

- *Bootstrap discovery*: Nabu doesn't include bootstrap peer discovery. To establish a private network, ten peers are manually connected to each other.
- *Optimistic BitSwap and flow control*: These features, related to data exchange between peers, were removed for the prototype.
- *File caching*: The ability to cache fetched files was also disabled.

Note: Only the content retrieval path is traced.

## Setup

```
./compile.sh
```
```
./run.sh <node-id>
```

`node-id` can be 0-9.
