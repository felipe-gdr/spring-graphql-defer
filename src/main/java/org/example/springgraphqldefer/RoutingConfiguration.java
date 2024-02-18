package org.example.springgraphqldefer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration(proxyBeanMethods = false)
public class RoutingConfiguration {

    @Bean
    public RouterFunction<ServerResponse> routerFunction(GraphQlHttpHandlerWithIncrementalSupport graphQlHttpHandler) {

        // Map the custom handler to the `/graphql` path.
        return RouterFunctions.route(RequestPredicates.path("/graphql"), graphQlHttpHandler::handleRequest);
    }

}
