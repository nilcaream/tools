package com.nilcaream.cptidy;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IoServiceTest {

    @InjectMocks
    private IoService underTest = new IoService();

    @Spy
    private Logger logger = new Logger();

    @Mock
    private NameResolver nameResolver;

    @Mock
    private FileCompare fileCompare;

    @Spy
    private Io io = new Io();

    private Path root = Jimfs.newFileSystem(Configuration.unix()).getPath("unix").toAbsolutePath();

    @BeforeAll
    static void beforeAll() {
        Locale.setDefault(Locale.ROOT);
    }

    @Test
    void shouldBuildMatchingTarget() throws IOException {
        // given
        Path source = io.write(root.resolve("input").resolve("file.txt"), "test");
        Path targetRoot = root.resolve("output");
        given(nameResolver.resolve(source)).willReturn(new NameResolver.Result("2010-01", "other.txt"));

        // then
        assertThat(underTest.buildMatchingTarget(source, targetRoot)).isEqualTo(targetRoot.resolve("2010-01").resolve("other.txt"));
    }

    @Test
    void shouldHaveSameContent() throws IOException {
        // given
        Path source = io.write(root.resolve("file1.txt"), "test");
        Path target = io.write(root.resolve("file2.txt"), "test");
        given(fileCompare.sameContent(source, target)).willReturn(true);
        given(fileCompare.sameContent(target, source)).willReturn(false);

        // then
        assertThat(underTest.haveSameContent(source, target)).isTrue();
        assertThat(underTest.haveSameContent(target, source)).isFalse();
    }

    @Test
    void shouldDelete() throws IOException {
        // given
        underTest.setDelete(true);
        Path source = io.write(root.resolve("file1.txt"), "test");
        Path kept = io.write(root.resolve("file2.txt"), "test");
        underTest.recordStates(List.of(source, kept));

        // when
        underTest.deleteCopy(source, kept);

        // then
        assertThat(source).doesNotExist();
        assertThat(kept).exists();
    }

    @Test
    void shouldNotDelete() throws IOException {
        // given
        underTest.setDelete(false);
        Path source = io.write(root.resolve("file1.txt"), "test");
        Path kept = io.write(root.resolve("file2.txt"), "test");
        underTest.recordStates(List.of(source, kept));

        // when
        underTest.deleteCopy(source, kept);

        // then
        assertThat(source).exists();
    }

    @Test
    void shouldNotDeleteCopyThatChangedAfterCompare() throws IOException {
        // given
        underTest.setDelete(true);
        Path source = io.write(root.resolve("file1.txt"), "test");
        Path kept = io.write(root.resolve("file2.txt"), "test");
        underTest.recordStates(List.of(source, kept));
        Files.setLastModifiedTime(source, FileTime.fromMillis(Files.getLastModifiedTime(source).toMillis() + 1000));

        // when
        underTest.deleteCopy(source, kept);

        // then
        assertThat(source).exists();
        assertThat(logger.getErrors()).hasSize(1);
    }

    @Test
    void shouldNotDeleteCopyWhenKeptFileChangedOrIsGone() throws IOException {
        // given
        underTest.setDelete(true);
        Path source1 = io.write(root.resolve("file1.txt"), "test");
        Path kept1 = io.write(root.resolve("file2.txt"), "test");
        Path source2 = io.write(root.resolve("file3.txt"), "test");
        Path kept2 = io.write(root.resolve("file4.txt"), "test");
        underTest.recordStates(List.of(source1, kept1, source2, kept2));
        io.write(kept1, "tes");
        Files.delete(kept2);

        // when
        underTest.deleteCopy(source1, kept1);
        underTest.deleteCopy(source2, kept2);

        // then
        assertThat(source1).exists();
        assertThat(source2).exists();
    }

    @Test
    void shouldNotDeleteFileThatWasNeverCompared() throws IOException {
        // given
        underTest.setDelete(true);
        Path longer = io.write(root.resolve("4444.txt"), "test");
        Path shorter = io.write(root.resolve("333.txt"), "test");

        // when
        underTest.retainOne(List.of(longer, shorter));

        // then
        assertThat(shorter).exists();
    }

    @Test
    void shouldBeDryRunWithoutApplyOptions() {
        assertThat(underTest.isDryRun()).isTrue();
        underTest.setTime(true);
        assertThat(underTest.isDryRun()).isFalse();
    }

    @Test
    void shouldMoveToOtherFileSystemOnlyAfterCopyReadsBackSame() throws IOException {
        // given
        underTest.setMove(true);
        Path other = Jimfs.newFileSystem(Configuration.unix()).getPath("other").toAbsolutePath();
        Path file1 = io.write(root.resolve("file1.txt"), "test");
        Path file2 = io.write(root.resolve("file2.txt"), "test");
        Path target1 = other.resolve("file1.txt");
        Path target2 = other.resolve("file2.txt");
        given(nameResolver.buildUniquePath(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(fileCompare.sameContent(file1, target1)).willReturn(true);
        given(fileCompare.sameContent(file2, target2)).willReturn(false);

        // when
        underTest.move(file1, target1);
        assertThatThrownBy(() -> underTest.move(file2, target2)).hasMessageContaining("Copy differs from its source");

        // then
        assertThat(file1).doesNotExist();
        assertThat(target1).hasContent("test");
        assertThat(file2).exists();
        assertThat(target2).doesNotExist();
    }

    @Test
    void shouldDeleteShorter() throws IOException {
        // given
        underTest.setDelete(true);
        Path longer1 = io.write(root.resolve("4444.txt"), "test");
        Path shorter1 = io.write(root.resolve("333.txt"), "test");
        Path longer2 = io.write(root.resolve("4444.dat"), "test");
        Path shorter2 = io.write(root.resolve("333.dat"), "test");

        // when
        retainOne(List.of(longer1, shorter1));
        retainOne(List.of(shorter2, longer2));

        // then
        assertThat(longer1).exists();
        assertThat(longer2).exists();
        assertThat(shorter1).doesNotExist();
        assertThat(shorter2).doesNotExist();
    }

    @Test
    void shouldNotDeleteShorterIfLongerIsHasCopySuffix() throws IOException {
        // given
        underTest.setDelete(true);
        Path longer1 = io.write(root.resolve("333x.txt"), "test");
        Path shorter1 = io.write(root.resolve("333-1.txt"), "test");
        Path longer2 = io.write(root.resolve("333x.dat"), "test");
        Path shorter2 = io.write(root.resolve("333-1.dat"), "test");

        // when
        retainOne(List.of(longer1, shorter1));
        retainOne(List.of(shorter2, longer2));

        // then
        assertThat(longer1).exists();
        assertThat(longer2).exists();
        assertThat(shorter1).doesNotExist();
        assertThat(shorter2).doesNotExist();
    }

    @Test
    void shouldNotDeleteAny() throws IOException {
        // given
        underTest.setDelete(false);
        Path longer1 = io.write(root.resolve("file1.txt"), "test");
        Path shorter1 = io.write(root.resolve("file.txt"), "test");
        Path longer2 = io.write(root.resolve("file1.txt22"), "test");
        Path shorter2 = io.write(root.resolve("file.txt22"), "test");

        // when
        retainOne(List.of(longer1, shorter1));
        retainOne(List.of(shorter2, longer2));

        // then
        assertThat(longer1).exists();
        assertThat(longer2).exists();
        assertThat(shorter1).exists();
        assertThat(shorter2).exists();
    }


    @Test
    void shouldDeleteEarlierByCompare() throws IOException {
        // given
        underTest.setDelete(true);
        Path earlier1 = io.write(root.resolve("file1.txt"), "test");
        Path later1 = io.write(root.resolve("file2.txt"), "test");
        Path earlier2 = io.write(root.resolve("file1.txt22"), "test");
        Path later2 = io.write(root.resolve("file2.txt22"), "test");

        // when
        retainOne(List.of(later1, earlier1));
        retainOne(List.of(earlier2, later2));

        // then
        assertThat(earlier1).doesNotExist();
        assertThat(earlier2).doesNotExist();
        assertThat(later1).exists();
        assertThat(later2).exists();
    }

    @Test
    void shouldNotDeleteByCompare() throws IOException {
        // given
        underTest.setDelete(false);
        Path file1 = io.write(root.resolve("file2.txt"), "test");
        Path earlier1 = io.write(root.resolve("file1.txt"), "test");
        Path file2 = io.write(root.resolve("file2.txt22"), "test");
        Path earlier2 = io.write(root.resolve("file1.txt22"), "test");

        // when
        retainOne(List.of(file1, earlier1));
        retainOne(List.of(earlier2, file2));

        // then
        assertThat(earlier1).exists();
        assertThat(earlier2).exists();
        assertThat(file1).exists();
        assertThat(file2).exists();
    }

    @Test
    void shouldFailOnMovingSameFile() throws IOException {
        // given
        underTest.setMove(true);
        Path file1 = io.write(root.resolve("file.txt"), "test");
        Path file2 = root.resolve("file.txt");

        // then
        assertThatThrownBy(() -> underTest.move(file1, file2)).hasMessageContaining("Both paths are equal for move");
        assertThat(file1).exists();
    }

    @Test
    void shouldFailOnCopyingSameFile() throws IOException {
        // given
        underTest.setCopy(true);
        Path file1 = io.write(root.resolve("file.txt"), "test");
        Path file2 = root.resolve("file.txt");

        // then
        assertThatThrownBy(() -> underTest.copy(file1, file2)).hasMessageContaining("Both paths are equal for copy");
        assertThat(file1).exists();
    }

    @Test
    void shouldMove() throws IOException {
        // given
        underTest.setMove(true);
        Path file1 = io.write(root.resolve("file.txt"), "test");
        Path file2 = root.resolve("file-new.txt");
        given(nameResolver.buildUniquePath(file2)).willReturn(root.resolve("updated.txt"));

        // then
        underTest.move(file1, file2);
        assertThat(file1).doesNotExist();
        assertThat(file2).doesNotExist();
        assertThat(root.resolve("updated.txt")).exists();
    }

    @Test
    void shouldNotMove() throws IOException {
        // given
        underTest.setMove(false);
        Path file1 = io.write(root.resolve("file.txt"), "test");
        Path file2 = root.resolve("file-new.txt");
        given(nameResolver.buildUniquePath(file2)).willReturn(root.resolve("updated.txt"));

        // then
        underTest.move(file1, file2);
        assertThat(file1).exists();
        assertThat(file2).doesNotExist();
        assertThat(root.resolve("updated.txt")).doesNotExist();
    }

    @Test
    void shouldCopy() throws IOException {
        // given
        underTest.setCopy(true);
        Path file1 = io.write(root.resolve("file.txt"), "test");
        Path file2 = root.resolve("file-new.txt");
        given(nameResolver.buildUniquePath(file2)).willReturn(root.resolve("updated.txt"));

        // then
        underTest.copy(file1, file2);
        assertThat(file1).exists();
        assertThat(file2).doesNotExist();
        assertThat(root.resolve("updated.txt")).exists();
    }

    @Test
    void shouldNotCopy() throws IOException {
        // given
        underTest.setCopy(false);
        Path file1 = io.write(root.resolve("file.txt"), "test");
        Path file2 = root.resolve("file-new.txt");
        given(nameResolver.buildUniquePath(file2)).willReturn(root.resolve("updated.txt"));

        // then
        underTest.copy(file1, file2);
        assertThat(file1).exists();
        assertThat(file2).doesNotExist();
        assertThat(root.resolve("updated.txt")).doesNotExist();
    }

    @Test
    void shouldDeleteAllEmptyDirectories() throws IOException {
        // given
        Files.createDirectories(root.resolve("test").resolve("second").resolve("other"));
        underTest.setDelete(true);

        // when
        underTest.deleteEmpty(root, root.resolve("test").resolve("second").resolve("other"));

        // then
        assertThat(root.resolve("test").resolve("second").resolve("other")).doesNotExist();
        assertThat(root.resolve("test").resolve("second")).doesNotExist();
        assertThat(root.resolve("test")).doesNotExist();
        assertThat(root).exists();
    }

    @Test
    void shouldNotDeleteAllEmptyDirectories() throws IOException {
        // given
        Files.createDirectories(root.resolve("test").resolve("second").resolve("other"));
        underTest.setDelete(false);

        // when
        underTest.deleteEmpty(root, root.resolve("test").resolve("second").resolve("other"));

        // then
        assertThat(root.resolve("test").resolve("second").resolve("other")).exists();
        assertThat(root.resolve("test").resolve("second")).exists();
        assertThat(root.resolve("test")).exists();
        assertThat(root).exists();
    }

    @Test
    void shouldDeleteSomeEmptyDirectories() throws IOException {
        // given
        Files.createDirectories(root.resolve("test").resolve("second").resolve("other"));
        io.write(root.resolve("test").resolve("test-file.jpg"), "testX");
        underTest.setDelete(true);

        // when
        underTest.deleteEmpty(root, root.resolve("test").resolve("second").resolve("other"));

        // then
        assertThat(root.resolve("test").resolve("second").resolve("other")).doesNotExist();
        assertThat(root.resolve("test").resolve("second")).doesNotExist();
        assertThat(root.resolve("test")).exists();
        assertThat(root).exists();
    }

    @Test
    void shouldDeleteEmptyDirectoriesWithIgnoredFiles1() throws IOException {
        // given
        Files.createDirectories(root.resolve("test").resolve("second").resolve("other"));
        io.write(root.resolve("test").resolve("empty.jpg"), "");
        underTest.setDelete(true);

        // when
        underTest.deleteEmpty(root, root.resolve("test").resolve("second").resolve("other"));

        // then
        assertThat(root.resolve("test").resolve("second").resolve("other")).doesNotExist();
        assertThat(root.resolve("test").resolve("second")).doesNotExist();
        assertThat(root.resolve("test")).doesNotExist();
        assertThat(root).exists();
    }

    @Test
    void shouldDeleteEmptyDirectoriesWithIgnoredFiles2() throws IOException {
        // given
        Files.createDirectories(root.resolve("test").resolve("second").resolve("other"));
        io.write(root.resolve("test").resolve(".picasa.ini"), "not empty but ignored");
        underTest.setDelete(true);
        underTest.setIgnoredFiles(Set.of(".picasa.ini"));

        // when
        underTest.deleteEmpty(root, root.resolve("test").resolve("second").resolve("other"));

        // then
        assertThat(root.resolve("test").resolve("second").resolve("other")).doesNotExist();
        assertThat(root.resolve("test").resolve("second")).doesNotExist();
        assertThat(root.resolve("test")).doesNotExist();
        assertThat(root).exists();
    }

    @Test
    void shouldNotDeleteEmptyDirectoriesWithTwoIgnoredFiles() throws IOException {
        // given
        Files.createDirectories(root.resolve("test").resolve("second").resolve("other"));
        io.write(root.resolve("test").resolve(".picasa.ini"), "not empty but ignored");
        io.write(root.resolve("test").resolve("empty.txt"), "");
        underTest.setDelete(true);

        // when
        underTest.deleteEmpty(root, root.resolve("test").resolve("second").resolve("other"));

        // then
        assertThat(root.resolve("test").resolve("second").resolve("other")).doesNotExist();
        assertThat(root.resolve("test").resolve("second")).doesNotExist();
        assertThat(root.resolve("test")).exists();
        assertThat(root.resolve("test").resolve(".picasa.ini")).exists();
        assertThat(root.resolve("test").resolve("empty.txt")).exists();
        assertThat(root).exists();
    }

    @Test
    void shouldNotDeleteNonEmptyDirectories() throws IOException {
        // given
        Files.createDirectories(root.resolve("test").resolve("second").resolve("other"));
        underTest.setDelete(true);

        // when
        underTest.deleteEmpty(root, root.resolve("test").resolve("second"));

        // then
        assertThat(root.resolve("test").resolve("second").resolve("other")).exists();
        assertThat(root.resolve("test").resolve("second")).exists();
        assertThat(root.resolve("test")).exists();
        assertThat(root).exists();
    }

    @Test
    void shouldNotDeleteRoot() throws IOException {
        // given
        Files.createDirectories(root);
        underTest.setDelete(true);

        // when
        underTest.deleteEmpty(root, root.resolve("other").getParent().toAbsolutePath());

        // then
        assertThat(root).exists();
    }

    @Test
    void shouldNotFileOnFileSize() throws IOException {
        // given
        Path exist = io.write(root.resolve("file.txt"), "test");
        Path doesNotExist = root.resolve("file-new.txt");

        // then
        assertThat(underTest.size(exist)).isEqualTo(4);
        assertThat(underTest.size(doesNotExist)).isEqualTo(-1);
    }

    @Test
    void shouldBeSameFile() throws IOException {
        // given
        Path source = io.write(root.resolve("file1.txt"), "test");
        Path target = io.write(root.resolve("file2.txt"), "test");
        given(io.isSameFile(source, target)).willReturn(true);
        given(io.isSameFile(target, source)).willReturn(false);

        // then
        assertThat(underTest.isSameFile(source, target)).isTrue();
        assertThat(underTest.isSameFile(target, source)).isFalse();
    }

    @Test
    void shouldBuildCopyTarget() {
        // given
        Path source = root.resolve("a").resolve("b").resolve("file");
        Path sourceRoot = root.resolve("a");
        Path targetRoot = root.resolve("target");

        // then
        assertThat(underTest.buildCopyTarget(source, sourceRoot, targetRoot)).isEqualTo(root.resolve("target").resolve("b").resolve("file"));
    }

    @Test
    void shouldSetFlags() {
        // then
        assertThat(underTest.isCopy()).isFalse();
        assertThat(underTest.isMove()).isFalse();
        assertThat(underTest.isDelete()).isFalse();

        // then
        underTest.setCopy(true);
        assertThat(underTest.isCopy()).isTrue();
        assertThat(underTest.isMove()).isFalse();
        assertThat(underTest.isDelete()).isFalse();

        // then
        underTest.setCopy(false);
        underTest.setMove(true);
        assertThat(underTest.isCopy()).isFalse();
        assertThat(underTest.isMove()).isTrue();
        assertThat(underTest.isDelete()).isFalse();

        // then
        underTest.setCopy(false);
        underTest.setMove(false);
        underTest.setDelete(true);
        assertThat(underTest.isCopy()).isFalse();
        assertThat(underTest.isMove()).isFalse();
        assertThat(underTest.isDelete()).isTrue();
    }

    @Test
    void shouldDeleteBestMatchPath1() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("test-file"), "testX");
        Path path2 = io.write(root.resolve("tes").resolve("test-file-1-2.jpg"), "testX");
        Path path3 = io.write(root.resolve("test").resolve("test-file-0.jpg"), "testX");
        underTest.setDelete(true);

        // when
        retainOne(List.of(path1, path2, path3));

        // then
        assertThat(path1).exists();
        assertThat(path2).doesNotExist();
        assertThat(path3).doesNotExist();
    }

    @Test
    void shouldDeleteBestMatchPath2() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("200-test-file.jpg"), "testX");
        Path path2 = io.write(root.resolve("tes").resolve("200-test-file-0.jp"), "testX");
        Path path3 = io.write(root.resolve("test").resolve("111-test-file.j"), "testX");
        underTest.setDelete(true);

        // when
        retainOne(List.of(path3, path1, path2));

        // then
        assertThat(path1).exists();
        assertThat(path2).doesNotExist();
        assertThat(path3).doesNotExist();
    }

    @Test
    void shouldDeleteBestMatchPath3() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("test-file-jpg"), "testX");
        Path path2 = io.write(root.resolve("tes").resolve("test-file-jp"), "testX");
        Path path3 = io.write(root.resolve("test").resolve("test-file-j"), "testX");
        Path path4 = io.write(root.resolve("test").resolve("test-file-zzz"), "testX");
        underTest.setDelete(true);

        // when
        retainOne(List.of(path3, path1, path4, path2));

        // then
        assertThat(path1).doesNotExist();
        assertThat(path2).doesNotExist();
        assertThat(path3).doesNotExist();
        assertThat(path4).exists();
    }

    @Test
    void shouldSelectFilesWithSameContent() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("test-file-jpg"), "testX");
        Path path2 = io.write(root.resolve("tes").resolve("test-file-jp"), "testX");
        Path path3 = io.write(root.resolve("test").resolve("test-file-j"), "AAAAA");
        Path path4 = io.write(root.resolve("test").resolve("test-file-zzz"), "testX");
        Path path5 = io.write(root.resolve("test").resolve("test-file-yyy"), "AAAAA");
        List<Path> paths = List.of(path1, path2, path3, path4, path5);
        given(fileCompare.groupBySameContent(paths)).willReturn(List.of(List.of(path3, path5), List.of(path1, path2, path4)));

        // when
        List<List<Path>> actual = underTest.groupBySameContent(paths);

        // then
        assertThat(actual).containsExactly(List.of(path3, path5), List.of(path1, path2, path4));
    }

    @Test
    void shouldLeaveOutSameFileFromSetsWithSameContent() throws IOException {
        // given
        Path path1 = io.write(root.resolve("link").resolve("a"), "testX");
        Path path2 = Files.createLink(root.resolve("link").resolve("b"), path1);
        Path path3 = io.write(root.resolve("link").resolve("c"), "testX");
        Path path4 = io.write(root.resolve("link").resolve("d"), "AAAAA");
        Path path5 = Files.createLink(root.resolve("link").resolve("e"), path4);
        List<Path> paths = List.of(path1, path2, path3, path4, path5);
        given(fileCompare.groupBySameContent(paths)).willReturn(List.of(List.of(path1, path2, path3), List.of(path4, path5)));

        // when
        List<List<Path>> actual = underTest.groupBySameContent(paths);

        // then
        assertThat(actual).containsExactly(List.of(path1, path3));
    }

    @Test
    void shouldNotDeleteBestMatchPath() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("test-file.jpg"), "testX");
        Path path2 = io.write(root.resolve("tes").resolve("test-file.jp"), "testX");
        Path path3 = io.write(root.resolve("test").resolve("test-file.j"), "testX");
        Path path4 = io.write(root.resolve("test").resolve("test-file.zzz"), "testX");

        // when
        retainOne(List.of(path3, path1, path4, path2));

        // then
        assertThat(path1).exists();
        assertThat(path2).exists();
        assertThat(path3).exists();
        assertThat(path4).exists();
    }

    @Test
    void shouldFailOnRetainSinglePath() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("test-file.jpg"), "testX");
        underTest.setDelete(true);

        // when
        assertThatThrownBy(() -> underTest.retainOne(List.of(path1))).hasMessageContaining("Should provide more than 1 path");
    }

    @Test
    void shouldFailOnRetainOnSinglePath() throws IOException {
        // given
        Path path = io.write(root.resolve("te").resolve("test-file.jpg"), "testX");
        underTest.setDelete(true);

        // when
        assertThatThrownBy(() -> underTest.retainOne(List.of(path))).hasMessageContaining("Should provide more than 1 path");
    }

    @Test
    void shouldFailOnRetainNonDistinctPaths() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("test-file.jpg"), "testX");
        Path path2 = root.resolve("te").resolve("test-file.jpg");
        underTest.setDelete(true);

        // when
        assertThatThrownBy(() -> underTest.retainOne(List.of(path1, path2))).hasMessageContaining("Should provide different paths");
    }

    @Test
    void shouldFailOnRetainNonDistinctNonRealPaths2() throws IOException {
        // given
        io.write(root.resolve("te").resolve("second").resolve("other.txt"), "o");
        Path path1 = io.write(root.resolve("te").resolve("test-file.jpg"), "testX");
        Path path2 = root.resolve("te").resolve("second").resolve("..").resolve("test-file.jpg");
        underTest.setDelete(true);

        // when
        assertThatThrownBy(() -> underTest.retainOne(List.of(path1, path2))).hasMessageContaining("Should provide different paths");
    }

    @Test
    void shouldFailOnRetainNonDistinctNonRealPaths3() throws IOException {
        // given
        io.write(root.resolve("te").resolve("second").resolve("other.txt"), "o");
        Path path1 = io.write(root.resolve("te").resolve("test-file.jpg"), "testX");
        Path path2 = root.resolve("te").resolve("second").resolve("..").resolve("test-file.jpg");
        Path path3 = io.write(root.resolve("te").resolve("other.dat"), "testX");
        underTest.setDelete(true);

        // when
        assertThatThrownBy(() -> underTest.retainOne(List.of(path3, path1, path2))).hasMessageContaining("Should provide different paths");
    }

    @Test
    void shouldFailOnDeleteNonDistinctPaths() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("test-file.jpg"), "testX");
        Path path2 = root.resolve("te").resolve("test-file.jpg");
        underTest.setDelete(true);

        // when
        assertThatThrownBy(() -> underTest.retainOne(List.of(path1, path2))).hasMessageContaining("Should provide different paths");
    }

    @Test
    void shouldSetTimestampFromSourceMatch() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("20101211-test.jpg"), "testX");
        Path path2 = io.write(root.resolve("te").resolve("20010102-wwww.jpg"), "testX");
        FileTime fileTime = FileTime.from(Instant.parse("2010-12-11T10:15:30.00Z"));
        Files.getFileAttributeView(path1, BasicFileAttributeView.class).setTimes(fileTime, null, fileTime);
        underTest.setTime(true);

        // when
        underTest.fixTimestamps(path1, path2);

        // then
        assertThat(Files.readAttributes(path1, BasicFileAttributes.class).creationTime().toInstant()).isEqualTo(Instant.parse("2010-12-11T10:15:30.00Z"));
        assertThat(Files.readAttributes(path2, BasicFileAttributes.class).creationTime().toInstant()).isEqualTo(Instant.parse("2010-12-11T10:15:30.00Z"));
    }

    @Test
    void shouldSetTimestampFromTargetMatch() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("20101211-test.jpg"), "testX");
        Path path2 = io.write(root.resolve("te").resolve("20010102-wwww.jpg"), "testX");
        FileTime fileTime = FileTime.from(Instant.parse("2010-12-11T10:15:30.00Z"));
        Files.getFileAttributeView(path1, BasicFileAttributeView.class).setTimes(fileTime, null, fileTime);
        underTest.setTime(true);

        // when
        underTest.fixTimestamps(path2, path1);

        // then
        assertThat(Files.readAttributes(path1, BasicFileAttributes.class).creationTime().toInstant()).isEqualTo(Instant.parse("2010-12-11T10:15:30.00Z"));
        assertThat(Files.readAttributes(path2, BasicFileAttributes.class).creationTime().toInstant()).isEqualTo(Instant.parse("2010-12-11T10:15:30.00Z"));
    }

    @Test
    void shouldSetTimestampFromFileName() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("20101211-test.jpg"), "testX");
        Path path2 = io.write(root.resolve("te").resolve("20010102-wwww.jpg"), "testX");
        underTest.setTime(true);

        // when
        underTest.fixTimestamps(path2, path1);

        // then
        assertThat(Files.readAttributes(path1, BasicFileAttributes.class).creationTime().toInstant()).isEqualTo(Instant.parse("2010-12-11T12:00:00.00Z"));
        assertThat(Files.readAttributes(path2, BasicFileAttributes.class).creationTime().toInstant()).isEqualTo(Instant.parse("2001-01-02T12:00:00.00Z"));
    }

    @Test
    void shouldSetDefaultTimestamp() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("2010xx1211-test.jpg"), "testX");
        Path path2 = io.write(root.resolve("te").resolve("30010102-wwww.jpg"), "testX");
        underTest.setTime(true);

        // when
        underTest.fixTimestamps(path2, path1);

        // then
        assertThat(Files.readAttributes(path1, BasicFileAttributes.class).creationTime().toInstant()).isEqualTo(Instant.parse("2000-01-01T12:00:00.00Z"));
        assertThat(Files.readAttributes(path2, BasicFileAttributes.class).creationTime().toInstant()).isEqualTo(Instant.parse("2000-01-01T12:00:00.00Z"));
    }

    @Test
    void shouldNotSetDefaultTimestamp() throws IOException {
        // given
        Path path1 = io.write(root.resolve("te").resolve("2010xx1211-test.jpg"), "testX");
        Path path2 = io.write(root.resolve("te").resolve("30010102-wwww.jpg"), "testX");

        // when
        underTest.fixTimestamps(path2, path1);

        // then
        assertThat(Files.readAttributes(path1, BasicFileAttributes.class).creationTime().toInstant()).isNotEqualTo(Instant.parse("2000-01-01T12:00:00.00Z"));
        assertThat(Files.readAttributes(path2, BasicFileAttributes.class).creationTime().toInstant()).isNotEqualTo(Instant.parse("2000-01-01T12:00:00.00Z"));
    }


    @Test
    void shouldCountZeroBlocks1() throws IOException {
        // given
        byte[] buffer = new byte[4];
        Path pathA = root.resolve("zeros.bin");
        io.write(pathA, new byte[]{
                0, 0, 0, 0,
                2, 2, 2, 2,
                0, 0, 1, 1,
                0
        });

        // when
        long actual = underTest.countZeroBytes(pathA, buffer);

        // then
        // the last block is one byte long
        assertThat(actual).isEqualTo(5);
    }

    @Test
    void shouldCountZeroBlocks2() throws IOException {
        // given
        byte[] buffer = new byte[4];
        Path pathA = root.resolve("zeros.bin");
        io.write(pathA, new byte[]{
                0, 0, 0, 0,
                2, 2, 2, 2,
                0, 0, 0, 1,
                6
        });

        // when
        long actual = underTest.countZeroBytes(pathA, buffer);

        // then
        assertThat(actual).isEqualTo(4);
    }

    @Test
    void shouldCountZeroBlocks3() throws IOException {
        // given
        byte[] buffer = new byte[4];
        Path pathA = root.resolve("zeros.bin");
        io.write(pathA, new byte[]{
                5, 0, 0, 0,
                2, 2, 2, 2,
                0, 0, 0, 1
        });

        // when
        long actual = underTest.countZeroBytes(pathA, buffer);

        // then
        assertThat(actual).isEqualTo(0);
    }

    // the actions delete only files whose content they compared
    private void retainOne(List<Path> paths) throws IOException {
        underTest.recordStates(paths);
        underTest.retainOne(paths);
    }
}