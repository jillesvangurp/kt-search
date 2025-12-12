# KT Search Pet Store Demo

This module ships a tiny Spring Boot 4.0 application that shows how to use the `kt-search` client with Elasticsearch. It ships with two indices (`pets` and `pet-search`), read/write aliases, ETL enrichment, and a modern single-page UI served from Spring static resources.

## Running the stack

1. **Start Elasticsearch**

   ```bash
   cd petstore-demo
   docker compose up elasticsearch -d
   ```

   The compose file exposes Elasticsearch on `localhost:9200` without security.

2. **Run the application locally**

   ```bash
   ./gradlew :petstore-demo:bootRun
   ```

   The app uses the `ELASTIC_HOST`/`ELASTIC_PORT` environment variables if you point it at a different cluster.

3. **Or run everything in Docker**

   ```bash
   docker compose up --build
   ```

   The compose file builds the Boot image, waits for Elasticsearch to become healthy, and exposes the UI on http://localhost:8080.

## What the app does

- Creates the `pets` and `pet-search` indices on startup with custom mappings and read/write aliases.
- Loads rich sample data from `src/main/resources/data/pets.json` when the index is empty.
- Writes CRUD operations to the `pets` write alias and enriches documents into the `pet-search` write alias (Wikipedia links, hero images, price buckets).
- Runs all queries against `pet-search` with faceting for animal, breed, sex, age, and price buckets.
- Supports manual reindexing via the `/api/pets/reindex` endpoint and the **Reindex search** button in the UI.

## Useful commands

- Run the module tests (uses Testcontainers for Elasticsearch):

  ```bash
  ./gradlew :petstore-demo:test
  ```

- Build the runnable JAR:

  ```bash
  ./gradlew :petstore-demo:bootJar
  ```

- Trigger a reindex from the command line:

  ```bash
  curl -XPOST http://localhost:8080/api/pets/reindex
  ```

Open http://localhost:8080 to explore the SPA UI. The cards are backed by the search index so new pets become searchable immediately after you create them.
