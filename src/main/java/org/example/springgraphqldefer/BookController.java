package org.example.springgraphqldefer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
public class BookController {
    private static final Log logger = LogFactory.getLog(BookController.class);
    @QueryMapping
    public CompletableFuture<Book> bookById(@Argument String id) {
        return CompletableFuture.supplyAsync(() -> {
            // Different sleep times for different book items just to create
            // some interval between the resolution of fields.
            long sleepTime = id.equals("book-1") ? 100 : 800;

            sleep(sleepTime);

            return Book.getById(id);
        });
    }

    @SchemaMapping
    public CompletableFuture<Author> author(Book book) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(500);

            return Author.getById(book.authorId());
        });
    }

    private static void sleep(long sleepTime) {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
