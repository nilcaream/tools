package com.nilcaream.cptidy;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
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
        assertThat(underTest.hasSameContent(file1, file2)).isFalse();
        assertThat(underTest.hasSameContent(file2, file1)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveSameContent(String fsType, Path input, Path output) throws IOException {
        // given
        Path file1 = io.write(input.resolve("test-file.jpg"), "test");
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), "test");

        // then
        assertThat(underTest.hasSameContent(file1, file2)).isTrue();
        assertThat(underTest.hasSameContent(file2, file1)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveDifferentContent(String fsType, Path input, Path output) throws IOException {
        // given
        Path file1 = io.write(input.resolve("test-file.jpg"), "test");
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), "test ");

        // then
        assertThat(underTest.hasSameContent(file1, file2)).isFalse();
        assertThat(underTest.hasSameContent(file2, file1)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveDifferentContentSameSize(String fsType, Path input, Path output) throws IOException {
        // given
        Path file1 = io.write(input.resolve("test-file.jpg"), "test1");
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), "test2");

        // then
        assertThat(underTest.hasSameContent(file1, file2)).isFalse();
        assertThat(underTest.hasSameContent(file2, file1)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveDifferentContentPermutation(String fsType, Path input, Path output) throws IOException {
        // given
        Path file1 = io.write(input.resolve("test-file.jpg"), "test");
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), "tset");

        // then
        assertThat(underTest.hasSameContent(file1, file2)).isFalse();
        assertThat(underTest.hasSameContent(file2, file1)).isFalse();
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
        assertThat(underTest.hasSameContent(file1, file2)).isTrue();
        assertThat(underTest.hasSameContent(file2, file1)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideFileSystem")
    void shouldHaveDifferentContentLargeFiles(String fsType, Path input, Path output) throws IOException {
        // given
        String content = Files.readString(Paths.get("pom.xml"));
        Path file1 = io.write(input.resolve("test-file.jpg"), content + "01");
        Path file2 = io.write(input.resolve("2nd").resolve("test-file.jpg"), content + "10");

        // then
        assertThat(underTest.hasSameContent(file1, file2)).isFalse();
        assertThat(underTest.hasSameContent(file2, file1)).isFalse();
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

        // then
        underTest.move(source, target);
        assertThat(Files.exists(source)).isTrue();
        assertThat(Files.exists(target)).isFalse();
        underTest.moveAsNew(source, target);
        assertThat(Files.exists(source)).isTrue();
        assertThat(Files.exists(target)).isFalse();
        underTest.delete(source);
        assertThat(Files.exists(source)).isTrue();
    }
}