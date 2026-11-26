package com.vemdancarjp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vemdancarjp.repository.OtherVenueRepository;
import com.vemdancarjp.repository.VariableVenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret",
    "APP_FRONTEND_URL=http://localhost:5173"
})
@AutoConfigureMockMvc
@Testcontainers
class AdminVenueIntegrationTest {

    private static final String APPROVED = "approved-subject";

    enum Resource {
        VARIABLE("/api/admin/variable-venues", "variableVenues",
            Set.of("id", "name", "neighborhood", "instagram", "days", "sortOrder"),
            List.of("name", "neighborhood", "instagram", "days")),
        OTHER("/api/admin/other-venues", "otherVenues",
            Set.of("id", "name", "neighborhood", "instagram", "sortOrder"),
            List.of("name", "neighborhood", "instagram"));

        final String path;
        final String agendaKey;
        final Set<String> responseFields;
        final List<String> textFields;

        Resource(String path, String agendaKey, Set<String> responseFields, List<String> textFields) {
            this.path = path;
            this.agendaKey = agendaKey;
            this.responseFields = responseFields;
            this.textFields = textFields;
        }

        String item(Object id) {
            return path + "/" + id;
        }

        String body(String name, int sortOrder) {
            return this == VARIABLE
                ? "{\"name\":\"" + name + "\",\"neighborhood\":\"Centro\",\"days\":\"Quinta a domingo\",\"sortOrder\":" + sortOrder + "}"
                : "{\"name\":\"" + name + "\",\"neighborhood\":\"Centro\",\"sortOrder\":" + sortOrder + "}";
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @SpyBean VariableVenueRepository variableVenueRepository;
    @SpyBean OtherVenueRepository otherVenueRepository;

    @BeforeEach
    void reset() {
        variableVenueRepository.deleteAllInBatch();
        otherVenueRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM admin_access");
        approve(APPROVED);
        Mockito.clearInvocations(variableVenueRepository, otherVenueRepository);
    }

    @ParameterizedTest
    @EnumSource(Resource.class)
    void fullLifecycle(Resource resource) throws Exception {
        assertThat(list(resource)).isEmpty();

        long later = create(resource, resource.body("Depois", 20));
        long id = create(resource, resource.body(" Novo ", 5)
            .replace("\"sortOrder\"", "\"instagram\":\" https://instagram.com/novo \",\"sortOrder\""));

        JsonNode listed = list(resource);
        assertThat(listed.findValuesAsText("name")).containsExactly("Novo", "Depois");
        JsonNode first = listed.get(0);
        assertThat(fieldNames(first)).containsExactlyInAnyOrderElementsOf(resource.responseFields);
        assertThat(first.path("id").asLong()).isEqualTo(id);
        assertThat(first.path("instagram").asText()).isEqualTo("https://instagram.com/novo");
        assertThat(listed.get(1).path("instagram").isNull()).isTrue();

        JsonNode updated = json(mockMvc.perform(approved(put(resource.item(id)), resource.body("Editado", 30)))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsByteArray());
        assertThat(fieldNames(updated)).containsExactlyInAnyOrderElementsOf(resource.responseFields);
        assertThat(updated.path("name").asText()).isEqualTo("Editado");
        assertThat(updated.path("instagram").isNull()).isTrue();
        assertThat(updated.path("sortOrder").asInt()).isEqualTo(30);
        assertThat(list(resource).findValuesAsText("name")).containsExactly("Depois", "Editado");

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(approved(delete(resource.item(id)), null))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
        }
        assertThat(list(resource).findValuesAsText("name")).containsExactly("Depois");

        mockMvc.perform(approved(put(resource.item(id)), resource.body("Fantasma", 1)))
            .andExpect(status().isNotFound())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string("{\"error\":\"NOT_FOUND\"}"));
        assertThat(list(resource)).hasSize(1);
        assertThat(list(resource).get(0).path("id").asLong()).isEqualTo(later);
    }

