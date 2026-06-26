package com.mudassir.eka.infrastructure.vectorstore.weaviate;

import com.mudassir.eka.domain.query.MetadataFilter;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
class MetadataFilterTranslator {

    @Nullable
    Filter.Expression translate(MetadataFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }

        var b = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> ops = new ArrayList<>();

        for (Map.Entry<String, Object> entry : filter.criteria().entrySet()) {
            String key   = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof List<?> list && !list.isEmpty()) {
                ops.add(b.in(key, list.toArray()));
            } else if (value != null) {
                ops.add(b.eq(key, value));
            }
        }

        if (ops.isEmpty()) {
            return null;
        }
        if (ops.size() == 1) {
            return ops.getFirst().build();
        }

        FilterExpressionBuilder.Op combined = ops.get(0);
        for (int i = 1; i < ops.size(); i++) {
            combined = b.and(combined, ops.get(i));
        }
        return combined.build();
    }
}
