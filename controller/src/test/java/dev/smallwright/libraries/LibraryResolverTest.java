package dev.smallwright.libraries;

import static dev.smallwright.libraries.MavenFixture.dependencies;
import static dev.smallwright.libraries.MavenFixture.dependency;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Resolution against a local fixture of Google's Maven repository and Maven Central. */
public final class LibraryResolverTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private MavenFixture fixture;
    private File cache;

    @Before
    public void start() throws Exception {
        fixture = new MavenFixture();
        cache = temporary.newFolder("cache");
    }

    @After
    public void stop() {
        fixture.close();
    }

    private List<String> resolve(String... libraries) throws IOException {
        List<Library> wanted = new ArrayList<>();
        for (String library : libraries) wanted.add(Library.parse(library));
        List<String> found = new ArrayList<>();
        for (LibraryResolver.Resolved resolved :
                new LibraryResolver(fixture.repositories(cache)).resolve(wanted))
            found.add(resolved.library.toString());
        return found;
    }

    @Test
    public void resolvesCompileAndRuntimeDependenciesTransitivelyButNotTestProvidedOrOptional()
            throws Exception {
        fixture.jar(
                "central",
                "org.example:app-lib:1.0",
                dependencies(
                        dependency("org.example:core:2.0", ""),
                        dependency("org.example:runtime-only:1.1", "<scope>runtime</scope>"),
                        dependency("junit:junit:4.13", "<scope>test</scope>"),
                        dependency("org.example:provided:1.0", "<scope>provided</scope>"),
                        dependency("org.example:optional:1.0", "<optional>true</optional>")));
        fixture.jar(
                "central",
                "org.example:core:2.0",
                dependencies(dependency("org.example:deep:3.0", "")));
        fixture.jar("central", "org.example:runtime-only:1.1", "");
        fixture.jar("central", "org.example:deep:3.0", "");
        assertEquals(
                List.of(
                        "org.example:app-lib:1.0",
                        "org.example:core:2.0",
                        "org.example:deep:3.0",
                        "org.example:runtime-only:1.1"),
                resolve("org.example:app-lib:1.0"));
        assertFalse(
                "Google's answers of not found leave nothing in its cache",
                new File(cache, "google/org").exists());
    }

    @Test
    public void readsGroupVersionPropertiesAndManagedVersionsFromParentPoms() throws Exception {
        fixture.library(
                "central",
                "org.example:base:7",
                "pom",
                "<properties><core.version>2.5</core.version>"
                        + "<managed.version>1.4</managed.version></properties>"
                        + "<dependencyManagement>"
                        + dependencies(dependency("org.example:managed:${managed.version}", ""))
                        + "</dependencyManagement>");
        fixture.library(
                "central",
                "org.example:middle:7",
                "pom",
                "<parent><groupId>org.example</groupId><artifactId>base</artifactId>"
                        + "<version>7</version></parent>");
        fixture.put(
                "central",
                "org/example/child/7/child-7.pom",
                MavenFixture.pom(
                                null,
                                "child",
                                null,
                                "jar",
                                "<parent><groupId>org.example</groupId><artifactId>middle</artifactId>"
                                        + "<version>7</version></parent>"
                                        + dependencies(
                                                dependency(
                                                        "${project.groupId}:core:${core.version}",
                                                        ""),
                                                dependency("org.example:managed", ""),
                                                dependency(
                                                        "org.example:sibling:${project.version}",
                                                        "")))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        fixture.put("central", "org/example/child/7/child-7.jar", MavenFixture.archive("child"));
        fixture.jar("central", "org.example:core:2.5", "");
        fixture.jar("central", "org.example:managed:1.4", "");
        fixture.jar("central", "org.example:sibling:7", "");
        assertEquals(
                List.of(
                        "org.example:child:7",
                        "org.example:core:2.5",
                        "org.example:managed:1.4",
                        "org.example:sibling:7"),
                resolve("org.example:child:7"));
    }

    @Test
    public void theHighestRequestedVersionWinsAndOnlyItsDependenciesStay() throws Exception {
        fixture.jar(
                "central",
                "org.example:a:1.0",
                dependencies(dependency("org.example:shared:1.2", "")));
        fixture.jar(
                "central", "org.example:b:1.0", dependencies(dependency("org.example:c:1.0", "")));
        fixture.jar(
                "central",
                "org.example:c:1.0",
                dependencies(dependency("org.example:shared:1.10", "")));
        fixture.jar(
                "central",
                "org.example:shared:1.2",
                dependencies(dependency("org.example:old-helper:1.0", "")));
        fixture.jar("central", "org.example:old-helper:1.0", "");
        fixture.jar(
                "central",
                "org.example:shared:1.10",
                dependencies(dependency("org.example:new-helper:1.0", "")));
        fixture.jar("central", "org.example:new-helper:1.0", "");
        assertEquals(
                List.of(
                        "org.example:a:1.0",
                        "org.example:b:1.0",
                        "org.example:c:1.0",
                        "org.example:new-helper:1.0",
                        "org.example:shared:1.10"),
                resolve("org.example:a:1.0", "org.example:b:1.0"));
    }

    @Test
    public void honoursExclusionsBelowTheDependencyThatDeclaresThem() throws Exception {
        fixture.jar(
                "central",
                "org.example:app:1",
                dependencies(
                        dependency(
                                "org.example:middle:1",
                                "<exclusions><exclusion><groupId>org.unwanted</groupId>"
                                        + "<artifactId>*</artifactId></exclusion>"
                                        + "<exclusion><groupId>org.example</groupId>"
                                        + "<artifactId>logging</artifactId></exclusion>"
                                        + "</exclusions>")));
        fixture.jar(
                "central",
                "org.example:middle:1",
                dependencies(
                        dependency("org.unwanted:anything:1", ""),
                        dependency("org.example:logging:1", ""),
                        dependency("org.example:kept:1", "")));
        fixture.jar(
                "central",
                "org.example:kept:1",
                dependencies(dependency("org.unwanted:deeper:1", "")));
        fixture.jar("central", "org.example:logging:1", "");
        fixture.jar("central", "org.unwanted:anything:1", "");
        fixture.jar("central", "org.unwanted:deeper:1", "");
        assertEquals(
                List.of("org.example:app:1", "org.example:kept:1", "org.example:middle:1"),
                resolve("org.example:app:1"));
    }

    @Test
    public void triesGoogleFirstThenMavenCentralAndKeepsEachFileWithItsPom() throws Exception {
        fixture.jar(
                "google",
                "androidx.example:thing:1.0",
                dependencies(dependency("org.example:plain:2", "")));
        fixture.jar("central", "org.example:plain:2", "");
        List<LibraryResolver.Resolved> resolved =
                new LibraryResolver(fixture.repositories(cache))
                        .resolve(List.of(Library.parse("androidx.example:thing:1.0")));
        assertEquals("Google's Maven repository", resolved.get(0).repository);
        assertEquals("Maven Central", resolved.get(1).repository);
        assertEquals(
                MavenFixture.hex("SHA-256", Files.readAllBytes(resolved.get(1).file.toPath())),
                resolved.get(1).sha256);
        assertArrayEquals(
                MavenFixture.archive("org.example:plain:2"),
                Files.readAllBytes(resolved.get(1).file.toPath()));
        assertTrue(resolved.get(1).file.getPath().startsWith(cache.getPath()));
    }

    @Test
    public void refusesAFileWhoseBytesDoNotMatchThePublishedChecksum() throws Exception {
        fixture.jar("central", "org.example:tampered:1", "");
        fixture.putRaw(
                "central",
                "org/example/tampered/1/tampered-1.jar",
                MavenFixture.archive("something else"));
        IOException refused =
                assertThrows(IOException.class, () -> resolve("org.example:tampered:1"));
        assertTrue(refused.getMessage(), refused.getMessage().contains("checksum"));
        assertTrue(refused.getMessage(), refused.getMessage().contains("tampered-1.jar"));
        assertNoCachedFile("tampered-1.jar");
    }

    @Test
    public void prefersTheStrongestPublishedChecksumAndRefusesFilesWithNone() throws Exception {
        fixture.jar("central", "org.example:weak:1", "");
        fixture.putRaw(
                "central", "org/example/weak/1/weak-1.jar.sha512", "0".repeat(128).getBytes());
        IOException wrongStrongest =
                assertThrows(IOException.class, () -> resolve("org.example:weak:1"));
        assertTrue(wrongStrongest.getMessage(), wrongStrongest.getMessage().contains("checksum"));

        fixture.jar("central", "org.example:sha1-only:1", "");
        fixture.remove("central", "org/example/sha1-only/1/sha1-only-1.jar.sha512");
        fixture.remove("central", "org/example/sha1-only/1/sha1-only-1.jar.sha256");
        assertEquals(List.of("org.example:sha1-only:1"), resolve("org.example:sha1-only:1"));

        fixture.jar("central", "org.example:unchecked:1", "");
        fixture.remove("central", "org/example/unchecked/1/unchecked-1.jar.sha512");
        fixture.remove("central", "org/example/unchecked/1/unchecked-1.jar.sha256");
        fixture.remove("central", "org/example/unchecked/1/unchecked-1.jar.sha1");
        IOException none =
                assertThrows(IOException.class, () -> resolve("org.example:unchecked:1"));
        assertTrue(none.getMessage(), none.getMessage().contains("no published checksum"));
        assertNoCachedFile("unchecked-1.jar");
    }

    @Test
    public void refusesLibrariesFromAnyOtherRepositoryAndNeverFollowsRedirects() throws Exception {
        fixture.jar(
                "central",
                "org.example:uses-other:1",
                "<repositories><repository><id>other</id><url>"
                        + fixture.base("other")
                        + "</url></repository></repositories>"
                        + dependencies(dependency("org.elsewhere:only-there:1", "")));
        fixture.jar("other", "org.elsewhere:only-there:1", "");
        IOException missing =
                assertThrows(IOException.class, () -> resolve("org.example:uses-other:1"));
        assertTrue(
                missing.getMessage(),
                missing.getMessage()
                        .contains(
                                "org.elsewhere:only-there:1 is not on Google's Maven repository"
                                        + " or Maven Central"));

        fixture.redirect(
                "central",
                "org/example/moved/1/moved-1.pom",
                fixture.base("other") + "org/example/moved/1/moved-1.pom");
        fixture.jar("other", "org.example:moved:1", "");
        IOException redirected =
                assertThrows(IOException.class, () -> resolve("org.example:moved:1"));
        assertTrue(redirected.getMessage(), redirected.getMessage().contains("redirect"));
        for (String request : fixture.requests) assertFalse(request, request.startsWith("/other/"));
    }

    @Test
    public void reusesCachedFilesOfflineAndRechecksThem() throws Exception {
        fixture.jar(
                "central",
                "org.example:cached:1",
                dependencies(dependency("org.example:dep:1", "")));
        fixture.jar("central", "org.example:dep:1", "");
        List<String> online = resolve("org.example:cached:1");
        fixture.close();
        MavenRepositories offline =
                new MavenRepositories(
                        cache,
                        new MavenRepositories.Endpoints(
                                "http://127.0.0.1:9/google/",
                                "http://127.0.0.1:9/central/",
                                "http://127.0.0.1:9/search/select"));
        List<String> again = new ArrayList<>();
        List<LibraryResolver.Resolved> resolved =
                new LibraryResolver(offline)
                        .resolve(List.of(Library.parse("org.example:cached:1")));
        for (LibraryResolver.Resolved library : resolved) again.add(library.library.toString());
        assertEquals(online, again);

        Files.write(resolved.get(1).file.toPath(), MavenFixture.archive("changed"));
        IOException changed =
                assertThrows(
                        IOException.class,
                        () ->
                                new LibraryResolver(offline)
                                        .resolve(List.of(Library.parse("org.example:cached:1"))));
        assertTrue(changed.getMessage(), changed.getMessage().contains("dep-1.jar"));
    }

    @Test
    public void resolvesAndroidLibrariesToTheirAarFilesWithTheirManifests() throws Exception {
        fixture.jar(
                "central",
                "org.example:wants-aar:1",
                dependencies(dependency("androidx.example:widget:1.0", "<type>aar</type>")));
        fixture.aar(
                "google",
                "androidx.example:widget:1.0",
                dependencies(dependency("androidx.example:base:2.0", "")),
                "<application><service android:name=\".Sync\"/></application>",
                java.util.Map.of());
        fixture.library("google", "androidx.example:base:2.0", "aar", "");
        List<LibraryResolver.Resolved> resolved =
                new LibraryResolver(fixture.repositories(cache))
                        .resolve(List.of(Library.parse("org.example:wants-aar:1")));
        assertEquals(3, resolved.size());
        assertEquals("androidx.example:base:2.0", resolved.get(0).library.toString());
        LibraryResolver.Resolved widget = resolved.get(1);
        assertEquals("androidx.example:widget:1.0", widget.library.toString());
        assertTrue(widget.file.getName(), widget.file.getName().equals("widget-1.0.aar"));
        assertTrue(widget.manifest, widget.manifest.contains("android:name=\".Sync\""));
        assertTrue(
                widget.manifest, widget.manifest.contains("package=\"androidx.example.widget\""));
        assertNull(resolved.get(2).manifest);
    }

    @Test
    public void resolvesAndroidLibrariesWithNativeCodeAndRefusesOnesWithoutAManifest()
            throws Exception {
        fixture.aar(
                "google",
                "androidx.example:native:1.0",
                "",
                "",
                java.util.Map.of("jni/arm64-v8a/libnative.so", new byte[] {1}));
        LibraryResolver.Resolved withNativeCode =
                new LibraryResolver(fixture.repositories(cache))
                        .resolve(List.of(Library.parse("androidx.example:native:1.0")))
                        .get(0);
        assertEquals("native-1.0.aar", withNativeCode.file.getName());
        assertTrue(
                withNativeCode.manifest,
                withNativeCode.manifest.contains("package=\"androidx.example.native\""));
        fixture.put(
                "google",
                "androidx/example/hollow/1.0/hollow-1.0.pom",
                MavenFixture.pom("androidx.example", "hollow", "1.0", "aar", "")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        fixture.put(
                "google",
                "androidx/example/hollow/1.0/hollow-1.0.aar",
                MavenFixture.archive("no manifest"));
        IOException refused =
                assertThrows(IOException.class, () -> resolve("androidx.example:hollow:1.0"));
        assertTrue(refused.getMessage(), refused.getMessage().contains("has no manifest"));
    }

    @Test
    public void kotlinStandardLibraryPartsFollowTheKotlinStandardLibraryVersion() throws Exception {
        fixture.jar(
                "central",
                "org.example:coroutines:1.6",
                dependencies(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21", "")));
        fixture.jar(
                "central",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21",
                dependencies(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21", "")));
        fixture.jar("central", "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21", "");
        fixture.jar(
                "central",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22",
                dependencies(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22", "")));
        fixture.jar("central", "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22", "");
        fixture.jar("central", "org.jetbrains.kotlin:kotlin-stdlib:1.8.22", "");
        assertEquals(
                List.of(
                        "org.example:coroutines:1.6",
                        "org.jetbrains.kotlin:kotlin-stdlib:1.8.22",
                        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22",
                        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22"),
                resolve("org.example:coroutines:1.6", "org.jetbrains.kotlin:kotlin-stdlib:1.8.22"));
        assertEquals(
                List.of(
                        "org.example:coroutines:1.6",
                        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21",
                        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21"),
                resolve("org.example:coroutines:1.6"));
    }

    @Test
    public void picksTheNewestStableVersionFromTheRepositoryMetadata() throws Exception {
        String metadata =
                "<metadata><groupId>org.example</groupId><artifactId>picked</artifactId>"
                        + "<versioning><latest>2.1.0-rc1</latest><release>2.1.0-rc1</release>"
                        + "<versions><version>1.9</version><version>2.0.0</version>"
                        + "<version>2.0.10</version><version>2.0.9</version>"
                        + "<version>2.1.0-alpha02</version><version>2.1.0-rc1</version>"
                        + "<version>3.0-SNAPSHOT</version></versions></versioning></metadata>";
        fixture.put(
                "central",
                "org/example/picked/maven-metadata.xml",
                metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(
                "2.0.10", fixture.repositories(cache).newestStableVersion("org.example", "picked"));
    }

    private void assertNoCachedFile(String name) throws IOException {
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(cache.toPath())) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith(name)));
        }
    }
}
