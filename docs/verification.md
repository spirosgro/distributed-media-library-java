# Verification record

Local verification date: **16 October 2026**.

Environment: Windows 11, Oracle JDK **23.0.1**, `javac --release 17`, UTF-8,
`-Xlint:all -Werror`. Both application and tests compile without warnings under
that configuration. Serialization warnings are intentionally suppressed only
for generic protocol envelopes and the non-serialized Swing panel, with source
comments explaining those choices.

## Automated scenarios

Run `java tools/Build.java test` from the repository root. The runner uses explicit
assertions that remain enabled without `-ea`, and exits unsuccessfully if a
scenario fails. Every network fixture binds an ephemeral loopback port, uses
its own temporary directory and shuts down its listener/workers afterwards.

| # | Scenario |
|---|---|
| 1 | CRUD, duplicate detection, title/genre search, same-title movie/series deletion |
| 2 | Validation and defensive copying of media and nested episode objects |
| 3 | Case-insensitive identity under a Turkish default locale |
| 4 | Rejection of malformed generic episode contents after deserialization |
| 5 | Capacity rejection preserves the last reloadable catalog |
| 6 | Snapshot save/load, fresh-library restart, corrupt/duplicate/invalid-load rollback |
| 7 | Traversal, absolute paths and unsupported extensions rejected |
| 8 | Disallowed classes, arrays, graph depth and encoded-byte limits |
| 9 | All eleven request operations over real TCP sockets |
| 10 | Invalid application requests do not poison a valid connection |
| 11 | Mutated request/payload objects are not cached across frames |
| 12 | Four simultaneous clients race to insert the same title; exactly one succeeds |
| 13 | Malformed frame lengths and disallowed classes close only their session |
| 14 | A full server rejects excess clients and releases idle sessions |
| 15 | Swing connection, search and panel rendering using an actual local server |
| 16 | Demo client seeds an empty catalog, persists it and refuses a second import |

All 16 scenarios passed locally. Expected rejection tests print a few
`Session closed` messages: those are deliberate malformed-input/timeout cases.
The test run creates `build/ui-preview.png`, an offscreen rendering of the
actual Swing panel with authored fixture data.

## Publication and CI checks

`python tools/check_release.py` uses only the Python standard library. It checks
the intended public file set, local Markdown links, required files and selected
publication-hygiene rules. Python is optional for using/building the application;
it is needed only for this additional publication check.

The supplied GitHub Actions workflow builds and tests Windows/Linux on JDK
17/21. Those hosted jobs have not been observed before publication. Local Java
17 bytecode compilation is not a claim that every runtime/OS combination has
already passed. The checked-in preview was visually inspected for clipping.

## What the evidence does not cover

- Manual interaction with every native Swing modal dialog or every desktop OS.
- Internet deployment, authentication, TLS or adversarial security assurance.
- Performance/soak benchmarks, process-crash recovery or power-loss durability.
- Filesystems without atomic replacement or all symlink implementations.
- Backward compatibility with old coursework clients/snapshots.

The tests demonstrate specific implementation behavior, not a measured user
capacity, availability SLA, or production certification.
