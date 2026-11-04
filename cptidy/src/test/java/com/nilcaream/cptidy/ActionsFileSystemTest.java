package com.nilcaream.cptidy;

import com.nilcaream.atto.Atto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// Actions wired as App wires them, on a real temporary file system.
class ActionsFileSystemTest {

    @TempDir
    private Path root;

    private Actions actions;
    private IoService ioService;
    private Logger logger;

    private Path source;
    private Path target;

    @BeforeEach
    void setUp() throws IOException {
        Atto atto = Atto.builder().build();
        actions = atto.instance(Actions.class);
        ioService = atto.instance(IoService.class);
        logger = atto.instance(Logger.class);
        logger.disable();
        source = Files.createDirectories(root.resolve("source"));
        target = Files.createDirectories(root.resolve("target"));
    }

    @Test
    void shouldHaveAllDestructiveFlagsOffByDefault() {
        assertThat(ioService.isDelete()).isFalse();
        assertThat(ioService.isMove()).isFalse();
        assertThat(ioService.isCopy()).isFalse();
        assertThat(ioService.isTime()).isFalse();
    }

    @Test
    void shouldOrganizeWithoutChangesInDryRun() throws IOException {
        // given
        write(source.resolve("20200102-a.jpg"), 2048, 1);
        write(source.resolve("unknown.txt"), 2048, 2);
        Set<Path> before = files(root);

        // when
        actions.organize("organize", source, target);

        // then
        assertThat(files(root)).isEqualTo(before);
    }

    @Test
    void shouldOrganizeByMove() throws IOException {
        // given
        ioService.setMove(true);
        write(source.resolve("x").resolve("20200102-a.jpg"), 2048, 1);
        write(source.resolve("IMG_20210304_Beach.JPG"), 2048, 2);
        write(source.resolve("unknown.txt"), 2048, 3);

        // when
        actions.organize("organize", source, target);

        // then
        assertThat(relative(source)).containsExactly("unknown.txt");
        assertThat(relative(target)).containsExactlyInAnyOrder("2020-01/20200102-a.jpg", "2021-03/20210304-img-beach.jpg");
    }

    @Test
    void shouldOrganizeToUniqueNameWhenTargetDiffers() throws IOException {
        // given
        ioService.setMove(true);
        write(source.resolve("20200102-a.jpg"), 2048, 1);
        write(target.resolve("2020-01").resolve("20200102-a.jpg"), 2048, 2);

        // when
        actions.organize("organize", source, target);

        // then
        assertThat(relative(source)).isEmpty();
        assertThat(relative(target)).containsExactlyInAnyOrder("2020-01/20200102-a.jpg", "2020-01/20200102-a-0.jpg");
        assertThat(content(target.resolve("2020-01").resolve("20200102-a.jpg"))).isEqualTo(bytes(2048, 2));
    }

    @Test
    void shouldOrganizeByDeletingDuplicate() throws IOException {
        // given
        ioService.setDelete(true);
        ioService.setMove(true);
        write(source.resolve("20200102-a.jpg"), 2048, 1);
        write(target.resolve("2020-01").resolve("20200102-a.jpg"), 2048, 1);

        // when
        actions.organize("organize", source, target);

        // then
        assertThat(relative(source)).isEmpty();
        assertThat(relative(target)).containsExactly("2020-01/20200102-a.jpg");
    }

    @Test
    void shouldReorganizeInPlace() throws IOException {
        // given
        ioService.setMove(true);
        write(source.resolve("2019-12").resolve("20200102-a.jpg"), 2048, 1);
        write(source.resolve("2020-01").resolve("20200103-b.jpg"), 2048, 2);

        // when
        actions.organize("reorganize", source, source);

        // then
        assertThat(relative(source)).containsExactlyInAnyOrder("2020-01/20200102-a.jpg", "2020-01/20200103-b.jpg");
    }

    @Test
    void shouldDeleteSourceCopiesOfTargetFiles() throws IOException {
        // given
        ioService.setDelete(true);
        write(target.resolve("2020-01").resolve("20200102-a.jpg"), 2048, 1);
        write(target.resolve("2020-01").resolve("small.jpg"), 1000, 5);
        write(source.resolve("copy.bin"), 2048, 1);
        write(source.resolve("unique.bin"), 2048, 2);
        write(source.resolve("small-copy.bin"), 1000, 5);

        // when
        actions.findCopies("no-copies", source, target);

        // then
        assertThat(relative(source)).containsExactlyInAnyOrder("unique.bin", "small-copy.bin");
        assertThat(relative(target)).hasSize(2);
    }

