# Security boundaries

This is an educational desktop/client-server project for local demonstrations.
The server binds to `127.0.0.1` by default. It has no authentication,
authorization or TLS. Every connected client can read and change the entire
catalog, save snapshots and replace the catalog by loading a snapshot.

## Implemented controls

- Maximum frame size before allocation, protocol/version validation and fresh
  object streams for each message.
- Exact deserialization class allowlist plus depth/reference/array limits,
  applied to network messages and snapshot loading.
- Server-side field/type validation, deep copies, atomic duplicate checking
  and rollback on invalid snapshot contents.
- Snapshot basenames instead of client-supplied paths; symlink rejection for
  snapshots and NOFOLLOW_LINKS when opening them.
- Loopback defaults, bounded active sessions, connect/read timeouts and
  transport-failure cleanup.

These controls address specific problems found in the original implementation.
They do not constitute a security audit or a general guarantee that native Java
serialization is safe for arbitrary remote input. Oracle explicitly cautions
against deserializing untrusted data; see the [ObjectInputFilter documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html).

## Remaining exposure

- Any local process able to connect can modify the catalog. There are no
  per-user ownership rules or read-only users.
- Changing the bind address can expose an unauthenticated plaintext service.
  Do not publish this port to the public internet.
- Read timeouts are not total request deadlines. Slow trickle input, blocked
  socket writes, repeated reconnects and abusive local clients are not fully
  controlled. There are no rate limits or abuse-resistant per-user quotas.
- The storage directory must be controlled by the trusted server operator.
  Path validation does not protect against an attacker who can rewrite local
  filesystem components concurrently. Snapshot files are neither encrypted
  nor authenticated.
- There is no optimistic concurrency control: a stale editor can overwrite
  another client's update. Lost responses can leave mutation outcomes unknown.
- Shutdown discards unsaved changes. Atomic file replacement does not provide
  a crash-consistent transactional store or power-loss durability.

## Before adapting for shared deployment

Replace native object serialization with a schema-validated protocol, add
authentication and operation-level authorization, use TLS, add request deadlines
and rate controls, and use stable IDs with versioned database updates. Measure
load and failure behavior. Keep the JDK patched and establish a private channel
for sensitive security reports before accepting external users.

For this public demonstration, GitHub issues can track non-sensitive defects.
Do not publish credentials, personal data or exploitable deployment details in
an issue. No private reporting service is promised by this repository.
