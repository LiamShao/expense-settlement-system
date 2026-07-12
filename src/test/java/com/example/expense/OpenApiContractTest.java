package com.example.expense;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {

    private static final Set<String> EXPECTED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/me",
            "/api/expense-applications",
            "/api/expense-applications/{id}",
            "/api/expense-applications/{id}/submit",
            "/api/expense-applications/{id}/approve",
            "/api/expense-applications/{id}/return",
            "/api/audit-logs",
            "/actuator/health"
    );

    @Test
    void openApi_正常系_正式YAMLが静的Resourceへコピーされる() throws IOException {
        String source = Files.readString(Path.of("docs/openapi.yaml"), StandardCharsets.UTF_8);
        String packaged = readPackagedOpenApi();

        assertThat(packaged).isEqualTo(source);
    }

    @Test
    @SuppressWarnings("unchecked")
    void openApi_正常系_実装Endpointと共通契約を定義する() throws IOException {
        Map<String, Object> document = new Yaml().load(readPackagedOpenApi());
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Object> securitySchemes = (Map<String, Object>) components.get("securitySchemes");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> basicAuth = (Map<String, Object>) securitySchemes.get("basicAuth");

        assertThat(document.get("openapi")).isEqualTo("3.0.3");
        assertThat(paths.keySet()).containsExactlyInAnyOrderElementsOf(EXPECTED_PATHS);
        assertThat(basicAuth)
                .containsEntry("type", "http")
                .containsEntry("scheme", "basic");
        assertThat(schemas).containsKeys("ErrorResponse", "ValidationErrorDetail");
    }

    @Test
    @SuppressWarnings("unchecked")
    void openApi_正常系_operationIdと認証と共通500Responseを定義する() throws IOException {
        Map<String, Object> document = new Yaml().load(readPackagedOpenApi());
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) document.get("paths");

        var operations = paths.entrySet().stream()
                .flatMap(path -> path.getValue().entrySet().stream()
                        .filter(method -> Set.of("get", "post", "put", "delete", "patch").contains(method.getKey()))
                        .map(method -> Map.entry(path.getKey(), (Map<String, Object>) method.getValue())))
                .toList();

        var operationIds = operations.stream()
                .map(operation -> operation.getValue().get("operationId").toString())
                .toList();
        assertThat(operationIds).doesNotHaveDuplicates();

        var protectedOperations = operations.stream()
                .filter(operation -> !Set.of("/api/auth/login", "/actuator/health").contains(operation.getKey()))
                .toList();
        assertThat(protectedOperations)
                .allSatisfy(operation -> assertThat(operation.getValue()).containsKey("security"));

        var operationsWithout500 = operations.stream()
                .filter(operation -> !operation.getKey().equals("/actuator/health"))
                .filter(operation -> {
                    Map<String, Object> responses = (Map<String, Object>) operation.getValue().get("responses");
                    return !responses.containsKey("500");
                })
                .map(operation -> operation.getValue().get("operationId").toString())
                .collect(Collectors.toList());
        assertThat(operationsWithout500).isEmpty();
    }

    private String readPackagedOpenApi() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/static/openapi.yaml")) {
            assertThat(inputStream).as("packaged OpenAPI resource").isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
