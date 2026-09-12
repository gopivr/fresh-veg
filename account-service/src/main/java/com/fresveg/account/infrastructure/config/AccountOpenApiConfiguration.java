package com.fresveg.account.infrastructure.config;

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
public class AccountOpenApiConfiguration {
    @Bean
    OpenAPI accountOpenApi() {
        return new OpenAPI().info(new Info().title("FresVeg Account API").version("0.1.0")
                .description("Account identity, owned addresses and stored vendor memberships. First authenticated use provisions a customer account. Collections use ascending UUID cursors; they are not snapshots."))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("RS256 access token from the configured issuer and audience, with sub and exp. Ownership and roles come from Account data.")));
    }

    @Bean
    OpenApiCustomizer accountOperationContracts() {
        return api -> {
            // Add with the referencing responses: SpringDoc prunes unreferenced bean schemas earlier.
            api.getComponents().addSchemas("AccountProblem", accountProblem());
            api.getComponents().getSchemas().forEach((name, schema) -> {
                if (schema.getProperties() == null) { return; }
                if (name.endsWith("Request")) { schema.setAdditionalProperties(false); }
                else { schema.setRequired(new java.util.ArrayList<>(schema.getProperties().keySet())); }
                for (String field : List.of("label", "line2", "region", "phone", "displayName", "email", "locale", "timeZone", "nextCursor")) {
                    var property = (Schema<?>) schema.getProperties().get(field);
                    if (property != null) { property.setTypes(java.util.Set.of("string", "null")); }
                }
            });
            api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            for (String status : List.of("400", "401", "403", "500")) {
                operation.getResponses().addApiResponse(status, problemResponse(switch (status) {
                    case "400" -> "Invalid request, cursor or page size";
                    case "401" -> "Missing or invalid bearer token";
                    case "403" -> "Account or customer profile is inactive, or access is denied";
                    default -> "Unexpected failure";
                }));
            }
            if (List.of("getMyAccount", "listMyAddresses", "createMyAddress", "replaceMyAddress").contains(operation.getOperationId())) {
                operation.getResponses().addApiResponse("409", problemResponse("Account data conflict; for address updates, reload the current version"));
            }
            if ("replaceMyAddress".equals(operation.getOperationId())) {
                operation.getResponses().addApiResponse("404", problemResponse("Address does not exist or is owned by another customer"));
            }
            if ("createMyAddress".equals(operation.getOperationId())) {
                var created = operation.getResponses().remove("200");
                if (created != null) {
                    operation.getResponses().addApiResponse("201", created.description("Address created")
                            .addHeaderObject("Location", new Header().schema(new StringSchema()).description("Owned address resource path")));
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
                        parameter.description("Exclusive last UUID from nextCursor; omit for the first page");
                    }
                });
            }
        }));
        };
    }

    private static Schema<?> accountProblem() {
        return new ObjectSchema().description("RFC 9457 problem with a stable code and request correlation ID.")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema()).addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema()).addProperty("instance", new StringSchema().format("uri-reference"))
                .addProperty("code", new StringSchema()).addProperty("correlationId", new StringSchema())
                .required(List.of("type", "title", "status", "detail", "instance", "code", "correlationId"));
    }

    private static ApiResponse problemResponse(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/AccountProblem"))));
    }
}
