package org.example.springgraphqldefer;

import java.util.Arrays;
import java.util.List;

import org.springframework.lang.Nullable;

public record Book (String id, String name, int pageCount, String authorId) {

    private static final List<Book> books = Arrays.asList(
            new Book("book-1", "Effective Java", 416, "author-1"),
            new Book("book-2", "Hitchhiker's Guide to the Galaxy", 208, "author-2"),
            new Book("book-3", "Down Under", 436, "author-3")
    );

	@Nullable
    public static Book getById(String id) {
        return books.stream().filter(book -> book.id().equals(id)).findFirst().orElse(null);
    }
}