    static Stream<Arguments> invalidBodies() {
        List<Arguments> cases = new ArrayList<>();
        for (Resource resource : Resource.values()) {
            for (String field : resource.textFields) {
                cases.add(Arguments.of(resource, field + " com NUL", withField(resource, field, "\"a\\u0000b\"")));
                cases.add(Arguments.of(resource, field + " número", withField(resource, field, "1")));
                cases.add(Arguments.of(resource, field + " booleano", withField(resource, field, "true")));
                cases.add(Arguments.of(resource, field + " objeto", withField(resource, field, "{}")));
                if (!"instagram".equals(field)) {
                    cases.add(Arguments.of(resource, field + " em branco", withField(resource, field, "\"   \"")));
                    cases.add(Arguments.of(resource, field + " ausente", withoutField(resource, field)));
                }
            }
            cases.add(Arguments.of(resource, "id no corpo", withField(resource, "id", "5")));
            cases.add(Arguments.of(resource, "campo desconhecido", withField(resource, "extra", "\"x\"")));
            cases.add(Arguments.of(resource, "instagram sem URL", withField(resource, "instagram", "\"@perfil\"")));
            cases.add(Arguments.of(resource, "sortOrder fracionário", withField(resource, "sortOrder", "1.5")));
            cases.add(Arguments.of(resource, "sortOrder string", withField(resource, "sortOrder", "\"1\"")));
            cases.add(Arguments.of(resource, "sortOrder nulo", withField(resource, "sortOrder", "null")));
            cases.add(Arguments.of(resource, "sortOrder fora de 32 bits", withField(resource, "sortOrder", "2147483648")));
            cases.add(Arguments.of(resource, "corpo array", "[]"));
            cases.add(Arguments.of(resource, "corpo string", "\"x\""));
            cases.add(Arguments.of(resource, "corpo null", "null"));
            cases.add(Arguments.of(resource, "JSON malformado", "{\"name\":"));
        }
        cases.add(Arguments.of(Resource.OTHER, "days em outro local", withField(Resource.OTHER, "days", "\"Sexta\"")));
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidBodies")
    void invalidBodyIsRejectedWithoutWriting(Resource resource, String description, String body) throws Exception {
        long id = create(resource, resource.body("Original", 1));

        mockMvc.perform(approved(post(resource.path), body))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string("{\"error\":\"VALIDATION_ERROR\"}"));
        mockMvc.perform(approved(put(resource.item(id)), body))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("{\"error\":\"VALIDATION_ERROR\"}"));

