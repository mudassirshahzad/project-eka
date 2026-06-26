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
        List<Filter.Expression> expressions = new ArrayList<>();

        for (Map.Entry<String, Object> entry : filter.criteria().entrySet()) {
            String key   = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof List<?> list && !list.isEmpty()) {
                expressions.add(b.in(key, list.toArray()));
            } else if (value != null) {
                expressions.add(b.eq(key, value));
            }
        }

        if (expressions.isEmpty()) {
            return null;
        }
        if (expressions.size() == 1) {
            return expressions.getFirst();
        }

        Filter.Expression combined = expressions.get(0);
        for (int i = 1; i < expressions.size(); i++) {
            combined = b.and(combined, expressions.get(i));
        }
        return combined;
    }
}
