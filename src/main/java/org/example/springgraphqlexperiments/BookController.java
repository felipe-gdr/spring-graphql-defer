package org.example.springgraphqlexperiments;

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
            long sleepTime = id.equals("book-1") ? 1000 : 2000;

            logger.info("Fetching book: " + id);

            sleep(sleepTime);

            logger.info("Fetched book: " + id);

            return Book.getById(id);
        });
    }

    @SchemaMapping
    public CompletableFuture<Author> author(Book book) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Fetching author: " + book.authorId());

            sleep(1000);

            logger.info("Fetched author for: " + book.authorId());

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
