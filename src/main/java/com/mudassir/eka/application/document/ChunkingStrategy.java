package com.mudassir.eka.application.document;

import java.util.List;

public interface ChunkingStrategy {

    String name();

    List<TextSegment> chunk(String text);
}