        JsonNode listed = list(resource);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).path("name").asText()).isEqualTo("Original");
    }

    @Test
    void variableVenueKeepsFreeTextDays() throws Exception {
        long id = create(Resource.VARIABLE, Resource.VARIABLE.body("Livre", 1).replace("Quinta a domingo", "  qui-dom, às vezes  "));
        assertThat(variableVenueRepository.findById(id).orElseThrow().getDays()).isEqualTo("qui-dom, às vezes");
    }

    @ParameterizedTest
    @EnumSource(Resource.class)
    void invalidIdIsRejectedBeforeTouchingVenues(Resource resource) throws Exception {
        long id = create(resource, resource.body("Original", 1));
        Mockito.clearInvocations(variableVenueRepository, otherVenueRepository);

        for (String rawId : List.of("0", "-1", "abc", "007", "%31", "9223372036854775808")) {
            URI uri = URI.create(resource.path + "/" + rawId);
            mockMvc.perform(approved(put(uri), resource.body("Novo", 2)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string("{\"error\":\"VALIDATION_ERROR\"}"));
            mockMvc.perform(approved(delete(uri), null))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("{\"error\":\"VALIDATION_ERROR\"}"));
        }

        Mockito.verifyNoInteractions(variableVenueRepository, otherVenueRepository);
        assertThat(list(resource).get(0).path("id").asLong()).isEqualTo(id);
    }

    @ParameterizedTest
    @EnumSource(Resource.class)
    void authorizationAndCsrf(Resource resource) throws Exception {
        long id = create(resource, resource.body("Original", 1));
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, 'PENDING')",
            UUID.randomUUID(), "pending");

        List<Supplier<MockHttpServletRequestBuilder>> all = List.of(
            () -> get(resource.path), () -> post(resource.path),
            () -> put(resource.item(id)), () -> delete(resource.item(id)));
        for (Supplier<MockHttpServletRequestBuilder> route : all) {
            mockMvc.perform(withBody(route.get(), resource.body("X", 1)).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string("{\"error\":\"UNAUTHENTICATED\"}"));
            mockMvc.perform(withBody(route.get(), resource.body("X", 1)).with(user("legacy-user")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string("{\"error\":\"FORBIDDEN\"}"));
            for (String subject : List.of("missing", "pending")) {
                mockMvc.perform(withBody(route.get(), resource.body("X", 1))
                        .with(authentication(googleAuthentication(subject))).with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string("{\"error\":\"FORBIDDEN\"}"));
            }
        }

        mockMvc.perform(get(resource.path).with(authentication(googleAuthentication(APPROVED))))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"));

        List<Supplier<MockHttpServletRequestBuilder>> writes = all.subList(1, 4);
        for (Supplier<MockHttpServletRequestBuilder> route : writes) {
            mockMvc.perform(withBody(route.get(), resource.body("X", 1))
                    .with(authentication(googleAuthentication(APPROVED))))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string("{\"error\":\"CSRF_INVALID\"}"));
            mockMvc.perform(withBody(route.get(), resource.body("X", 1))
                    .with(authentication(googleAuthentication(APPROVED))).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden())
                .andExpect(content().string("{\"error\":\"CSRF_INVALID\"}"));
        }

        MockHttpSession owner = approvedSession();
        String otherToken = objectMapper.readTree(mockMvc.perform(get("/api/auth/csrf").session(approvedSession()))
            .andReturn().getResponse().getContentAsByteArray()).path("token").asText();
        for (Supplier<MockHttpServletRequestBuilder> route : writes) {
            mockMvc.perform(withBody(route.get(), resource.body("X", 1)).session(owner).header("X-CSRF-TOKEN", otherToken))
                .andExpect(status().isForbidden())
                .andExpect(content().string("{\"error\":\"CSRF_INVALID\"}"));
        }

        JsonNode listed = list(resource);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).path("name").asText()).isEqualTo("Original");
    }

    @ParameterizedTest
    @EnumSource(Resource.class)
    void revocationTakesEffectInSameSession(Resource resource) throws Exception {
        long id = create(resource, resource.body("Original", 1));
        MockHttpSession session = approvedSession();

        mockMvc.perform(get(resource.path).session(session)).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE admin_access SET status = 'PENDING' WHERE google_sub = ?", APPROVED);

        mockMvc.perform(get(resource.path).session(session))
            .andExpect(status().isForbidden())
            .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(put(resource.item(id)).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(resource.body("Novo", 2)))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete(resource.item(id)).session(session).with(csrf()))
            .andExpect(status().isForbidden());

        approve("checker");
        JsonNode listed = json(mockMvc.perform(get(resource.path).with(authentication(googleAuthentication("checker"))))
            .andReturn().getResponse().getContentAsByteArray());
        assertThat(listed.findValuesAsText("name")).containsExactly("Original");
    }

    @ParameterizedTest
    @EnumSource(Resource.class)
    void otherRoutesStayBlocked(Resource resource) throws Exception {
        long id = create(resource, resource.body("Original", 1));

        List<MockHttpServletRequestBuilder> blocked = List.of(
            get(resource.item(id)),
            patch(resource.item(id)),
            put(resource.path),
            delete(resource.path),
            put(resource.path + "/"),
            delete(resource.path + "/"),
            put(resource.item(id) + "/x"),
            delete(resource.item(id) + "/x"),
            get("/api/admin/admins"),
            post("/api/admin/venues"));
        for (MockHttpServletRequestBuilder request : blocked) {
            mockMvc.perform(withBody(request, resource.body("X", 1))
                    .with(authentication(googleAuthentication(APPROVED))).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().string("{\"error\":\"FORBIDDEN\"}"));
        }
        for (MockHttpServletRequestBuilder request : List.of(put(URI.create(resource.item(id) + ";x=1")),
                delete(URI.create(resource.item(id) + ";x=1")))) {
            int status = mockMvc.perform(withBody(request, resource.body("X", 1))
                    .with(authentication(googleAuthentication(APPROVED))).with(csrf()))
                .andReturn().getResponse().getStatus();
            assertThat(status).isBetween(400, 499);
        }
        mockMvc.perform(get("/api/agenda")).andExpect(status().isOk());
        assertThat(list(resource).findValuesAsText("name")).containsExactly("Original");
    }

    @Test
    void publicAgendaReflectsVenueChanges() throws Exception {
        for (Resource resource : Resource.values()) {
            long edited = create(resource, resource.body("Antigo", 10));
            long removed = create(resource, resource.body("Removido", 20));
            create(resource, resource.body("Novo", 5));

            mockMvc.perform(approved(put(resource.item(edited)), resource.body("Editado", 30)))
                .andExpect(status().isOk());
            mockMvc.perform(approved(delete(resource.item(removed)), null))
                .andExpect(status().isNoContent());
        }

        JsonNode agenda = json(mockMvc.perform(get("/api/agenda"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray());
        for (Resource resource : Resource.values()) {
            assertThat(agenda.path(resource.agendaKey).findValuesAsText("name")).containsExactly("Novo", "Editado");
        }
        assertThat(agenda.path("variableVenues").get(0).path("days").asText()).isEqualTo("Quinta a domingo");
    }

    private long create(Resource resource, String body) throws Exception {
        JsonNode created = json(mockMvc.perform(approved(post(resource.path), body))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsByteArray());
        assertThat(fieldNames(created)).containsExactly("id");
        return created.path("id").asLong();
    }

    private JsonNode list(Resource resource) throws Exception {
        return json(mockMvc.perform(get(resource.path).with(authentication(googleAuthentication(APPROVED))))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsByteArray());
    }

    private MockHttpServletRequestBuilder approved(MockHttpServletRequestBuilder request, String body) {
        return withBody(request, body).with(authentication(googleAuthentication(APPROVED))).with(csrf());
    }

    private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String body) {
        return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String withField(Resource resource, String field, String rawJsonValue) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode node = (ObjectNode) mapper.readTree(resource.body("Valido", 1));
            node.set(field, mapper.readTree(rawJsonValue));
            return mapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String withoutField(Resource resource, String field) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode node = (ObjectNode) mapper.readTree(resource.body("Valido", 1));
            node.remove(field);
            return mapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).toList();
    }

    private JsonNode json(byte[] body) throws Exception {
        return objectMapper.readTree(body);
    }

    private MockHttpSession approvedSession() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(googleAuthentication(APPROVED));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private void approve(String subject) {
        jdbcTemplate.update("INSERT INTO admin_access (id, google_sub, status) VALUES (?, ?, 'APPROVED')",
            UUID.randomUUID(), subject);
    }

    private static OAuth2AuthenticationToken googleAuthentication(String subject) {
        OidcIdToken token = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600),
            Map.of("sub", subject, "iss", "https://accounts.google.com"));
        return new OAuth2AuthenticationToken(new DefaultOidcUser(List.of(), token), List.of(), "google");
    }
}
