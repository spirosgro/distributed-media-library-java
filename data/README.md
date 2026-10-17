# Local server data

Only this README belongs in Git. The default server writes snapshots such as
`library.ser` and `demo.ser` here when a client explicitly selects Save.

Snapshots contain serialized media metadata, not movie/video files. Startup
begins with an empty in-memory catalog; use Load to restore a trusted snapshot.
Keep the directory writable only by the trusted server operator. Local snapshot
files are ignored by Git and are excluded from the upload archive.
