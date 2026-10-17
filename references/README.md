# Technical references

The implementation relies only on the JDK. These official references explain
the APIs used; none is bundled or relicensed by this project.

- [ObjectInputFilter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html): class/graph filtering and the limits of native deserialization.
- [SwingWorker](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/swing/SwingWorker.html): background execution with event-thread completion.
- [Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): bounded file reads, temporary files and atomic moves.
- [ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): bounded workers and rejection behavior.
- [Socket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/Socket.html): connect and read timeouts.

The first three references were reviewed during preparation. Local tests are
the evidence for this application's behavior; API documentation alone does
not verify the implementation or its deployment security.
