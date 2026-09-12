package com.fresveg.fulfillment.infrastructure.config;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
public class FulfillmentOpenApiConfiguration {
 @Bean OpenAPI fulfillmentOpenApi(){return new OpenAPI().info(new Info().title("FresVeg Fulfillment API").version("0.1.0")).components(new Components().addSecuritySchemes("bearerAuth",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT").description("RS256 token from configured issuer/audience. Internal fulfillment creation requires a configured service subject.")));}
 @Bean OpenApiCustomizer fulfillmentCustomizer(){return api->{api.getComponents().addSchemas("FulfillmentProblem",new ObjectSchema().addProperty("type",new StringSchema().format("uri")).addProperty("title",new StringSchema()).addProperty("status",new IntegerSchema()).addProperty("detail",new StringSchema()).addProperty("code",new StringSchema()).addProperty("correlationId",new StringSchema()));api.getPaths().values().forEach(path->path.readOperations().forEach(op->{for(String code:java.util.List.of("400","401","403","404","409","500"))op.getResponses().addApiResponse(code,new ApiResponse().description("Fulfillment error").content(new Content().addMediaType("application/problem+json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/FulfillmentProblem")))));op.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().in("header").name("X-Correlation-ID").schema(new StringSchema().maxLength(128)));}));};}
}
