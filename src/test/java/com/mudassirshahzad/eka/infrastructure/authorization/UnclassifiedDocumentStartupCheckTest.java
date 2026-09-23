package com.mudassirshahzad.eka.infrastructure.authorization;

import com.mudassirshahzad.eka.domain.document.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnclassifiedDocumentStartupCheckTest {

    @Mock private DocumentRepository documentRepository;

    @Test
    void run_noUnclassifiedDocuments_countsButDoesNotThrow() throws Exception {
        when(documentRepository.countUnclassified()).thenReturn(0L);

        new UnclassifiedDocumentStartupCheck(documentRepository).run(null);

        verify(documentRepository).countUnclassified();
    }

    @Test
    void run_unclassifiedDocumentsExist_stillCompletesWithoutThrowing() throws Exception {
        when(documentRepository.countUnclassified()).thenReturn(3L);

        new UnclassifiedDocumentStartupCheck(documentRepository).run(null);

        verify(documentRepository).countUnclassified();
    }
}
