# Architecture and protocol

## Components

`model` retains Media, Movie, Series and Episode. Their mutable accessors support
forms and object serialization. `MediaRules` performs validation and deep copies
at the server catalog boundary: returned lists, returned episodes and caller
objects cannot mutate stored state.

`common` holds one copy of Request, Response, RequestType, ObjectCodec and
FramedChannel. Both entry points use these same compiled types. Request payloads
remain generic to preserve the original operation-envelope design; runtime
class filtering and operation-specific checks narrow what is accepted.

`server` separates connection handling, dispatch and storage. A bounded pool
serves at most eight simultaneous sessions. Excess connections are closed;
there is no unbounded task queue. Each connection handles one request at a time.
Library methods synchronize on one catalog, including snapshot operations.

`client` uses a SwingWorker for each connection/request operation. UI reads and
writes happen on the event-dispatch thread; network I/O happens in the worker.
Buttons are temporarily disabled while a request runs. Closing the window while
a request is pending waits for the user to retry after that operation completes.

## Frame format (public protocol v1)

| Bytes | Meaning |
|---|---|
| 0-3 | Big-endian magic/version `0x4D4C0001` |
| 4-7 | Big-endian signed length, required range 1-1,048,576 |
| 8 onward | Exactly that many bytes: one Java serialized Request or Response |

The receiver validates length before allocating the payload buffer. Each frame
uses a fresh ObjectInputStream, avoiding cumulative reference caches and stale
objects across messages. Wrong versions, malformed frames, rejected classes,
unexpected root types, EOF or transport errors end the session. Application
errors such as a duplicate title return a failure Response and keep the
connection usable.

ObjectCodec allows only the protocol/domain classes, ArrayList, String, Enum
and the Object[] allocation used by ArrayList. Actual elements are filtered
individually. Limits are depth 20, references 20,000 and array length 10,000.
The byte cap applies independently of the object filter, including strings.
The same codec filters snapshots on disk before domain validation.

## Operation contract

| RequestType | Input | Successful response data |
|---|---|---|
| ADD_MOVIE / UPDATE_MOVIE | Movie payload | None |
| ADD_SERIES / UPDATE_SERIES | Series payload | None |
| SEARCH_BY_TITLE | `textParameter`: partial title | ArrayList of media |
| SEARCH_BY_GENRE | `textParameter`: exact genre | ArrayList of media |
| GET_ALL_MEDIA | No input | ArrayList of media |
| DELETE_MEDIA | Media payload; title and concrete type identify the item | None |
| SAVE_LIBRARY | Optional `fileName` | None |
| LOAD_LIBRARY | Optional `fileName` | None |
| DISCONNECT | No input | Acknowledgement, then close |

GUI disconnect simply closes its socket; the server also supports the explicit
DISCONNECT operation used by the demo and tests. Unused request fields do not
grant additional behavior.

## Data validation

Required title/director values are 1-200 characters, genre 1-80, and optional
description 0-2,000. Values are trimmed. Control codes are rejected except
newline/tab. Release years are 1888-2100, duration 1-1,440 minutes, seasons 1-100,
and episode numbers 1-1,000. A series can contain at most 100 episodes, each
within its season range and with a unique season/episode pair. These are explicit
project policies, not claims about all real-world media.

The catalog caps item count at 1,000 and rejects a mutation before committing
if the resulting response would exceed byte or graph limits. The byte budget
also reserves metadata headroom. A large series catalog can reach the graph
limit before reaching the item or byte limit. Full scans and copies are deliberate
small-catalog tradeoffs; this design has no indexed database or pagination.

## Persistence and consistency

Save/load accept only a basename matching `[A-Za-z0-9][A-Za-z0-9_-]{0,63}\.ser`.
Empty names use `library.ser`. All files live in a directory selected by the
server operator. Remote clients cannot choose directories. Snapshot symlinks
are rejected, and load opens with NOFOLLOW_LINKS. The directory itself must be
controlled by the trusted server user; this is not a sandbox against another
local process that can rewrite its contents or replace filesystem components.

Save serializes a copy, writes a temporary file in the same directory, then
requests an atomic replacement. If the filesystem does not support the atomic
move, save fails rather than falling back to a partially written overwrite.
This is not a power-loss durability guarantee: there is no fsync/WAL strategy.

Load parses, filters, validates and checks duplicates/capacity in a temporary
catalog. Only a fully accepted snapshot replaces live state. Synchronization
makes an individual load/save/mutation consistent, but holds the catalog lock
during disk I/O. Concurrent stale edits use last-writer-wins behavior; there are
no versions, transactions spanning requests, or multi-server consistency.

## Failure semantics

The client connects with a 3-second timeout and waits up to 5 seconds per socket
read. Server sessions have a 120-second idle read timeout. A transport error
closes the client connection; reconnect explicitly. A disconnected peer may
still appear connected until the next operation detects its closure.

If a response is lost after a successful mutation, the client cannot determine
whether the operation committed. It must inspect the catalog after reconnecting;
there is no automatic retry or exactly-once guarantee. A GUI refresh after a
successful mutation can also fail separately from the mutation itself.

These are per-read timeouts, not whole-request deadlines. Slow trickle senders,
blocked writes, DNS resolution and repeated reconnects are not fully addressed.
See [security boundaries](../SECURITY.md) before adapting the design for a shared
or untrusted network.
