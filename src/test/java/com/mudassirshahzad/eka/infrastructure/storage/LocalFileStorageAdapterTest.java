package com.mudassirshahzad.eka.infrastructure.storage;

import com.mudassirshahzad.eka.infrastructure.config.AppProperties;
import com.mudassirshahzad.eka.infrastructure.storage.exception.FileStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalFileStorageAdapterTest {

    @TempDir
    Path tempDir;

    @Mock
    AppProperties appProperties;

    @Mock
    AppProperties.Storage storage;

    private LocalFileStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        when(appProperties.storage()).thenReturn(storage);
        when(storage.documentRoot()).thenReturn(tempDir.toString());
        adapter = new LocalFileStorageAdapter(appProperties);
    }

    @Test
    void store_writesContentToDiskAndReturnsRelativePath() throws IOException {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);

        String returned = adapter.store("tenant1/doc1/raw/file.txt", content);

        assertThat(returned).isEqualTo("tenant1/doc1/raw/file.txt");
        assertThat(Files.readAllBytes(tempDir.resolve("tenant1/doc1/raw/file.txt"))).isEqualTo(content);
    }

    @Test
    void store_createsAllIntermediateDirectories() {
        byte[] content = "deep".getBytes(StandardCharsets.UTF_8);
        assertThatCode(() -> adapter.store("a/b/c/d/file.txt", content)).doesNotThrowAnyException();
        assertThat(tempDir.resolve("a/b/c/d/file.txt")).exists();
    }

    @Test
    void load_returnsExactBytesStoredEarlier() {
        byte[] content = "retrieve me".getBytes(StandardCharsets.UTF_8);
        adapter.store("t/d/file.txt", content);

        assertThat(adapter.load("t/d/file.txt")).isEqualTo(content);
    }

    @Test
    void load_throwsFileStorageExceptionWhenFileAbsent() {
        assertThatExceptionOfType(FileStorageException.class)
                .isThrownBy(() -> adapter.load("missing/no-such-file.txt"));
    }

    @Test
    void delete_removesStoredFile() {
        byte[] content = "bye".getBytes(StandardCharsets.UTF_8);
        adapter.store("t/d/bye.txt", content);

        adapter.delete("t/d/bye.txt");

        assertThat(tempDir.resolve("t/d/bye.txt")).doesNotExist();
    }

    @Test
    void delete_isIdempotentWhenFileDoesNotExist() {
        assertThatCode(() -> adapter.delete("nonexistent/file.txt")).doesNotThrowAnyException();
    }
}
