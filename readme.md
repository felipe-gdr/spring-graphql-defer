This is an example GraphQL backend service that has support for the `@defer` directive. 
Please notice that this service uses experimental graphql-java APIs, and `@defer` support in graphql-java is 
still under very active development.


## Testing a Query

### curl:

```shell
curl -v http://localhost:8080/graphql -H "Content-Type:application/json" --data \
'{"query": "query bookTestSimple { bookById(id: \"book-1\") { name ... @defer { author { firstName } } } ... @defer { book2: bookById(id: \"book-2\") { name } } }"}'
```

### Apollo Sandbox:

GraphiQL doen't work due to the GraphQL path being overridden, but you can use
[Apollo Sandbox](https://studio.apollographql.com/sandbox/explorer?_gl=1%2A16aak2f%2A_ga%2AOTUxMDY0NDkuMTcwMDE5ODEwNg..%2A_ga_0BGG5V2W2K%2AMTcwNzk2OTIyNC4yMS4xLjE3MDc5NzAxNzUuMC4wLjA) instead.

Example query:

```graphql
query bookTestSimple {
  bookById(id: "book-1") {
    name
    ... @defer {
      author {
         firstName
      }
    }
  }
  ... @defer {
    book2: bookById(id: "book-2") {
      name
    }
  }
}
```

