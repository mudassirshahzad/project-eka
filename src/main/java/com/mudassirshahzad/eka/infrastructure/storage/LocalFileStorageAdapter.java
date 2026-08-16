package com.mudassirshahzad.eka.infrastructure.storage;

import com.mudassirshahzad.eka.domain.document.FileStorage;
import com.mudassirshahzad.eka.infrastructure.config.AppProperties;
import com.mudassirshahzad.eka.infrastructure.storage.exception.FileStorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class LocalFileStorageAdapter implements FileStorage {

    private final AppProperties appProperties;

    @Override
    public String store(String relativePath, byte[] content) {
        Path target = resolveAndCreateDirectories(relativePath);
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new FileStorageException("Failed to write file: " + relativePath, e);
        }
        return relativePath;
    }

    @Override
    public byte[] load(String relativePath) {
        Path target = documentRoot().resolve(relativePath);
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new FileStorageException("Failed to read file: " + relativePath, e);
        }
    }

    @Override
    public void delete(String relativePath) {
        Path target = documentRoot().resolve(relativePath);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new FileStorageException("Failed to delete file: " + relativePath, e);
        }
    }

    private Path documentRoot() {
        return Path.of(appProperties.storage().documentRoot());
    }

    private Path resolveAndCreateDirectories(String relativePath) {
        Path target = documentRoot().resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new FileStorageException("Failed to create directories for: " + relativePath, e);
        }
        return target;
    }
}
