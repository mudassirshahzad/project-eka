package com.mudassirshahzad.eka.infrastructure.authorization;

import com.mudassirshahzad.eka.domain.document.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Operational visibility for the P06.2 classification backfill (V018 migration): logs a
 * {@code WARN} with a count — never document content or identifiers — if any document is still
 * unclassified at startup. A count above zero after the backfill has run indicates either the
 * migration hasn't executed in this environment yet, or a document was written around it
 * (a direct DB write bypassing {@code UploadDocumentUseCase}'s now-mandatory classification).
 *
 * <p>Deliberately a startup {@code WARN}, not a fail-fast abort (contrast {@code JwtProperties},
 * v0.6.1 ADR EX04, which does fail fast) — a data-integrity gap like this shouldn't prevent the
 * whole application from starting the way a broken security config should. Every unclassified
 * document is already denied to every caller by {@link com.mudassirshahzad.eka.domain.document.DocumentClassification#UNKNOWN_LEVEL}'s
 * fail-closed handling regardless of whether this check runs at all — this is visibility, not the
 * enforcement mechanism itself.
 */
@Slf4j
@Component
public class UnclassifiedDocumentStartupCheck implements ApplicationRunner {

    private final DocumentRepository documentRepository;

    public UnclassifiedDocumentStartupCheck(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        long unclassifiedCount = documentRepository.countUnclassified();
        if (unclassifiedCount > 0) {
            log.warn("{} document(s) have no classification set — they are denied to every caller "
                    + "(fail-closed) until reclassified. Expected to be zero after the V018 backfill "
                    + "migration has run.", unclassifiedCount);
        }
    }
}
