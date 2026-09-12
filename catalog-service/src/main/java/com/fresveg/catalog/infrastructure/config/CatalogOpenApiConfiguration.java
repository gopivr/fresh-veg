package com.fresveg.catalog.infrastructure.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.math.BigDecimal;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class CatalogOpenApiConfiguration {
    @Bean
    OpenAPI catalogOpenApi() {
        return new OpenAPI().info(new Info().title("FresVeg Catalog API").version("0.1.0")
                .description("Reusable master products, classification and metadata. Public reads expose ACTIVE products. Mutations require stored PLATFORM_ADMIN authority from Account."))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("RS256 access token from the configured issuer and audience, with sub and exp. Admin authority is verified through Account /me using the forwarded token. The token must also be accepted by Account.")));
    }

    @Bean
    OpenApiCustomizer catalogOperationContracts() {
        return api -> {
            // Add with the referencing responses: SpringDoc prunes unreferenced bean schemas earlier.
            api.getComponents().addSchemas("CatalogProblem", catalogProblem());
            api.getComponents().getSchemas().forEach((name, schema) -> {
                if (schema.getProperties() == null) { return; }
                if (name.endsWith("Request")) { schema.setAdditionalProperties(false); }
                else { schema.setRequired(new java.util.ArrayList<>(schema.getProperties().keySet())); }
                for (String field : List.of("description", "parentCategoryId", "nextCursor")) {
                    var property = (Schema<?>) schema.getProperties().get(field);
                    if (property != null) { property.setTypes(java.util.Set.of("string", "null")); }
                }
            });
            api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            for (String status : List.of("400", "401", "403", "500", "503")) {
                operation.getResponses().addApiResponse(status, problemResponse(switch (status) {
                    case "400" -> "Invalid request, cursor or page size";
                    case "401" -> "Missing or invalid bearer token";
                    case "403" -> "Stored platform administrator authority is required";
                    case "503" -> "Account authorization is unavailable; writes fail closed";
                    default -> "Unexpected failure";
                }));
            }
            if (List.of("createProduct", "replaceProduct", "patchProduct", "createCategory").contains(operation.getOperationId())) {
                operation.getResponses().addApiResponse("409", problemResponse("Duplicate code, invalid relation or stale aggregate version"));
            }
            if (!"listCategories".equals(operation.getOperationId())) {
                operation.getResponses().addApiResponse("404", problemResponse("Resource does not exist or is not publicly visible"));
            }
            if (List.of("createProduct", "createCategory").contains(operation.getOperationId())) {
                var created = operation.getResponses().remove("200");
                if (created != null) {
                    operation.getResponses().addApiResponse("201", created.description("Resource created")
                            .addHeaderObject("Location", new Header().schema(new StringSchema()).description("Created resource path")));
                }
            }
            operation.getResponses().forEach((status, response) -> response.addHeaderObject("X-Correlation-ID",
                    new Header().schema(new StringSchema()).description("Validated incoming ID or generated UUID")));
            operation.getResponses().get("401").addHeaderObject("WWW-Authenticate", new Header().schema(new StringSchema()).example("Bearer"));
            operation.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().in("header")
                    .name("X-Correlation-ID").required(false).description("Optional safe request ID; invalid values are replaced")
                    .schema(new StringSchema().maxLength(128).pattern("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")));
            if (operation.getParameters() != null) {
                operation.getParameters().forEach(parameter -> {
                    if ("pageSize".equals(parameter.getName())) {
                        parameter.getSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(100));
                    } else if ("cursor".equals(parameter.getName())) {
                        parameter.description("Opaque nextCursor from the preceding page; preserve filters and sort");
                    }
                });
            }
        }));
        };
    }

    private static Schema<?> catalogProblem() {
        return new ObjectSchema().description("RFC 9457 problem with a stable code and request correlation ID.")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema()).addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema()).addProperty("instance", new StringSchema().format("uri-reference"))
                .addProperty("code", new StringSchema()).addProperty("correlationId", new StringSchema())
                .required(List.of("type", "title", "status", "detail", "instance", "code", "correlationId"));
    }

    private static ApiResponse problemResponse(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/CatalogProblem"))));
    }
}