    @Test
    void shouldRemoveDuplicatesGloballyKeepingOne() throws IOException {
        // given
        ioService.setDelete(true);
        write(source.resolve("a").resolve("x.jpg"), 2048, 1);
        write(source.resolve("b").resolve("x-1.jpg"), 2048, 1);
        write(source.resolve("c").resolve("x-copy.jpg"), 2048, 1);
        write(source.resolve("c").resolve("other.jpg"), 2048, 2);

        // when
        actions.removeDuplicatesGlobally("no-source-copies", source);

        // then
        // the longest name without a copy suffix is kept
        assertThat(relative(source)).containsExactlyInAnyOrder("c/x-copy.jpg", "c/other.jpg");
    }

    @Test
    void shouldRemoveDuplicatesGloballyOnlyInLargestSetOfSameSize() throws IOException {
        // given
        ioService.setDelete(true);
        write(source.resolve("a1.jpg"), 2048, 1);
        write(source.resolve("a2.jpg"), 2048, 1);
        write(source.resolve("a3.jpg"), 2048, 1);
        write(source.resolve("b1.jpg"), 2048, 2);
        write(source.resolve("b2.jpg"), 2048, 2);

        // when
        actions.removeDuplicatesGlobally("no-source-copies", source);

        // then
        // names of the same length: the last in order is kept
        assertThat(relative(source)).containsExactlyInAnyOrder("a3.jpg", "b1.jpg", "b2.jpg");
    }

    @Test
    void shouldKeepLargeFilesThatDifferOnlyByZeroedHole() throws IOException {
        // given
        ioService.setDelete(true);
        byte[] bytes = bytes(64 * 1024 * 1024, 7);
        Files.write(source.resolve("20200101-clip-1.mp4"), bytes);
        Files.write(target.resolve("20200101-clip-1.mp4"), bytes);
        java.util.Arrays.fill(bytes, 20 * 1024 * 1024, 21 * 1024 * 1024, (byte) 0);
        Files.write(source.resolve("20200101-clip.mp4"), bytes);
        Files.write(target.resolve("20200101-clip.mp4"), bytes);

        // when
        actions.removeDuplicatesGlobally("no-source-copies", source);
        actions.removeDuplicatesPerDirectory("no-target-copies", target);

        // then
        assertThat(relative(source)).containsExactly("20200101-clip-1.mp4", "20200101-clip.mp4");
        assertThat(relative(target)).containsExactly("20200101-clip-1.mp4", "20200101-clip.mp4");
    }

    @Test
    void shouldRemoveDuplicatesPerDirectory() throws IOException {
        // given
        ioService.setDelete(true);
        write(target.resolve("2020-01").resolve("x.jpg"), 2048, 1);
        write(target.resolve("2020-01").resolve("x-1.jpg"), 2048, 1);
        write(target.resolve("2020-01").resolve("y.jpg"), 2048, 2);
        write(target.resolve("2020-02").resolve("x.jpg"), 2048, 1);

        // when
        actions.removeDuplicatesPerDirectory("no-target-copies", target);

        // then
        assertThat(relative(target)).containsExactlyInAnyOrder("2020-01/x.jpg", "2020-01/y.jpg", "2020-02/x.jpg");
    }

    @Test
    void shouldReportDeletesInDryRunOfDuplicatesPerDirectory() throws IOException {
        // given
        write(target.resolve("d").resolve("x.jpg"), 2048, 1);
        write(target.resolve("d").resolve("x-1.jpg"), 2048, 1);
        write(target.resolve("d").resolve("x-2.jpg"), 2048, 1);
        Set<Path> before = files(root);

        // when
        Statistics statistics = actions.removeDuplicatesPerDirectory("no-target-copies", target);

        // then
        assertThat(files(root)).isEqualTo(before);
        assertThat(statistics.getData().get("DELETE").getCount()).isEqualTo(3);
    }

    @Test
    void shouldRemoveEmptyDirectories() throws IOException {
        // given
        ioService.setDelete(true);
        ioService.setIgnoredFiles(Set.of("Thumbs.db"));
        Files.createDirectories(target.resolve("a").resolve("b").resolve("c"));
        write(target.resolve("zero").resolve("empty.jpg"), 0, 1);
        write(target.resolve("ignored").resolve("Thumbs.db"), 10, 1);
        write(target.resolve("kept").resolve("x.jpg"), 10, 1);

        // when
        actions.removeEmpty("no-empty", target);

        // then
        assertThat(target).exists();
        assertThat(relative(target)).containsExactly("kept/x.jpg");
        try (Stream<Path> list = Files.list(target)) {
            assertThat(list.map(p -> p.getFileName().toString())).containsExactly("kept");
        }
    }

    @Test
    void shouldNotRemoveEmptyDirectoriesInDryRun() throws IOException {
        // given
        Path empty = Files.createDirectories(target.resolve("a").resolve("b"));

        // when
        actions.removeEmpty("no-empty", target);

        // then
        assertThat(empty).exists();
    }

