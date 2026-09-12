package com.fresveg.supply.infrastructure.config;

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
public class SupplyOpenApiConfiguration {
    @Bean
    OpenAPI supplyOpenApi() {
        return new OpenAPI().info(new Info().title("FresVeg Supply API").version("0.1.0")
                .description("Vendor listings and effective unit prices. Currency and quantity are explicit; tier prices apply to the whole quantity. Vendor selection requires live Account membership; writes require VENDOR_ADMIN. Offer browsing does not reserve stock. Inventory mutations and service reservations use a separate audited stock aggregate."))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("RS256 access token from the configured issuer and audience, with sub and exp. Vendor authority is verified through Account /me and paginated vendor memberships. Vendor tokens must also be accepted by Account. Internal reservation operations require an explicitly configured service subject and inventory.reserve scope.")));
    }

    @Bean
    OpenApiCustomizer supplyOperationContracts() {
        return api -> {
            // Add with the referencing responses: SpringDoc prunes unreferenced bean schemas earlier.
            api.getComponents().addSchemas("SupplyProblem", supplyProblem());
            api.getComponents().getSchemas().forEach((name, schema) -> {
                if (schema.getProperties() == null) { return; }
                if (name.endsWith("Request")) { schema.setAdditionalProperties(false); }
                else { schema.setRequired(new java.util.ArrayList<>(schema.getProperties().keySet())); }
                for (String field : List.of("priceId", "tierId", "validTo", "nextCursor", "harvestDate", "bestBeforeDate", "expiryDate", "reservationId")) {
                    var property = (Schema<?>) schema.getProperties().get(field);
                    if (property != null && (!"priceId".equals(field) || name.endsWith("Request")) && (!"reservationId".equals(field) || name.equals("TransactionResponse"))) { property.setTypes(java.util.Set.of("string", "null")); }
                }
            });
            api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            for (String status : List.of("400", "401", "403", "500", "503")) {
                operation.getResponses().addApiResponse(status, problemResponse(switch (status) {
                    case "400" -> "Invalid request, cursor or page size";
                    case "401" -> "Missing or invalid bearer token";
                    case "403" -> "Required verified vendor role or configured service subject/scope is missing";
                    case "503" -> "Account or Catalog dependency is unavailable; validation fails closed";
                    default -> "Unexpected failure";
                }));
            }
            if (List.of("createVendorListing", "replaceVendorListing", "createVendorLocation", "replaceVendorLocation", "listProductOffers", "initializeInventory", "adjustInventory", "recordInventoryAdjustment", "receiveInventoryBatch", "reserveInventory", "commitInventoryReservation", "releaseInventoryReservation").contains(operation.getOperationId())) {
                operation.getResponses().addApiResponse("409", problemResponse("Duplicate resource, stale version, conflicting request intent/state, or insufficient eligible inventory"));
            }
            operation.getResponses().addApiResponse("404", problemResponse("Resource does not exist or is not visible to this caller"));
            if (List.of("createVendorListing", "createVendorLocation").contains(operation.getOperationId())) {
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

    private static Schema<?> supplyProblem() {
        return new ObjectSchema().description("RFC 9457 problem with a stable code and request correlation ID.")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema()).addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema()).addProperty("instance", new StringSchema().format("uri-reference"))
                .addProperty("code", new StringSchema()).addProperty("correlationId", new StringSchema())
                .required(List.of("type", "title", "status", "detail", "instance", "code", "correlationId"));
    }

    private static ApiResponse problemResponse(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/SupplyProblem"))));
    }
}
