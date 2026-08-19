package com.mudassirshahzad.eka.api.controller;

import com.mudassirshahzad.eka.api.config.SecurityConfig;
import com.mudassirshahzad.eka.api.config.WebMvcConfig;
import com.mudassirshahzad.eka.api.observability.CorrelationIdFilter;
import com.mudassirshahzad.eka.api.security.AuthorizationInterceptor;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationFilter;
import com.mudassirshahzad.eka.api.security.JwtAuthenticationToken;
import com.mudassirshahzad.eka.api.security.JwtTokenProvider;
import com.mudassirshahzad.eka.api.security.RequestSizeLimitFilter;
import com.mudassirshahzad.eka.api.security.RestAuthenticationEntryPoint;
import com.mudassirshahzad.eka.application.document.DeleteDocumentUseCase;
import com.mudassirshahzad.eka.application.document.GetDocumentUseCase;
import com.mudassirshahzad.eka.application.document.ListDocumentsUseCase;
import com.mudassirshahzad.eka.application.document.UploadDocumentUseCase;
import com.mudassirshahzad.eka.application.shared.ResourceNotFoundException;
import com.mudassirshahzad.eka.domain.document.Document;
import com.mudassirshahzad.eka.domain.document.DocumentMetadata;
import com.mudassirshahzad.eka.domain.document.SupportedFormat;
import com.mudassirshahzad.eka.domain.shared.PageResult;
import com.mudassirshahzad.eka.domain.shared.TenantId;
import com.mudassirshahzad.eka.domain.user.UserId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} slice for {@link DocumentController} (P06.1) — same recipe as
 * {@code ConversationControllerTest}: real {@link SecurityConfig}/{@link WebMvcConfig}/
 * {@link AuthorizationInterceptor} chain, mocked use cases, a real {@link SimpleMeterRegistry}.
 */
@WebMvcTest(DocumentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        WebMvcConfig.class, AuthorizationInterceptor.class, CorrelationIdFilter.class,
        RequestSizeLimitFilter.class, DocumentControllerTest.MeterRegistryTestConfig.class})
class DocumentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UploadDocumentUseCase uploadDocumentUseCase;
    @MockitoBean private GetDocumentUseCase    getDocumentUseCase;
    @MockitoBean private ListDocumentsUseCase  listDocumentsUseCase;
    @MockitoBean private DeleteDocumentUseCase deleteDocumentUseCase;
    @MockitoBean private JwtTokenProvider      jwtTokenProvider;

    @TestConfiguration
    static class MeterRegistryTestConfig {
        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private final UUID userId   = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    private RequestPostProcessor authenticated() {
        return authenticatedAs("ROLE_USER");
    }

    private RequestPostProcessor authenticatedAs(String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        return authentication(new JwtAuthenticationToken(UserId.of(userId), TenantId.of(tenantId), granted));
    }

    private Document sampleDocument() {
        return Document.create(TenantId.of(tenantId), UserId.of(userId), "report.pdf",
                SupportedFormat.PDF, DocumentMetadata.builder().title("Report").build());
    }

    // ── POST /documents ──────────────────────────────────────────────────────

    @Test
    void uploadDocument_returnsCreatedWithLocationAndBody() throws Exception {
        Document document = sampleDocument();
        when(uploadDocumentUseCase.execute(any())).thenReturn(document);
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents").file(file).with(authenticated()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/documents/" + document.getId().value()))
                .andExpect(jsonPath("$.filename").value("report.pdf"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void uploadDocument_emptyFile_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/v1/documents").file(file).with(authenticated()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadDocument_unsupportedFormat_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "archive.zip", "application/zip", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents").file(file).with(authenticated()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Unsupported")));
    }

    @Test
    void uploadDocument_unauthenticated_returnsUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadDocument_viewerRole_returnsForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents").file(file).with(authenticatedAs("ROLE_VIEWER")))
                .andExpect(status().isForbidden());
    }

    // ── GET /documents/{id} ──────────────────────────────────────────────────

    @Test
    void getDocument_returnsDocumentDetail() throws Exception {
        Document document = sampleDocument();
        when(getDocumentUseCase.execute(any(), any())).thenReturn(document);

        mockMvc.perform(get("/api/v1/documents/{id}", document.getId().value()).with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("report.pdf"))
                .andExpect(jsonPath("$.title").value("Report"));
    }

    @Test
    void getDocument_notFound_returnsProblemDetail() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(getDocumentUseCase.execute(any(), any()))
                .thenThrow(new ResourceNotFoundException("Document", missingId.toString()));

        mockMvc.perform(get("/api/v1/documents/{id}", missingId).with(authenticated()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDocument_viewerRole_isPermitted() throws Exception {
        when(getDocumentUseCase.execute(any(), any())).thenReturn(sampleDocument());

        mockMvc.perform(get("/api/v1/documents/{id}", UUID.randomUUID()).with(authenticatedAs("ROLE_VIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    void getDocument_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/documents/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /documents ───────────────────────────────────────────────────────

    @Test
    void listDocuments_returnsPage() throws Exception {
        PageResult<Document> page = PageResult.of(List.of(sampleDocument()), 0, 20, 1);
        when(listDocumentsUseCase.execute(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/documents").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].filename").value("report.pdf"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── DELETE /documents/{id} ───────────────────────────────────────────────

    @Test
    void deleteDocument_returnsNoContent() throws Exception {
        doNothing().when(deleteDocumentUseCase).execute(any());

        mockMvc.perform(delete("/api/v1/documents/{id}", UUID.randomUUID()).with(authenticated()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteDocument_viewerRole_returnsForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/documents/{id}", UUID.randomUUID()).with(authenticatedAs("ROLE_VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteDocument_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/documents/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