    @Test
    void shouldSynchronizeByCopyNeverOverwriting() throws IOException {
        // given
        ioService.setCopy(true);
        write(source.resolve("2020-01").resolve("new.jpg"), 2048, 1);
        write(source.resolve("2020-01").resolve("changed.jpg"), 2048, 2);
        write(target.resolve("2020-01").resolve("changed.jpg"), 3072, 3);

        // when
        actions.synchronize("synchronize", source, target);

        // then
        assertThat(relative(target)).containsExactlyInAnyOrder("2020-01/new.jpg", "2020-01/changed.jpg", "2020-01/changed-0.jpg");
        assertThat(content(target.resolve("2020-01").resolve("changed.jpg"))).isEqualTo(bytes(3072, 3));
        assertThat(content(target.resolve("2020-01").resolve("changed-0.jpg"))).isEqualTo(bytes(2048, 2));
        assertThat(relative(source)).hasSize(2);
    }

    @Test
    void shouldSynchronizeSameSizeEditByModifiedTime() throws IOException {
        // given
        ioService.setCopy(true);
        Path edited = write(source.resolve("2020-01").resolve("edited.jpg"), 2048, 4);
        Path old = write(target.resolve("2020-01").resolve("edited.jpg"), 2048, 5);
        Files.setLastModifiedTime(old, FileTime.from(Files.getLastModifiedTime(edited).toInstant().minusSeconds(3)));

        // when
        actions.synchronize("synchronize", source, target);

        // then
        assertThat(relative(target)).containsExactlyInAnyOrder("2020-01/edited.jpg", "2020-01/edited-0.jpg");
        assertThat(content(target.resolve("2020-01").resolve("edited-0.jpg"))).isEqualTo(bytes(2048, 4));
    }

    @Test
    void shouldSkipSynchronizationWithoutReadingContentWhenSizeAndTimeMatch() throws IOException {
        // given
        ioService.setCopy(true);
        Path sourceFile = write(source.resolve("2020-01").resolve("same.jpg"), 2048, 4);
        Path targetFile = write(target.resolve("2020-01").resolve("same.jpg"), 2048, 5);
        Files.setLastModifiedTime(targetFile, FileTime.from(Files.getLastModifiedTime(sourceFile).toInstant().minusMillis(1900)));

        // when
        Statistics statistics = actions.synchronize("synchronize", source, target);

        // then
        assertThat(relative(target)).containsExactly("2020-01/same.jpg");
        assertThat(statistics.getData().get("SAME-ATTRIBUTES").getCount()).isEqualTo(1);
    }

    @Test
    void shouldNotCopyFileWithSameContentAndDifferentTime() throws IOException {
        // given
        ioService.setCopy(true);
        Path sourceFile = write(source.resolve("2020-01").resolve("same.jpg"), 2048, 4);
        Path targetFile = write(target.resolve("2020-01").resolve("same.jpg"), 2048, 4);
        Files.setLastModifiedTime(targetFile, FileTime.from(Files.getLastModifiedTime(sourceFile).toInstant().minusSeconds(3600)));

        // when
        Statistics statistics = actions.synchronize("synchronize", source, target);

        // then
        assertThat(relative(target)).containsExactly("2020-01/same.jpg");
        assertThat(statistics.getData().get("SAME-CONTENT").getCount()).isEqualTo(1);
    }

    @Test
    void shouldCountEmptyBlocks() throws IOException {
        // given
        byte[] bytes = bytes(4096, 1);
        java.util.Arrays.fill(bytes, 0, 2048, (byte) 0);
        Files.write(source.resolve("half.bin"), bytes);
        write(source.resolve("full.bin"), 4096, 2);

        // when
        Statistics statistics = actions.countEmptyBlocks("count-zeros", source, 1024);

        // then
        assertThat(statistics.getData().get("EMPTY-BLOCKS").getCount()).isEqualTo(1);
        assertThat(relative(source)).hasSize(2);
    }

    @Test
    void shouldLoadConfiguration() throws IOException {
        // given
        Path file = root.resolve("cptidy.json");
        Files.writeString(file, "{\"ignored\": [\"Thumbs.db\", \".picasa.ini\"], \"explicitDates\": {\"scan.*\": \"2001-02-03\", \"dsc.*\": \"FILE_DATE\"}}");
        Configuration configuration = new Configuration();

        // when
        configuration.load(file);

        // then
        assertThat(configuration.getIgnored()).containsExactlyInAnyOrder("Thumbs.db", ".picasa.ini");
        assertThat(configuration.getExplicitDates()).containsEntry("scan.*", "2001-02-03").containsEntry("dsc.*", "FILE_DATE").hasSize(2);
    }

    private static byte[] bytes(int size, int seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    private static Path write(Path path, int size, int seed) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.write(path, bytes(size, seed));
    }

    private static byte[] content(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    private static Set<Path> files(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.collect(Collectors.toSet());
        }
    }

    private static List<String> relative(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).map(p -> root.relativize(p).toString()).sorted().collect(Collectors.toList());
        }
    }
}
