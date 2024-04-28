package org.example.springgraphqldefer;

import java.util.Map;

import graphql.ExperimentalApi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration(proxyBeanMethods = false)
public class AppConfiguration implements WebFluxConfigurer {

    @Bean
    public RouterFunction<ServerResponse> routerFunction(ExtendedGraphQlHttpHandler handler) {
        return RouterFunctions.route(RequestPredicates.path("/graphql"), handler::handleRequest);
    }

    @Bean
    public ExtendedGraphQlHttpHandler graphQlHttpHandler(WebGraphQlHandler handler, ServerCodecConfigurer configurer) {
        return new ExtendedGraphQlHttpHandler(handler, configurer);
    }

    @Bean
    public WebGraphQlInterceptor experimentalApiEnablingInterceptor() {
        return (request, chain) -> {
            request.configureExecutionInput((input, builder) ->
                    builder.graphQLContext(Map.of(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT, true)).build());
            return chain.next(request);
        };
    }

    @Override
    public void addCorsMappings(CorsRegistry corsRegistry) {
        corsRegistry.addMapping("/**").allowedOrigins("*").allowedMethods("*").maxAge(3600);
    }

}
