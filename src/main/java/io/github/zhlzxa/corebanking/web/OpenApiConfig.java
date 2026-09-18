package io.github.zhlzxa.corebanking.web;

import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

/**
 * The OpenAPI description of the API, served at {@code /v3/api-docs} with Swagger UI at {@code
 * /swagger-ui.html} when {@code springdoc.api-docs.enabled} is set. It is off by default and
 * enabled for local development and demonstrations only.
 *
 * <p>Error responses are generated from {@link ErrorCode} and {@link ApiErrors}, grouped by HTTP
 * status, with the RFC 9457 problem body every error shares.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER = "bearer";
    private static final String PROBLEM = "Problem";

    /** Errors any endpoint can return, whatever it does. */
    private static final Set<ErrorCode> COMMON = EnumSet.of(
            ErrorCode.VALIDATION_FAILED,
            ErrorCode.MALFORMED_REQUEST,
            ErrorCode.UNAUTHENTICATED,
            ErrorCode.ACCESS_DENIED,
            ErrorCode.INTERNAL_ERROR);

    @Bean
    OpenAPI coreBankingOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        String version = buildProperties.stream()
                .map(BuildProperties::getVersion)
                .findFirst()
                .orElse("dev");
        return new OpenAPI()
                .info(new Info()
                        .title("Core Banking Service API")
                        .version(version)
                        .description("Accounts, transfers, FPS payments, cash and payees. Every request needs "
                                + "an OAuth2 bearer token; every error is an RFC 9457 problem with a "
                                + "stable `code` and the request's `correlationId`.")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Access token issued by the bank's OpenID Connect provider"))
                        .addSchemas(PROBLEM, problemSchema()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    OperationCustomizer errorResponses() {
        return (operation, handlerMethod) -> {
            Set<ErrorCode> codes = EnumSet.copyOf(COMMON);
            ApiErrors declared = handlerMethod.getMethodAnnotation(ApiErrors.class);
            if (declared != null) {
                codes.addAll(Arrays.asList(declared.value()));
            }
            Map<Integer, String> byStatus = codes.stream()
                    .collect(Collectors.groupingBy(
                            code -> code.httpStatus().value(),
                            TreeMap::new,
                            Collectors.mapping(
                                    code -> "`" + code.name() + "`: " + code.title(), Collectors.joining("; "))));
            ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
            byStatus.forEach((status, description) -> responses.addApiResponse(
                    String.valueOf(status),
                    new ApiResponse()
                            .description(description)
                            .content(new Content()
                                    .addMediaType(
                                            MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                            new io.swagger.v3.oas.models.media.MediaType()
                                                    .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))))));
            operation.setResponses(responses);
            return operation;
        };
    }

    private static Schema<?> problemSchema() {
        StringSchema code = new StringSchema();
        Arrays.stream(ErrorCode.values()).forEach(value -> code.addEnumItem(value.name()));
        return new ObjectSchema()
                .description("RFC 9457 problem details")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri-reference"))
                .addProperty("code", code.description("Stable error code; clients branch on this value"))
                .addProperty("correlationId", new StringSchema().description("Quote this id when contacting support"))
                .addProperty(
                        "errors",
                        new ArraySchema()
                                .description("Invalid fields, for VALIDATION_FAILED only")
                                .items(new ObjectSchema()
                                        .addProperty("field", new StringSchema())
                                        .addProperty("reason", new StringSchema())));
    }
}
