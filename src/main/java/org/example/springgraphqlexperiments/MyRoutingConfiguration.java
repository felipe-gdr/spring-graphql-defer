package org.example.springgraphqlexperiments;

import org.reactivestreams.Publisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class MyRoutingConfiguration {

    private static final RequestPredicate ACCEPT_JSON = RequestPredicates.accept(MediaType.APPLICATION_JSON);

    @Bean
    public RouterFunction<ServerResponse> routerFunction(GraphQlHttpHandlerWithIncrementalSupport graphQlHttpHandler) {
        return RouterFunctions.route()
                .POST("/graphql", graphQlHttpHandler::handleRequest)
                .GET("/test", serverRequest -> {
                    Publisher<String> publisher = Flux.just("1\n", "2\n", "3\n").delayElements(Duration.ofMillis(1000));


                    return ServerResponse.ok().body(
                            publisher, String.class
                    );
                })
                .build();
    }

}
