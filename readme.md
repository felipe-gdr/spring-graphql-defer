
## Apollo Sandbox 

Use Apollo Sandbox to send queries to this server, as GraphiQL won't quite work due to the GraphQL path being overridden.

https://studio.apollographql.com/sandbox/explorer?_gl=1%2A16aak2f%2A_ga%2AOTUxMDY0NDkuMTcwMDE5ODEwNg..%2A_ga_0BGG5V2W2K%2AMTcwNzk2OTIyNC4yMS4xLjE3MDc5NzAxNzUuMC4wLjA.



## Example query:

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
