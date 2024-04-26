package org.example.springgraphqldefer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import graphql.incremental.DelayedIncrementalPartialResult;
import graphql.incremental.IncrementalExecutionResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.webflux.GraphQlHttpHandler;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Extends {@link GraphQlHttpHandler} with support for an {@link IncrementalExecutionResult}.
 *
 * @see <a href="https://github.com/graphql/graphql-wg/blob/main/rfcs/DeferStream.md">RFC: GraphQL Defer and Stream Directives</a>
 * @see <a href="https://github.com/graphql/graphql-over-http/blob/main/rfcs/IncrementalDelivery.md">Incremental Delivery over HTTP<a>
 */
public class ExtendedGraphQlHttpHandler extends GraphQlHttpHandler {


	public ExtendedGraphQlHttpHandler(WebGraphQlHandler graphQlHandler, CodecConfigurer codecConfigurer) {
		super(graphQlHandler, codecConfigurer);
	}


	@Override
	protected Mono<ServerResponse> prepareResponse(ServerRequest request, WebGraphQlResponse response) {
		if (!(response.getExecutionResult() instanceof IncrementalExecutionResult result)) {
			return super.prepareResponse(request, response);
		}

		Flux<Map<String, Object>> resultMapFlux =
				Flux.from(result.getIncrementalItemPublisher()).map(DelayedIncrementalPartialResult::toSpecification);

		Flux<DataBuffer> dataBufferFlux =
				Mono.just(result.toSpecification()).concatWith(resultMapFlux)
						.flatMapIterable(this::encodeResultMap)
						.concatWithValues(toDataBuffer("--"));

		return ServerResponse.ok()
				.header("content-type", "multipart/mixed; boundary=\"-\"")
				.body(dataBufferFlux, DataBuffer.class);
	}

	private List<DataBuffer> encodeResultMap(Map<String, Object> resultMap) {
		return List.of(
				toDataBuffer("\r\ncontent-type: application/json; charset=utf-8\r\n\r\n"),
				encode(resultMap),
				toDataBuffer(("\r\n---")));
	}

	private static DataBuffer toDataBuffer(String s) {
		return DefaultDataBufferFactory.sharedInstance.wrap(s.getBytes(StandardCharsets.UTF_8));
	}

}
