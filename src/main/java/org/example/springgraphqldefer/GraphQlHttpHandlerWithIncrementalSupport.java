package org.example.springgraphqldefer;

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
import java.util.Optional;

/**
 * This class is a blatant copy of {@link org.springframework.graphql.server.webflux.GraphQlHttpHandler} with some modifications that allow us to process an {@link IncrementalExecutionResult}.
 *
 * @see org.springframework.graphql.server.webflux.GraphQlHttpHandler
 */
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
                        return handleIncrementalResult((IncrementalExecutionResult) response.getExecutionResult());
                    } else {
                        builder.headers(headers -> headers.putAll(response.getResponseHeaders()));
                        builder.contentType(selectResponseMediaType(serverRequest));
                        return builder.bodyValue(response.toMap());
                    }
                });
    }

    private static MediaType selectResponseMediaType(ServerRequest serverRequest) {
        for (MediaType accepted : serverRequest.headers().accept()) {
            if (SUPPORTED_MEDIA_TYPES.contains(accepted)) {
                return accepted;
            }
        }
        return MediaType.APPLICATION_JSON;
    }

    // ***** Everything under here is code written to support incremental results ***** //
    private static final String BOUNDARY_CHARACTERS = "-";
    private static final String CHUNK_BOUNDARY = "-" + BOUNDARY_CHARACTERS + "-";
    private static final String TERMINATION_BOUNDARY = "--" + BOUNDARY_CHARACTERS + "--";
    private static final String LINE_BREAK = "\r\n";
    private static final String CHUNK_HEADERS = "content-type: application/json; charset=" + Charset.defaultCharset();


    /**
     * Converts a {@link IncrementalExecutionResult} from graphql-java into a {@link ServerResponse} that can deliver incremental chunks of data.
     * <p>
     * <a href="https://github.com/graphql/graphql-over-http/blob/main/rfcs/IncrementalDelivery.md">HTTP spec for incremental delivery<a>
     *
     * @param incrementalExecutionResult the result of executing a GraphQL operation with incremental data
     *
     * @return a ServerResponse that is able to deliver incremental data following the graphql-over-http specification.
     */
    private static Mono<ServerResponse> handleIncrementalResult(IncrementalExecutionResult incrementalExecutionResult) {
        Publisher<String> firstBoundayPublisher = Flux.just(CHUNK_BOUNDARY);

        Publisher<String> initialResponse = Flux.just(incrementalExecutionResult.toSpecification())
                .flatMap(GraphQlHttpHandlerWithIncrementalSupport::createChunk);

        Publisher<String> delayedResponses = Flux.from(incrementalExecutionResult.getIncrementalItemPublisher())
                .map(DelayedIncrementalPartialResult::toSpecification)
                .flatMap(GraphQlHttpHandlerWithIncrementalSupport::createChunk);


        Publisher<String> resultPublisher = Flux.mergeSequential(firstBoundayPublisher, initialResponse, delayedResponses);

        return ServerResponse.ok()
                .header("content-type", "multipart/mixed; boundary=\"" + BOUNDARY_CHARACTERS + "\"")
                // TODO: The 'transfer-encoding', 'connection' and 'keep-alive' headers should not be present on HTTP/2 connections
                // TODO: see https://datatracker.ietf.org/doc/html/rfc9113#name-connection-specific-header-
                .header("transfer-encoding", "chunked")
                .header("connection", "keep-alive")
                // 5 was chosen as the timeout value because is what Apollo Server uses
                .header("keep-alive", "timeout=5")
                .body(resultPublisher, String.class);
    }

    /**
     * Creates a chunk containing the data.
     * <p>
     * Example of a response containing 2 chunks:
     * <pre>
     * ---                                                                                      # -> chunk boundary
     * content-type: application/json; charset=utf-8                                            # -> chunk headers
     *
     * {"hasNext":true,"data":{"post":{"id":"1001"}}}                                           # -> chunk data
     * ---                                                                                      # |
     * content-type: application/json; charset=utf-8                                            # | another
     *                                                                                          # | chunk
     * {"hasNext":false,"incremental":[{"path":["post"],"data":{"text":"The full text"}}]}      # |
     * -----                                                                                    # -> termination boundary
     * </pre>
     *
     * @param data the data
     *
     * @return The chunk
     */
    private static Mono<String> createChunk(Map<String, Object> data) {
        final String dataString;
        try {
            dataString = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }

        String chunkText =
                LINE_BREAK +
                CHUNK_HEADERS +
                LINE_BREAK +
                LINE_BREAK +
                dataString +
                        LINE_BREAK +
                        (isLastChunk(data) ? CHUNK_BOUNDARY : TERMINATION_BOUNDARY);

        return Mono.just(chunkText);
    }

    private static boolean isLastChunk(Map<String, Object> data) {
        return Optional.ofNullable(data.get("hasNext"))
                .map(x -> Boolean.valueOf(x.toString()))
                .orElseThrow(() -> new IllegalStateException("'hasNext' field is missing in incremental data payload. " +
                        "Something has probably gone wrong in the execution of the query in graphql-java."));
    }

}
