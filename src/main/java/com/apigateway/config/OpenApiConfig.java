package com.apigateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

  private static final String API_KEY_SCHEME = "ApiKeyAuth";
  private static final String JWT_SCHEME = "BearerAuth";

  @Bean
  public OpenAPI gatewayOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("SQL API Gateway")
            .description("""
                Doris / ClickHouse / PostgreSQL 动态 SQL 数据 API 网关。

                - **Data API**（`/api/data/**`）：对外数据查询，需 API Key；响应统一为 `ApiResponse`（`code=0` 时 `data` 为分页结果）
                - **Admin API**（`/admin/**`）：管理配置，需 JWT 登录

                管理端登录：`POST /admin/auth/login` 获取 JWT。
                """)
            .version("1.0.1")
            .contact(new Contact().name("knowcai"))
            .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
        .servers(List.of(new Server().url("/").description("Current host")))
        .components(new Components()
            .addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-Api-Key")
                .description("或使用 Authorization: Bearer <api_key>"))
            .addSecuritySchemes(JWT_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("管理端 JWT，从 /admin/auth/login 获取")))
        .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME));
  }

  /** 补充 Data API 通用路径说明（动态 apiCode 无法由注解完全扫描）。 */
  @Bean
  public OpenApiCustomizer dataApiPathsCustomizer() {
    return openApi -> {
      var paths = openApi.getPaths();
      if (paths == null) {
        return;
      }
      var queryResultSchema = new Schema<Map<String, Object>>().$ref("#/components/schemas/QueryResult");
      var apiResponseSchema = new Schema<Map<String, Object>>()
          .type("object")
          .addProperty("code", new Schema<>().type("integer").example(0))
          .addProperty("message", new Schema<>().type("string").example("success"))
          .addProperty("data", queryResultSchema)
          .addProperty("requestId", new Schema<>().type("string"));
      var errorSchema = new Schema<Map<String, Object>>().$ref("#/components/schemas/ApiResponse");

      var getOp = new io.swagger.v3.oas.models.Operation()
          .tags(List.of("Data API"))
          .summary("分页查询（GET）")
          .description("已发布版本的动态 SQL API。page、pageSize 为必填 query 参数。")
          .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME))
          .parameters(List.of(
              pathParam("version", "API 版本号", "integer"),
              pathParam("theme", "主题编码", "string"),
              pathParam("apiCode", "API 编码", "string"),
              queryParam("page", "页码，从 1 开始", true, "integer"),
              queryParam("pageSize", "每页条数", true, "integer")))
          .responses(new ApiResponses()
              .addApiResponse("200", okResponse("查询成功", apiResponseSchema))
              .addApiResponse("401", errorResponse("缺少或无效 API Key", errorSchema))
              .addApiResponse("403", errorResponse("无 API 授权", errorSchema))
              .addApiResponse("429", errorResponse("限流", errorSchema))
              .addApiResponse("503", errorResponse("熔断", errorSchema)));

      var postOp = new io.swagger.v3.oas.models.Operation()
          .tags(List.of("Data API"))
          .summary("分页查询（POST）")
          .description("SQL 参数通过 JSON body 传入；page、pageSize 仍为 query 参数。")
          .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME))
          .parameters(List.of(
              pathParam("version", "API 版本号", "integer"),
              pathParam("theme", "主题编码", "string"),
              pathParam("apiCode", "API 编码", "string"),
              queryParam("page", "页码", true, "integer"),
              queryParam("pageSize", "每页条数", true, "integer")))
          .responses(getOp.getResponses());

      paths.addPathItem("/api/data/v{version}/{theme}/{apiCode}",
          new io.swagger.v3.oas.models.PathItem().get(getOp).post(postOp));
    };
  }

  private static Parameter pathParam(String name, String desc, String type) {
    return new Parameter().in("path").name(name).required(true).description(desc)
        .schema(new Schema<>().type(type));
  }

  private static Parameter queryParam(String name, String desc, boolean required, String type) {
    return new Parameter().in("query").name(name).required(required).description(desc)
        .schema(new Schema<>().type(type));
  }

  private static ApiResponse okResponse(String desc, Schema<?> schema) {
    return new ApiResponse().description(desc)
        .content(new Content().addMediaType("application/json",
            new MediaType().schema(schema)));
  }

  private static ApiResponse errorResponse(String desc, Schema<?> schema) {
    return new ApiResponse().description(desc)
        .content(new Content().addMediaType("application/json",
            new MediaType().schema(schema)));
  }
}
