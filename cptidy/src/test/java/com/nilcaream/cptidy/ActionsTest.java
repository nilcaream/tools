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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionsTest {

    @InjectMocks
    private Actions underTest = new Actions();

    @Mock
    private IoService ioService;

    @Spy
    private Logger logger = new Logger();

    @Mock
    private Marker marker;

    private Io io = new Io();
    private Path root = Jimfs.newFileSystem(Configuration.unix()).getPath("unix").toAbsolutePath();

    @Test
    void shouldOrganize() throws IOException {
        // given
        Path source = root.resolve("source");
        Path target = root.resolve("target");

        Files.createDirectories(source);

        Path fail = io.write(source.resolve("a"), "fail");

        Path matchToMove = io.write(source.resolve("match"), "test");
        Path matchToMoveResolved = target.resolve("2020-01").resolve("match.jpg");

        Path matchToDelete = io.write(source.resolve("delete"), "test");
        Path matchToDeleteResolved = target.resolve("2020-01").resolve("delete.jpg");

        Path same = io.write(source.resolve("same"), "test");
        Path sameResolved = target.resolve("2020-01").resolve("same.jpg");

        Path noMatch = io.write(source.resolve("no-match"), "test");

        given(ioService.buildMatchingTarget(fail, target)).willThrow(IOException.class);

        given(ioService.buildMatchingTarget(matchToMove, target)).willReturn(matchToMoveResolved);
        given(ioService.buildMatchingTarget(matchToDelete, target)).willReturn(matchToDeleteResolved);
        given(ioService.buildMatchingTarget(same, target)).willReturn(sameResolved);
        given(ioService.buildMatchingTarget(noMatch, target)).willReturn(null);

        given(ioService.isSameFile(matchToMove, matchToMoveResolved)).willReturn(false);
        given(ioService.haveSameContent(matchToMove, matchToMoveResolved)).willReturn(false);

        given(ioService.isSameFile(matchToDelete, matchToDeleteResolved)).willReturn(false);
        given(ioService.haveSameContent(matchToDelete, matchToDeleteResolved)).willReturn(true);

        given(ioService.isSameFile(same, sameResolved)).willReturn(true);

        // when
        underTest.organize("org", source, target);

        // then
        verify(ioService, times(1)).move(matchToMove, matchToMoveResolved);
        verify(ioService, times(1)).delete(matchToDelete);
        verifyNoMoreInteractions(ioService);
    }

//    @Test
//    void shouldReorganize() throws IOException {
//        // given
//        Path target = root.resolve("target");
//
//        Path fail = io.write(source.resolve("a"), "fail");
//
//        Path matchToMove = io.write(source.resolve("match"), "test");
//        Path matchToMoveResolved = target.resolve("2020-01").resolve("match.jpg");
//
//        Path matchToDelete = io.write(source.resolve("delete"), "test");
//        Path matchToDeleteResolved = target.resolve("2020-01").resolve("delete.jpg");
//
//        Path same = io.write(source.resolve("same"), "test");
//        Path sameResolved = target.resolve("2020-01").resolve("same.jpg");
//
//        Path noMatch = io.write(source.resolve("no-match"), "test");
//
//        given(ioService.buildMatchingTarget(fail, target)).willThrow(IOException.class);
//
//        given(ioService.buildMatchingTarget(matchToMove, target)).willReturn(matchToMoveResolved);
//        given(ioService.buildMatchingTarget(matchToDelete, target)).willReturn(matchToDeleteResolved);
//        given(ioService.buildMatchingTarget(same, target)).willReturn(sameResolved);
//        given(ioService.buildMatchingTarget(noMatch, target)).willReturn(null);
//
//        given(ioService.isSameFile(matchToMove, matchToMoveResolved)).willReturn(false);
//        given(ioService.haveSameContent(matchToMove, matchToMoveResolved)).willReturn(false);
//
//        given(ioService.isSameFile(matchToDelete, matchToDeleteResolved)).willReturn(false);
//        given(ioService.haveSameContent(matchToDelete, matchToDeleteResolved)).willReturn(true);
//
//        given(ioService.isSameFile(same, sameResolved)).willReturn(true);
//
//        // when
//        underTest.organize("org", source, target);
//
//        // then
//        verify(ioService, times(1)).move(matchToMove, matchToMoveResolved);
//        verify(ioService, times(1)).delete(matchToDelete);
//        verifyNoMoreInteractions(ioService);
//    }

    @Test
    void shouldRemoveDuplicates() throws IOException {
        // given
        Path root = this.root.resolve("root");
        Path single = io.write(root.resolve("dir").resolve("not"), "test");
        Path tooSmall1 = io.write(root.resolve("dir").resolve("small1"), "test");
        Path tooSmall2 = io.write(root.resolve("dir").resolve("small2"), "test");
        Path duplicate1 = io.write(root.resolve("dir").resolve("duplicate1"), "test");
        Path duplicate2 = io.write(root.resolve("dir").resolve("duplicate2"), "test");
        Path notDuplicated = io.write(root.resolve("dir").resolve("not-duplicated"), "test");
        Path inOtherDirectory = io.write(root.resolve("dir-other").resolve("other"), "test");

        given(ioService.size(single)).willReturn(1026L);
        given(ioService.size(inOtherDirectory)).willReturn(1025L);

        given(ioService.size(tooSmall1)).willReturn(1024L);
        given(ioService.size(tooSmall2)).willReturn(1024L);

        given(ioService.size(duplicate1)).willReturn(1025L);
        given(ioService.size(duplicate2)).willReturn(1025L);
        given(ioService.size(notDuplicated)).willReturn(1025L);

        given(ioService.isSameFile(duplicate1, notDuplicated)).willReturn(false);
        given(ioService.isSameFile(duplicate2, notDuplicated)).willReturn(false);

        given(ioService.haveSameContent(duplicate1, notDuplicated)).willReturn(false);
        given(ioService.haveSameContent(duplicate2, notDuplicated)).willReturn(false);

        given(ioService.isSameFile(duplicate1, duplicate2)).willReturn(false);

        given(ioService.haveSameContent(duplicate1, duplicate2)).willReturn(true);

        // when
        underTest.removeDuplicatesPerDirectory("id", root);

        // then
        verify(ioService, times(1)).deleteOne(duplicate1, duplicate2);
        verifyNoMoreInteractions(ioService);
    }
}