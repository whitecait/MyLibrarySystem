package com.library;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleDependencyRulesTest {

    private static final Set<String> MODULES = Set.of(
            "identity", "catalog", "circulation", "settlement",
            "review", "query", "ai", "delivery");
    private static final Pattern OWNER = Pattern.compile(
            "[/\\\\]com[/\\\\]library[/\\\\](identity|catalog|circulation|settlement|review|query|ai|delivery)[/\\\\]");
    private static final Pattern INTERNAL_REFERENCE = Pattern.compile(
            "com\\.library\\.(identity|catalog|circulation|settlement|review|query|ai|delivery)\\.(internal|adapter\\.persistence)(?:\\.|;)");
    private static final Pattern AGGREGATE_REFERENCE = Pattern.compile(
            "com\\.library\\.(identity|catalog|circulation|settlement|review|query|ai|delivery)\\.contract\\.[A-Z][A-Za-z]+Module\\b");
    private static final Pattern MODULE_CONTRACT_CAPABILITY_REFERENCE = Pattern.compile(
            "com\\.library\\.(identity|catalog|circulation|settlement|review|query|ai|delivery)"
                    + "\\.contract\\.(?!model(?:\\.|;|\\s))");
    private static final Set<String> QUERY_MODULE_SNAPSHOT_MODULES = Set.of(
            "identity", "catalog", "circulation", "settlement", "query");

    @Test
    void internalImplementationsAreVisibleOnlyInsideTheirOwningModule() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String owner = ownerOf(source);
            String text = Files.readString(source);
            Matcher references = INTERNAL_REFERENCE.matcher(text);
            while (references.find()) {
                String target = references.group(1);
                if (!target.equals(owner)) {
                    violations.add(source + " imports " + target + " internal implementation");
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void aggregateModuleInterfacesAreImportedOnlyByBootstrap() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String normalized = source.toString().replace('\\', '/');
            if (normalized.contains("/com/library/bootstrap/")) {
                continue;
            }
            if (AGGREGATE_REFERENCE.matcher(Files.readString(source)).find()) {
                violations.add(source + " imports an aggregate module interface outside bootstrap");
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void queryModuleUsesOnlyMd0002SnapshotModulesForPublicCapabilities() throws IOException {
        Path root = Path.of("src/main/java/com/library/query/internal");
        if (!Files.exists(root)) {
            return;
        }
        List<String> violations = new ArrayList<>();
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
        for (Path source : sources) {
            Matcher references = MODULE_CONTRACT_CAPABILITY_REFERENCE.matcher(Files.readString(source));
            while (references.find()) {
                String target = references.group(1);
                if (!QUERY_MODULE_SNAPSHOT_MODULES.contains(target)) {
                    violations.add(source + " reads public capability from " + target
                            + "; QUERY snapshots are limited by MD-0002 §3");
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    private static List<Path> javaSources() throws IOException {
        Path root = Path.of("src/main/java");
        if (!Files.exists(root)) {
            return List.of();
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static String ownerOf(Path source) {
        Matcher matcher = OWNER.matcher(source.toAbsolutePath().toString());
        if (!matcher.find() || !MODULES.contains(matcher.group(1))) {
            return "";
        }
        return matcher.group(1);
    }
}
