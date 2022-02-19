package com.nilcaream.cptidy;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IoServiceTest {

    @InjectMocks
    private IoService underTest = new IoService();

    @Spy
    private Logger logger = new Logger();

    @Mock
    private NameResolver nameResolver;

    @Spy
    private Io io = new Io();

    private Path root = Jimfs.newFileSystem(Configuration.unix()).getPath("unix").toAbsolutePath();

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
        root = Files.createTempDirectory("cptidy-");
        Path source = io.write(root.resolve("file1.txt"), "test");
        Path target = io.write(root.resolve("file2.txt"), "test");
        given(io.haveSameContent(source, target)).willReturn(true);
        given(io.haveSameContent(target, source)).willReturn(false);

        // then
        assertThat(underTest.haveSameContent(source, target)).isTrue();
        assertThat(underTest.haveSameContent(target, source)).isFalse();
    }

    @Test
    void shouldDelete() throws IOException {
        // given
        underTest.setDelete(true);
        Path source = io.write(root.resolve("file1.txt"), "test");

        // when
        underTest.delete(source);

        // then
        assertThat(source).doesNotExist();
    }

    @Test
    void shouldNotDelete() throws IOException {
        // given
        underTest.setDelete(false);
        Path source = io.write(root.resolve("file1.txt"), "test");

        // when
        underTest.delete(source);

        // then
        assertThat(source).exists();
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
        underTest.deleteOne(longer1, shorter1);
        underTest.deleteOne(shorter2, longer2);

        // then
        assertThat(longer1).exists();
        assertThat(longer2).exists();
        assertThat(shorter1).doesNotExist();
        assertThat(shorter2).doesNotExist();
    }

    @Test
    void shouldDeleteLongerWhenItStartsAsShorter() throws IOException {
        // given
        underTest.setDelete(true);
        Path longer1 = io.write(root.resolve("333x.txt"), "test");
        Path shorter1 = io.write(root.resolve("333.txt"), "test");
        Path longer2 = io.write(root.resolve("333x.dat"), "test");
        Path shorter2 = io.write(root.resolve("333.dat"), "test");

        // when
        underTest.deleteOne(longer1, shorter1);
        underTest.deleteOne(shorter2, longer2);

        // then
        assertThat(longer1).doesNotExist();
        assertThat(longer2).doesNotExist();
        assertThat(shorter1).exists();
        assertThat(shorter2).exists();
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
        underTest.deleteOne(longer1, shorter1);
        underTest.deleteOne(shorter2, longer2);

        // then
        assertThat(longer1).exists();
        assertThat(longer2).exists();
        assertThat(shorter1).exists();
        assertThat(shorter2).exists();
    }


    @Test
    void shouldDeleteByCompare() throws IOException {
        // given
        underTest.setDelete(true);
        Path file1 = io.write(root.resolve("file2.txt"), "test");
        Path earlier1 = io.write(root.resolve("file1.txt"), "test");
        Path file2 = io.write(root.resolve("file2.txt22"), "test");
        Path earlier2 = io.write(root.resolve("file1.txt22"), "test");

        // when
        underTest.deleteOne(file1, earlier1);
        underTest.deleteOne(earlier2, file2);

        // then
        assertThat(earlier1).exists();
        assertThat(earlier2).exists();
        assertThat(file1).doesNotExist();
        assertThat(file2).doesNotExist();
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
        underTest.deleteOne(file1, earlier1);
        underTest.deleteOne(earlier2, file2);

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
}