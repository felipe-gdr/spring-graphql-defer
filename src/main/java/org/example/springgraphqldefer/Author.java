package org.example.springgraphqldefer;

import java.util.Arrays;
import java.util.List;

import org.springframework.lang.Nullable;

public record Author (String id, String firstName, String lastName) {

    private static final List<Author> authors = Arrays.asList(
            new Author("author-1", "Joshua", "Bloch"),
            new Author("author-2", "Douglas", "Adams"),
            new Author("author-3", "Bill", "Bryson")
    );

	@Nullable
    public static Author getById(String id) {
        return authors.stream().filter(author -> author.id().equals(id)).findFirst().orElse(null);
    }
}
