package com.mudassir.eka.domain.document;

public interface FileStorage {

    String store(String relativePath, byte[] content);

    byte[] load(String relativePath);

    void delete(String relativePath);
}
