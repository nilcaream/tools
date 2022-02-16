package com.nilcaream.cptidy;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IoServiceTest {

    @InjectMocks
    private IoService underTest = new IoService();

    @Spy
    private Io io = new Io();

    @Spy
    private Logger logger = new Logger();

    @Mock
    private ExifService exifService;

    private static Stream<Arguments> provideFileSystem() throws IOException {
        Path unix = Jimfs.newFileSystem(Configuration.unix()).getPath("unix").toAbsolutePath();
        Path win = Jimfs.newFileSystem(Configuration.windows()).getPath("win").toAbsolutePath();
        Path osx = Jimfs.newFileSystem(Configuration.osX()).getPath("osx").toAbsolutePath();

        return Stream.of(
                Arguments.of("UNIX", create(unix, "input"), create(unix, "output")),
                Arguments.of("WIN", create(win, "input"), create(win, "output")),
                Arguments.of("OSX", create(osx, "input"), create(osx, "output"))
        );
    }

    private static Path create(Path root, String name) throws IOException {
        Path result = root.resolve(name);
        Files.createDirectories(result);
        return result;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldResolveNameWithDate(String fsType, Path input, Path output) throws IOException {
        // given
        Path file = io.write(input.resolve("TEST file$20110219-0103.jpg"), "test");

        // when
        Path actual = underTest.buildMatchingTarget(file, output);

        // then
        assertThat(actual).isEqualTo(output.resolve("2011-02").resolve("test-file-20110219-0103.jpg"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldResolveNameWithExif(String fsType, Path input, Path output) throws IOException {
        // given
        Path file = io.write(input.resolve("test-file.jpg"), "test");
        given(exifService.getDate(eq(file))).willReturn("2122-12");

        // when
        Path actual = underTest.buildMatchingTarget(file, output);

        // then
        assertThat(actual).isEqualTo(output.resolve("2122-12").resolve("test-file.jpg"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldNotMatch(String fsType, Path input, Path output) throws IOException {
        // then
        assertThat(underTest.buildMatchingTarget(io.write(input.resolve("unsupported extension.mp3"), ""), output)).isNull();
        assertThat(underTest.buildMatchingTarget(io.write(input.resolve("exif-error.jpg"), ""), output)).isNull();
        assertThat(underTest.buildMatchingTarget(io.write(input.resolve("unsupported-extension-20110219-0103.txt"), ""), output)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveDifferentContentForMissingFile(String fsType, Path input, Path output) throws IOException {
        // given
        Path file1 = io.write(input.resolve("test-file.jpg"), "test");
        Path file2 = input.resolve("other-file.jpg");

        // then
        assertThat(underTest.haveSameContent(file1, file2)).isFalse();
        assertThat(underTest.haveSameContent(file2, file1)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveSameContent(String fsType, Path input, Path output) throws IOException {
        // given
        Path file1 = io.write(input.resolve("test-file.jpg"), "test");
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), "test");

        // then
        assertThat(underTest.haveSameContent(file1, file2)).isTrue();
        assertThat(underTest.haveSameContent(file2, file1)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveDifferentContent(String fsType, Path input, Path output) throws IOException {
        // given
        Path file1 = io.write(input.resolve("test-file.jpg"), "test");
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), "test ");

        // then
        assertThat(underTest.haveSameContent(file1, file2)).isFalse();
        assertThat(underTest.haveSameContent(file2, file1)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveDifferentContentSameSize(String fsType, Path input, Path output) throws IOException {
        // given
        Path file1 = io.write(input.resolve("test-file.jpg"), "test1");
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), "test2");

        // then
        assertThat(underTest.haveSameContent(file1, file2)).isFalse();
        assertThat(underTest.haveSameContent(file2, file1)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveDifferentContentPermutation(String fsType, Path input, Path output) throws IOException {
        // given
        Path file1 = io.write(input.resolve("test-file.jpg"), "test");
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), "tset");

        // then
        assertThat(underTest.haveSameContent(file1, file2)).isFalse();
        assertThat(underTest.haveSameContent(file2, file1)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveSameContentLargeFiles(String fsType, Path input, Path output) throws IOException {
        // given
        Path file1 = input.resolve("test-file.jpg");
        Path file2 = input.resolve("2nd").resolve("test-file-2.jpg");
        io.copy(Paths.get("pom.xml"), file1);
        io.copy(Paths.get("pom.xml"), file2);

        // then
        assertThat(underTest.haveSameContent(file1, file2)).isTrue();
        assertThat(underTest.haveSameContent(file2, file1)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveDifferentContentLargeFiles(String fsType, Path input, Path output) throws IOException {
        // given
        String content = Files.readString(Paths.get("pom.xml"));
        Path file1 = io.write(input.resolve("test-file.jpg"), content + "01");
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), content + "10");

        // then
        assertThat(underTest.haveSameContent(file1, file2)).isFalse();
        assertThat(underTest.haveSameContent(file2, file1)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveDifferentContentLargerFiles(String fsType, Path input, Path output) throws IOException {
        // given
        String content = Files.readString(Paths.get("pom.xml"));
        Path file1 = io.write(input.resolve("test-file.jpg"), "01" + content + content + content);
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), "10" + content + content + content);

        // then
        assertThat(underTest.haveSameContent(file1, file2)).isFalse();
        assertThat(underTest.haveSameContent(file2, file1)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveSameContentLargerFiles(String fsType, Path input, Path output) throws IOException {
        // given
        String content = Files.readString(Paths.get("pom.xml"));
        Path file1 = io.write(input.resolve("test-file.jpg"), content + content + content);
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), content + content + content);

        // then
        assertThat(underTest.haveSameContent(file1, file2)).isTrue();
        assertThat(underTest.haveSameContent(file2, file1)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldCopy(String fsType, Path input, Path output) throws IOException {
        // given
        Path file = io.write(input.resolve("test-file.jpg"), "test");

        // when
        underTest.setCopy(true);
        underTest.copy(file, input.resolve("new-file"));

        // then
        assertThat(input.resolve("test-file.jpg")).hasContent("test");
        assertThat(input.resolve("new-file")).hasContent("test");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldMove(String fsType, Path input, Path output) throws IOException {
        // given
        Path file = io.write(input.resolve("test-file.jpg"), "test");

        // when
        underTest.setMove(true);
        underTest.move(file, input.resolve("new-file"));

        // then
        assertThat(input.resolve("test-file.jpg")).doesNotExist();
        assertThat(input.resolve("new-file")).hasContent("test");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldDelete(String fsType, Path input, Path output) throws IOException {
        // given
        Path file = io.write(input.resolve("test-file.jpg"), "test");

        // when
        underTest.setDelete(true);
        underTest.delete(file);

        // then
        assertThat(input.resolve("test-file.jpg")).doesNotExist();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldMoveAsNew(String fsType, Path input, Path output) throws IOException {
        // given
        Path file = io.write(input.resolve("test-file.jpg"), "test");

        // when
        underTest.setMove(true);

        // then
        assertThat(underTest.moveAsNew(file, input.resolve("test.txt"))).isEqualTo(input.resolve("test.txt"));
        io.write(input.resolve("test-file.jpg"), "test");
        assertThat(underTest.moveAsNew(file, file)).isEqualTo(input.resolve("test-file-0.jpg"));
        io.write(input.resolve("test-file.jpg"), "test");
        assertThat(underTest.moveAsNew(file, file)).isEqualTo(input.resolve("test-file-1.jpg"));

        assertThat(input.resolve("test-file.jpg")).doesNotExist();
        assertThat(input.resolve("test-file-0.jpg")).hasContent("test");
        assertThat(input.resolve("test-file-1.jpg")).hasContent("test");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldCopyAsNew(String fsType, Path input, Path output) throws IOException {
        // given
        Path file = io.write(input.resolve("test-file.jpg"), "test");

        // when
        underTest.setCopy(true);

        // then
        assertThat(underTest.copyAsNew(file, input.resolve("test.txt"))).isEqualTo(input.resolve("test.txt"));
        io.write(input.resolve("test-file.jpg"), "test");
        assertThat(underTest.copyAsNew(file, file)).isEqualTo(input.resolve("test-file-0.jpg"));
        io.write(input.resolve("test-file.jpg"), "test");
        assertThat(underTest.copyAsNew(file, file)).isEqualTo(input.resolve("test-file-1.jpg"));

        assertThat(input.resolve("test-file.jpg")).hasContent("test");
        assertThat(input.resolve("test-file-0.jpg")).hasContent("test");
        assertThat(input.resolve("test-file-1.jpg")).hasContent("test");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldDetectSameFile(String fsType, Path input, Path output) throws IOException {
        // given
        Path file1 = io.write(input.resolve("file1"), "test");
        Path file2 = io.write(input.resolve("file2"), "test");
        Path file1Resolved = input.resolve("file1");
        Path file2Resolved = input.resolve("file2");

        // then
        assertThat(underTest.isSameFile(file1, file1)).isTrue();
        assertThat(underTest.isSameFile(file1Resolved, file1Resolved)).isTrue();
        assertThat(underTest.isSameFile(file1, file1Resolved)).isTrue();
        assertThat(underTest.isSameFile(file1Resolved, file1)).isTrue();
        assertThat(underTest.isSameFile(file1, file2Resolved)).isFalse();
        assertThat(underTest.isSameFile(file1Resolved, file2)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldNotDoAnythingWhenNotEnabledExplicitly(String fsType, Path input, Path output) throws IOException {
        // given
        Path source = io.write(input.resolve("file1"), "test");
        Path target = input.resolve("file2");
        Path newPath = null;

        // when
        underTest.move(source, target);
        // then
        assertThat(Files.exists(source)).isTrue();
        assertThat(Files.exists(target)).isFalse();

        // when
        newPath = underTest.moveAsNew(source, target);
        // then
        assertThat(Files.exists(source)).isTrue();
        assertThat(Files.exists(target)).isFalse();
        assertThat(Files.exists(newPath)).isFalse();

        // when
        underTest.delete(source);
        // then
        assertThat(Files.exists(source)).isTrue();

        // when
        underTest.copy(source, target);
        // then
        assertThat(Files.exists(source)).isTrue();
        assertThat(Files.exists(target)).isFalse();

        // when
        newPath = underTest.copyAsNew(source, target);
        // then
        assertThat(Files.exists(source)).isTrue();
        assertThat(Files.exists(target)).isFalse();
        assertThat(Files.exists(newPath)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldBuildCopyTarget(String fsType, Path input, Path output) throws IOException {
        // given
        Path file = io.write(input.resolve("parent").resolve("test-file.jpg"), "test");

        // when
        Path actual = underTest.buildCopyTarget(file, input, output);

        // then
        assertThat(actual).doesNotExist();
        assertThat(actual).isEqualTo(output.resolve("parent").resolve("test-file.jpg"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveStatistics(String fsType, Path input, Path output) throws IOException {
        // given
        Path file = io.write(input.resolve("parent").resolve("test-file.jpg"), "testX");

        // when
        Statistics base0 = underTest.getStatistics();
        Statistics base1 = underTest.resetStatistics("0");
        Statistics base2 = underTest.getStatistics();
        assertThat(base2.hasData()).isFalse();
        underTest.reportOkLocation(file);
        underTest.reportOkLocation(file);
        underTest.reportNoMatch(file);
        Statistics first1 = underTest.getStatistics();
        Statistics first2 = underTest.resetStatistics("1");
        underTest.reportOkLocation(file);
        Statistics second1 = underTest.getStatistics();
        Statistics second2 = underTest.resetStatistics("2");

        // then
        assertThat(base0.hasData()).isFalse();
        assertThat(base0.getId()).isEqualTo("statistics");
        assertThat(base1).isSameAs(base0);
        assertThat(base2.hasData()).isTrue();
        assertThat(base2.getId()).isEqualTo("0");
        assertThat(base2).isSameAs(first1);

        assertThat(first1.hasData()).isTrue();
        assertThat(first1.getId()).isEqualTo("0");
        assertThat(first1.getData()).hasSize(2);
        assertThat(first1.getData().get("ok location").getCount()).isEqualTo(2);
        assertThat(first1.getData().get("ok location").getBytes()).isEqualTo(10);
        assertThat(first1.getData().get("no match").getCount()).isEqualTo(1);
        assertThat(first1.getData().get("no match").getBytes()).isEqualTo(5);
        assertThat(first2).isSameAs(first1);

        assertThat(second1.hasData()).isTrue();
        assertThat(second1.getId()).isEqualTo("1");
        assertThat(second1.getData()).hasSize(1);
        assertThat(second1.getData().get("ok location").getCount()).isEqualTo(1);
        assertThat(second1.getData().get("ok location").getBytes()).isEqualTo(5);
        assertThat(second2).isSameAs(second1);
    }

    @Test
    void shouldPrintBytesInHex() {
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++) {
            byte b = (byte) i;
            System.out.printf("%d - '%s'\n", b, Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldDeleteAllEmptyDirectories(String fsType, Path input, Path output) throws IOException {
        // given
        Files.createDirectories(input.resolve("test").resolve("second").resolve("other"));
        underTest.setDelete(true);

        // when
        underTest.deleteEmpty(input, input.resolve("test").resolve("second").resolve("other"));

        // then
        assertThat(input.resolve("test").resolve("second").resolve("other")).doesNotExist();
        assertThat(input.resolve("test").resolve("second")).doesNotExist();
        assertThat(input.resolve("test")).doesNotExist();
        assertThat(input).exists();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldDeleteSomeEmptyDirectories(String fsType, Path input, Path output) throws IOException {
        // given
        Files.createDirectories(input.resolve("test").resolve("second").resolve("other"));
        io.write(input.resolve("test").resolve("test-file.jpg"), "testX");
        underTest.setDelete(true);

        // when
        underTest.deleteEmpty(input, input.resolve("test").resolve("second").resolve("other"));

        // then
        assertThat(input.resolve("test").resolve("second").resolve("other")).doesNotExist();
        assertThat(input.resolve("test").resolve("second")).doesNotExist();
        assertThat(input.resolve("test")).exists();
        assertThat(input).exists();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldNotDeleteNonEmptyDirectories(String fsType, Path input, Path output) throws IOException {
        // given
        Files.createDirectories(input.resolve("test").resolve("second").resolve("other"));
        underTest.setDelete(true);

        // when
        underTest.deleteEmpty(input, input.resolve("test").resolve("second"));

        // then
        assertThat(input.resolve("test").resolve("second").resolve("other")).exists();
        assertThat(input.resolve("test").resolve("second")).exists();
        assertThat(input.resolve("test")).exists();
        assertThat(input).exists();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldNotDeleteRoot(String fsType, Path input, Path output) throws IOException {
        // given
        underTest.setDelete(true);

        // when
        underTest.deleteEmpty(input, input.resolve("other").getParent().toAbsolutePath());

        // then
        assertThat(input).exists();
    }
}