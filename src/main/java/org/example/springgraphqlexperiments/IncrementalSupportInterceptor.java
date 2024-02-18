package org.example.springgraphqlexperiments;

import graphql.ExperimentalApi;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Component
public class IncrementalSupportInterceptor implements WebGraphQlInterceptor {

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        request.configureExecutionInput((executionInput, builder) ->
                // Enable incremental support in graphql-java execution
                builder.graphQLContext(Collections.singletonMap(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT, true)
                ).build());

        return chain.next(request);
    }
}
