package org.example.springgraphqldefer;

import java.time.Duration;

import reactor.core.publisher.Mono;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class BookController {

    @QueryMapping
    public Mono<Book> bookById(@Argument String id) {
		return Mono.delay(Duration.ofMillis(id.equals("book-1") ? 100 : 800))
                .mapNotNull(aLong -> Book.getById(id));
    }

    @SchemaMapping
    public Mono<Author> author(Book book) {
        return Mono.delay(Duration.ofMillis(500))
                .mapNotNull(aLong -> Author.getById(book.authorId()));
    }

}
