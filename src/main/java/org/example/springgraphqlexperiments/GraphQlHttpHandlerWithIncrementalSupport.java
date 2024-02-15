/*
 * Copyright 2020-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.example.springgraphqlexperiments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.incremental.DelayedIncrementalPartialResult;
import graphql.incremental.IncrementalExecutionResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class GraphQlHttpHandlerWithIncrementalSupport {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Log logger = LogFactory.getLog(GraphQlHttpHandlerWithIncrementalSupport.class);

    // To be removed in favor of Framework's MediaType.APPLICATION_GRAPHQL_RESPONSE
    private static final MediaType APPLICATION_GRAPHQL_RESPONSE =
            new MediaType("application", "graphql-response+json");

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF =
            new ParameterizedTypeReference<Map<String, Object>>() {
            };

    @SuppressWarnings("removal")
    private static final List<MediaType> SUPPORTED_MEDIA_TYPES =
            Arrays.asList(APPLICATION_GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON, MediaType.APPLICATION_GRAPHQL);

    private final WebGraphQlHandler graphQlHandler;

    /**
     * Create a new instance.
     *
     * @param graphQlHandler common handler for GraphQL over HTTP requests
     */
    public GraphQlHttpHandlerWithIncrementalSupport(WebGraphQlHandler graphQlHandler) {
        Assert.notNull(graphQlHandler, "WebGraphQlHandler is required");
        this.graphQlHandler = graphQlHandler;
    }

    /**
     * Handle GraphQL requests over HTTP.
     *
     * @param serverRequest the incoming HTTP request
     *
     * @return the HTTP response
     */
    public Mono<ServerResponse> handleRequest(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(MAP_PARAMETERIZED_TYPE_REF)
                .flatMap(body -> {
                    WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
                            serverRequest.uri(), serverRequest.headers().asHttpHeaders(),
                            serverRequest.cookies(), serverRequest.attributes(), body,
                            serverRequest.exchange().getRequest().getId(),
                            serverRequest.exchange().getLocaleContext().getLocale());
                    if (logger.isDebugEnabled()) {
                        logger.trace("Executing: " + graphQlRequest);
                    }
                    return this.graphQlHandler.handleRequest(graphQlRequest);
                })
                .flatMap(response -> {
                    boolean isIncremental = response.getExecutionResult() instanceof IncrementalExecutionResult;

                    if (logger.isDebugEnabled()) {
                        logger.debug("Execution complete. Is incremental result? " + isIncremental);
                    }

                    ServerResponse.BodyBuilder builder = ServerResponse.ok();

                    if (isIncremental) {
                        return handleIncrementalResult(response);
                    } else {
                        builder.headers(headers -> headers.putAll(response.getResponseHeaders()));
                        builder.contentType(selectResponseMediaType(serverRequest));
                        return builder.bodyValue(response.toMap());
                    }
                });
    }

    private class Chunk {

    }

    private static final String BOUNDARY_CHAR = "-";
    private static final String CHUNK_BOUNDARY = "-" + BOUNDARY_CHAR + "-";
    private static final String TERMINATION_BOUNDARY = "--" + BOUNDARY_CHAR + "--";
    private static final String LINE_BREAK = "\n";
    private static final String CHUNK_HEADERS = "Content-Type: application/json; charset=" + Charset.defaultCharset();


    private static Mono<ServerResponse> handleIncrementalResult(WebGraphQlResponse response) {
        IncrementalExecutionResult incrementalExecutionResult = (IncrementalExecutionResult) response.getExecutionResult();

        Publisher<String> initialResponse = Flux.just(incrementalExecutionResult.toSpecification())
                .flatMap(GraphQlHttpHandlerWithIncrementalSupport::createChunk);

        Publisher<String> delayedResponses = Flux.from(incrementalExecutionResult.getIncrementalItemPublisher())
                .map(DelayedIncrementalPartialResult::toSpecification)
                .flatMap(GraphQlHttpHandlerWithIncrementalSupport::createChunk);

        Publisher<String> terminationPublisher = Flux.just(TERMINATION_BOUNDARY);

        Publisher<String> resultPublisher = Flux.mergeSequential(initialResponse, delayedResponses, terminationPublisher);

        return ServerResponse.ok()
                .header("Content-Type", "multipart/mixed; boundary=\"" + BOUNDARY_CHAR + "\"")
                .header("Transfer-Encoding", "chunked")
                .header("Connection", "keep-alive")
                .header("Keep-Alive", "timeout=5")
                .body(resultPublisher, String.class);
    }

    private static Mono<String> createChunk(Map<String, Object> data) {
        final String dataString;
        try {
            dataString = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }

        String stringBuilder = CHUNK_BOUNDARY +
                LINE_BREAK +
                CHUNK_HEADERS +
                LINE_BREAK +
                LINE_BREAK +
                dataString +
                LINE_BREAK;

        return Mono.just(stringBuilder);
    }

    private static MediaType selectResponseMediaType(ServerRequest serverRequest) {
        for (MediaType accepted : serverRequest.headers().accept()) {
            if (SUPPORTED_MEDIA_TYPES.contains(accepted)) {
                return accepted;
            }
        }
        return MediaType.APPLICATION_JSON;
    }

}
