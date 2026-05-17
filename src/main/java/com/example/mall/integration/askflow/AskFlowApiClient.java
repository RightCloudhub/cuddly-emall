package com.example.mall.integration.askflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Thin adapter for the subset of AskFlow's API the mall integrates with. The exact endpoints come
 * from prompt.md and are frozen contract:
 *
 * <ul>
 *   <li>{@code POST /api/v1/embedding/upload} — multipart with {@code file} (markdown) and {@code
 *       metadata} JSON.
 *   <li>{@code DELETE /api/v1/embedding/documents/{doc_id}}.
 *   <li>{@code POST /api/v1/admin/users} — internal endpoint (see "Cross-project changes" in
 *       README).
 *   <li>{@code GET /api/v1/admin/tickets?user_id=...}.
 * </ul>
 */
@Component
public class AskFlowApiClient {

    private final RestClient client;
    private final ObjectMapper mapper;

    public AskFlowApiClient(RestClient askFlowRestClient, ObjectMapper objectMapper) {
        this.client = askFlowRestClient;
        this.mapper = objectMapper;
    }

    /** Upload (upsert) a markdown chunk under {@code docId}. {@code source == docId}. */
    public void uploadDocument(String docId, String title, String markdown) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", docId);
        metadata.put("title", title);
        metadata.put("doc_id", docId);

        String metadataJson;
        try {
            metadataJson = mapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new AskFlowApiException("failed to serialize embedding metadata", e);
        }

        byte[] body = markdown.getBytes(StandardCharsets.UTF_8);
        ByteArrayResource file =
                new ByteArrayResource(body) {
                    @Override
                    public String getFilename() {
                        return safeFilename(docId) + ".md";
                    }
                };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.TEXT_MARKDOWN);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", file);
        form.add("metadata", metadataJson);

        execute("upload " + docId, () ->
                client.post()
                        .uri("/api/v1/embedding/upload")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(form)
                        .retrieve()
                        .toBodilessEntity());
    }

    /** Delete by doc_id (which equals source in our convention). */
    public void deleteDocument(String docId) {
        execute("delete " + docId, () ->
                client.delete()
                        .uri("/api/v1/embedding/documents/{id}", docId)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                            // 404 is benign — already absent. Anything else bubbles up.
                            if (res.getStatusCode().value() != 404) {
                                throw new AskFlowApiException(
                                        "askflow delete failed: " + res.getStatusCode(),
                                        res.getStatusCode().value());
                            }
                        })
                        .toBodilessEntity());
    }

    /**
     * Create (or sync) a mall user into AskFlow. Returns the AskFlow {@code user_id} (UUID).
     * Depends on AskFlow patch — see "Cross-project changes" section of README.
     */
    public UUID createUser(String username, String email, String externalId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("email", email);
        payload.put("password_hash", null);
        payload.put("role", "user");
        payload.put("external_id", externalId);

        JsonNode resp =
                execute(
                        "createUser " + email,
                        () ->
                                client.post()
                                        .uri("/api/v1/admin/users")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(payload)
                                        .retrieve()
                                        .body(JsonNode.class));
        if (resp == null || !resp.hasNonNull("user_id")) {
            throw new AskFlowApiException("askflow createUser missing user_id in response", -1);
        }
        return UUID.fromString(resp.get("user_id").asText());
    }

    /** List tickets for a given AskFlow user. The shape is forwarded to mall admin UI as-is. */
    public List<Map<String, Object>> listTickets(UUID askflowUserId) {
        JsonNode resp =
                execute(
                        "listTickets " + askflowUserId,
                        () ->
                                client.get()
                                        .uri(
                                                builder ->
                                                        builder.path("/api/v1/admin/tickets")
                                                                .queryParam(
                                                                        "user_id",
                                                                        askflowUserId.toString())
                                                                .build())
                                        .retrieve()
                                        .body(JsonNode.class));
        if (resp == null) {
            return List.of();
        }
        JsonNode arr = resp.isArray() ? resp : resp.get("tickets");
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        try {
            return mapper.convertValue(arr, mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (IllegalArgumentException e) {
            throw new AskFlowApiException("failed to parse tickets response", e);
        }
    }

    private static <T> T execute(String op, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpStatusCodeException e) {
            throw new AskFlowApiException(
                    "askflow " + op + " failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(),
                    e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new AskFlowApiException("askflow " + op + " transport error", e);
        }
    }

    private static String safeFilename(String docId) {
        return docId.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
