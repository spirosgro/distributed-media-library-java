# Demo walkthrough

## Five-minute demonstration

1. Run `java tools/Build.java test`. Look for `ALL 16 SCENARIOS PASSED`.
2. Start `java -cp build/media-library.jar server.ServerMain` in terminal 1.
3. In terminal 2, run `java -cp build/media-library.jar demo.DemoClient` once
   against the empty server. It creates three authored media entries and
   explicitly saves `demo.ser`.
4. Run `java -jar build/media-library.jar`, then Connect. Select the series to
   view both episodes. Search title `horizon` and exact genre `Drama`.
5. Select Show all, choose the movie, then Edit selected. Change its duration.
   The title is fixed because title/type identify an update.
6. Add a new series with two seasons. Enter episode lines such as
   `1 | 1 | 42 | First Light` and `2 | 1 | 46 | The Return`.
7. Save using `my-demo.ser`, then delete one selected item. Load `my-demo.ser`
   and confirm that the deleted entry returns.
8. Open another client. Changes are shared on the server; select Show all to
   refresh. Stop the server, restart it, then Load the saved snapshot to show
   the difference between memory and explicit persistence.

These steps describe expected behavior. The automated tests cover the underlying
operations, but the entire native modal-dialog sequence has not been manually
verified on each operating system.

## Troubleshooting

| Symptom | Action |
|---|---|
| `javac` unavailable | Use a JDK, then check terminal PATH |
| Build cannot find source | Change directory to the repository root |
| Port already in use | Start the server with another port, then match it in the GUI |
| Connection refused | Start the server and check host/port |
| Immediate disconnect when several clients connect | The server allows eight sessions; close an idle client |
| Client disconnected after inactivity | Reconnect; the default server idle timeout is two minutes |
| Snapshot save fails | Use a basename such as `demo.ser`, check folder permissions and filesystem support for atomic move |
| Load fails but catalog remains | The snapshot is missing, invalid, incompatible or over capacity; failed load preserves memory |
| Duplicate title rejected | Title and type are case-insensitive; edit the existing entry instead |
| GUI unavailable on headless host | Run the tests or DemoClient; launch Swing on a desktop |
| Request fails during server shutdown | Reconnect and inspect the catalog before retrying a mutation |

Do not load arbitrary downloaded serialized files. Only use snapshots you
created or inspected in a trusted environment. Old coursework files and old
clients are not a supported migration path for the revised implementation.
