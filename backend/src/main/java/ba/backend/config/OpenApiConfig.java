package ba.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${openapi.server.http-url}")
    private String httpUrl;

    @Value("${openapi.server.https-url}")
    private String httpsUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BusNow API")
                        .version("1.0")
                        .description("BusNow backend REST API"))
                .servers(List.of(
                        new Server().url(httpsUrl).description("Production (HTTPS)"),
                        new Server().url(httpUrl).description("Local (HTTP)")
                ));
    }
}
