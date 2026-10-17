import javax.tools.ToolProvider;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Dependency-free build, packaging and test runner. Run from the repository root. */
public class Build {
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "test" : args[0];
        if (!Set.of("test", "package").contains(mode) || args.length > 1) {
            throw new IllegalArgumentException("Usage: java tools/Build.java [test|package]");
        }
        if (!Files.isDirectory(Path.of("src/main/java"))) throw new IOException("Run from the repository root");
        Path build = Path.of("build").toAbsolutePath().normalize();
        Files.createDirectories(build);
        Path work = Files.createTempDirectory(build, "compile-");
        try {
            Path main = work.resolve("main");
            compile(Path.of("src/main/java"), main, null);
            Path jar = build.resolve("media-library.jar");
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "client.ClientMain");
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest);
                 var files = Files.walk(main)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    JarEntry entry = new JarEntry(main.relativize(file).toString().replace('\\', '/'));
                    entry.setTime(0);
                    output.putNextEntry(entry);
                    Files.copy(file, output);
                    output.closeEntry();
                }
            }
            System.out.println("Built " + jar.getFileName() + " (Java 17 target, no external dependencies)");
            if (mode.equals("test")) {
                Path tests = work.resolve("tests");
                compile(Path.of("src/test/java"), tests, main.toString());
                String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
                Process process = new ProcessBuilder(java, "-Djava.awt.headless=true", "-cp", main + File.pathSeparator + tests,
                        "tests.TestSuite").inheritIO().start();
                int result = process.waitFor();
                if (result != 0) throw new IOException("Tests failed (exit " + result + ")");
            }
        } finally {
            // Only remove the unique compilation directory created by this invocation.
            if (!work.normalize().startsWith(build) || Files.isSymbolicLink(work)) throw new IOException("Invalid build cleanup target");
            try (var files = Files.walk(work)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void compile(Path source, Path output, String classpath) throws IOException {
        Files.createDirectories(output);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("A JDK is required, not a runtime-only Java installation");
        ArrayList<String> arguments = new ArrayList<>(List.of("--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-Werror", "-d", output.toString()));
        if (classpath != null) arguments.addAll(List.of("-classpath", classpath));
        try (var files = Files.walk(source)) {
            files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(path -> arguments.add(path.toString()));
        }
        if (compiler.run(null, System.out, System.err, arguments.toArray(String[]::new)) != 0) throw new IOException("Compilation failed");
    }
}
