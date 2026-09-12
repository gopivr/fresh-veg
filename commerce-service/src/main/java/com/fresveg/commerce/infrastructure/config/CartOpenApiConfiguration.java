package com.fresveg.commerce.infrastructure.config;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;
import java.math.BigDecimal;
import java.util.List;
@Configuration(proxyBeanMethods=false)
public class CartOpenApiConfiguration {
 @Bean OpenAPI cartApi() { return new OpenAPI().info(new Info().title("FresVeg Commerce API").version("0.1.0").description("Customer-owned carts. Prices are current Supply unit prices, never persisted or client-authoritative. Checkout preview resolves current address, prices, stock and explicit delivery/pricing policies without creating an order or reservation. Orders persist authoritative snapshots, authorize payment through a provider-neutral gateway, commit inventory and retain a transactional outbox."))
 .components(new Components().addSecuritySchemes("bearerAuth",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT").description("Trusted issuer, RS256, sub/exp and Commerce audience; token must also be accepted by Account. Stored Account customer identity establishes ownership."))); }
 @Bean OpenApiCustomizer cartContracts() { return api->{
  api.getComponents().addSchemas("CommerceProblem",new ObjectSchema().addProperty("type",new StringSchema().format("uri")).addProperty("title",new StringSchema()).addProperty("status",new IntegerSchema()).addProperty("detail",new StringSchema()).addProperty("instance",new StringSchema().format("uri-reference")).addProperty("code",new StringSchema()).addProperty("correlationId",new StringSchema()).required(List.of("type","title","status","detail","instance","code","correlationId")));
  api.getComponents().getSchemas().forEach((name,schema)->{
   if(schema.getProperties()==null)return;
   if(name.endsWith("Request"))schema.setAdditionalProperties(false);else schema.setRequired(new java.util.ArrayList<>(schema.getProperties().keySet()));
   for(String field:List.of("nextCursor","tierId","line2","region","phone")) { var p=(Schema<?>)schema.getProperties().get(field);if(p!=null)p.setTypes(java.util.Set.of("string","null")); }
   if(name.equals("OrderLine"))schema.addProperty("productSnapshot",new ObjectSchema().additionalProperties(true).description("Immutable product, listing, vendor and authoritative price snapshots"));
   if(name.equals("OrderResponse") || name.equals("VendorOrderResponse"))schema.addProperty("status",new StringSchema()._enum(List.of("PENDING_PAYMENT","PAYMENT_AUTHORIZED","CONFIRMED","PAYMENT_FAILED","CANCEL_PENDING","CANCELLED")));
   if(name.equals("VendorOrderResponse"))schema.addProperty("vendorStatus",new StringSchema()._enum(List.of("PENDING","ACCEPTED","REJECTED")));
   if(name.equals("ItemResponse")) {
    var p=(Schema<?>)schema.getProperties().get("currentPrice");schema.addProperty("currentPrice",new ComposedSchema().addOneOfItem(p).addOneOfItem(new Schema<>().types(java.util.Set.of("null"))));
   }
  });
  api.getComponents().getSchemas().remove("JsonNode");
  api.getPaths().values().forEach(path->path.readOperations().forEach(op->{
   for(String code:List.of("400","401","403","404","409","500","503")) op.getResponses().addApiResponse(code,new ApiResponse().description(switch(code){case "400"->"Invalid request or changed cursor";case "401"->"Missing/invalid JWT";case "403"->"Account identity denied";case "404"->"Owned cart/item/address not found";case "409"->"Stale/empty cart, unavailable item/stock/address delivery combination, or cart limit";case "503"->"Owner unavailable, malformed response, timeout or resilience rejection; no stale price fallback";default->"Unexpected failure";}).content(new Content().addMediaType("application/problem+json",new MediaType().schema(new Schema<>().$ref("#/components/schemas/CommerceProblem")))));
   if(op.getOperationId().equals("createCart") || op.getOperationId().equals("createOrder")) { var response=op.getResponses().remove("200");if(response!=null)op.getResponses().addApiResponse("201",response.addHeaderObject("Location",new Header().schema(new StringSchema()))); }
   op.getResponses().forEach((code,response)->response.addHeaderObject("X-Correlation-ID",new Header().schema(new StringSchema())));
   op.getResponses().get("401").addHeaderObject("WWW-Authenticate",new Header().schema(new StringSchema()).example("Bearer"));
   op.addParametersItem(new io.swagger.v3.oas.models.parameters.Parameter().in("header").name("X-Correlation-ID").schema(new StringSchema().maxLength(128)).description("Optional safe request ID"));
   op.getParameters().forEach(p->{if(p.getName().equals("Idempotency-Key"))p.getSchema().minLength(1).maxLength(128).pattern("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");if(p.getName().equals("pageSize"))p.getSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.TEN);if(p.getName().equals("cursor") && !op.getOperationId().equals("listOrders") && !op.getOperationId().equals("listVendorOrders"))p.description("Opaque cursor bound to this customer, cart and cart version; restart pagination after an edit");});
  }));
 }; }
}
