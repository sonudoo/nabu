
## Investigating tracing for IPFS with a minimal implementation

This project explores adding tracing functionality to IPFS. To create a prototype, we started with [Nabu](https://github.com/Peergos/nabu), a minimal implementation of IPFS.

To focus on the tracing aspect, several features of Nabu were disabled:

- *Bootstrap discovery*: Nabu doesn't include bootstrap peer discovery. To establish a private network, ten peers are manually connected to each other.
- *Optimistic BitSwap and flow control*: These features, related to data exchange between peers, were removed for the prototype.
- *File caching*: The ability to cache fetched files was also disabled.

Note: Only the content retrieval (GET) path is traced.

## Setup

```
./compile-local.sh
```
```
./run-local.sh <node-id>
```

`node-id` can be 0-9.

## API

### PUT

#### Description

Adds content provided in raw request body and returns the content id for it.

```
PUT /api/v0/block/put
```

#### Response

```
{
  "cid" : string
}
```

#### Status Codes

- 200 - `OK`
- 400 - `BAD_REQUEST`

### GET

#### Description

Retrieves content provided for the provided `cid`. Optionally exports trace for the request if `trace` is set to 1.

```
GET /api/v0/block/get?cid=<cid>[&trace=1]
```

#### Response

Response body contains content bytes.

#### Status Codes

- 200 - `OK`
- 400 - `BAD_REQUEST`


## Example

#### Request 1

```
curl -X PUT -d "abc" http://localhost:5001/api/v0/block/put
```

#### Response 1

```
{"Hash":"bafkreif2pall7dybz7vecqka3zo24irdwabwdi4wc55jznaq75q7eaavvu"}
```

#### Request 2

```
curl 'http://localhost:5001/api/v0/block/get?cid=bafkreif2pall7dybz7vecqka3zo24irdwabwdi4wc55jznaq75q7eaavvu&trace=1'
```

#### Response 2

```
abc
```