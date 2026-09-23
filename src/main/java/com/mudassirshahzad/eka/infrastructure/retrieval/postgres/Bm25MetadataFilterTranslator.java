package com.mudassirshahzad.eka.infrastructure.retrieval.postgres;

import com.mudassirshahzad.eka.domain.query.MetadataFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates a {@link MetadataFilter} into a SQL WHERE fragment and its corresponding
 * named parameters for use with {@code NamedParameterJdbcTemplate}.
 *
 * <p>Supported filter keys:
 * <ul>
 *   <li>{@code department} (String) — matched against {@code documents.department}</li>
 *   <li>{@code classification} (String) — matched against {@code documents.classification}</li>
 *   <li>{@code chunkingStrategy} (String) — matched against {@code chunks.chunking_strategy}</li>
 *   <li>{@code tags} (List&lt;String&gt;) — all listed tags must appear in {@code documents.tags};
 *       each tag generates an independent {@code = ANY(d.tags)} predicate</li>
 * </ul>
 *
 * <p>Unknown keys produce a warning and are silently skipped — they do not cause
 * query failure. This keeps the BM25 adapter forward-compatible as the filter
 * vocabulary grows.
 *
 * <p><b>P06.2 note:</b> classification-clearance enforcement (the Authorization Filter) does
 * <em>not</em> live here, deliberately. {@link MetadataFilter} is shared verbatim across every
 * {@code RetrievalPort} adapter by {@code HybridRetrievalAdapter} — a criterion this translator
 * understood but the Weaviate-side translator interpreted as a generic equality match (Weaviate
 * indexes no per-chunk classification property) would silently return zero vector-search results
 * the moment it was populated. Enforcement instead happens once, engine-agnostically, in
 * {@code RetrievalService} after both engines' results are back — see that class.
 */
@Slf4j
@Component
class Bm25MetadataFilterTranslator {

    record FilterClause(String sql, Map<String, Object> params) {}

    FilterClause translate(MetadataFilter filter) {
        if (filter.isEmpty()) {
            return new FilterClause("", Map.of());
        }

        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        for (Map.Entry<String, Object> entry : filter.criteria().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            switch (key) {
                case "department" -> {
                    sql.append(" AND d.department = :fDept");
                    params.put("fDept", value);
                }
                case "classification" -> {
                    sql.append(" AND d.classification = :fClass");
                    params.put("fClass", value);
                }
                case "chunkingStrategy" -> {
                    sql.append(" AND c.chunking_strategy = :fChunkingStrategy");
                    params.put("fChunkingStrategy", value);
                }
                case "tags" -> {
                    if (value instanceof List<?> tags && !tags.isEmpty()) {
                        for (int i = 0; i < tags.size(); i++) {
                            String paramName = "fTag" + i;
                            sql.append(" AND :").append(paramName).append(" = ANY(d.tags)");
                            params.put(paramName, tags.get(i));
                        }
                    }
                }
                default -> log.warn(
                        "BM25 adapter: MetadataFilter key '{}' is not supported and will be ignored", key);
            }
        }

        return new FilterClause(sql.toString(), params);
    }
}
