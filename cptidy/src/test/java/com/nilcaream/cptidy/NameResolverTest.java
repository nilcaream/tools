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
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NameResolverTest {

    @InjectMocks
    private NameResolver underTest = new NameResolver();

    @Spy
    private Logger logger = new Logger();

    @Mock
    private ExifService exifService;

    private Io io = new Io();
    private Path root = Jimfs.newFileSystem(Configuration.unix()).getPath("unix").toAbsolutePath();

    @Test
    void shouldResolveByExplicitDate() throws IOException {
        // given
        underTest.addDate(".+any", "2010-11-21");
        Path knownAny = io.write(root.resolve("known.any"), "test");
        Path knownAnyWithDate = io.write(root.resolve("--kNOWN20120901-..any"), "test");
        Path other = io.write(root.resolve("--kNOWN20120901.anynot"), "test");

        // then
        assertThat(underTest.resolve(knownAny)).isEqualTo(new NameResolver.Result("2010-11", "20101121-known.any"));
        assertThat(underTest.resolve(knownAnyWithDate)).isEqualTo(new NameResolver.Result("2010-11", "20101121-known.any"));
        assertThat(underTest.resolve(other)).isNull();
    }

    @Test
    void shouldResolveByDateInName() throws IOException {
        // given
        Path matchNameMatchExtension = io.write(root.resolve("Test--20200115.jPG"), "test");
        Path matchNameNotExtension = io.write(root.resolve("Test--20200115.jpg.txt"), "test");

        // then
        assertThat(underTest.resolve(matchNameMatchExtension)).isEqualTo(new NameResolver.Result("2020-01", "20200115-test.jpg"));
        assertThat(underTest.resolve(matchNameNotExtension)).isNull();
    }

    @Test
    void shouldResolveByExif() throws IOException {
        // given
        Path notNameMatchExtension = io.write(root.resolve("Test--2020-0115.jPG"), "test");
        Path notNameNotExtension = io.write(root.resolve("Test--30200115.jpg.txt"), "test");
        Path noExif = io.write(root.resolve("test.jpg"), "test");
        given(exifService.getDate(notNameMatchExtension)).willReturn(new DateString("2002-12-09"));
        given(exifService.getDate(noExif)).willReturn(null);

        // then
        assertThat(underTest.resolve(notNameMatchExtension)).isEqualTo(new NameResolver.Result("2002-12", "20021209-test-2020-0115.jpg"));
        assertThat(underTest.resolve(notNameNotExtension)).isNull();
        assertThat(underTest.resolve(noExif)).isNull();
    }

    @Test
    void shouldBuildUniquePath() throws IOException {
        // given
        Path existingA = io.write(root.resolve("existing-.txt"), "test");
        Path existingB = io.write(root.resolve("existing.dat"), "test");

        io.write(root.resolve("existing.more"), "test");
        io.write(root.resolve("existing-0.more"), "test");
        io.write(root.resolve("existing-1.more"), "test");

        Path matchingA = io.write(root.resolve("ExisTing--.txt"), "test");
        Path matchingB = io.write(root.resolve("exiSTing.Dat"), "test");
        Path matchingMore = io.write(root.resolve("exiSTing.more"), "test");
        Path notMatching = io.write(root.resolve("ExisTing--5.txt"), "test");

        // then
        assertThat(underTest.buildUniquePath(existingA)).isEqualTo(root.resolve("existing.txt"));
        assertThat(underTest.buildUniquePath(existingB)).isEqualTo(root.resolve("existing-0.dat"));
        assertThat(underTest.buildUniquePath(matchingB)).isEqualTo(root.resolve("existing-0.dat"));
        assertThat(underTest.buildUniquePath(matchingA)).isEqualTo(root.resolve("existing.txt"));
        assertThat(underTest.buildUniquePath(matchingMore)).isEqualTo(root.resolve("existing-2.more"));
        assertThat(underTest.buildUniquePath(notMatching)).isEqualTo(root.resolve("existing-5.txt"));
    }

    @Test
    void shouldNotFailForNoExtension() throws IOException {
        // given
        Path existing = io.write(root.resolve("existing-20200101-test"), "test");
        Path other = root.resolve("other-20200101-test");

        // then
        assertThat(underTest.resolve(existing)).isNull();
        assertThat(underTest.buildUniquePath(existing)).isEqualTo(root.resolve("existing-20200101-test-0"));
        assertThat(underTest.buildUniquePath(other)).isEqualTo(root.resolve("other-20200101-test"));
    }
}