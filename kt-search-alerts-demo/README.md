# KT Search Alerts Demo

This module is a minimal showcase of the `kt-search-lib-alerts` rule engine. It wires the alerts library into a tiny JVM application so you can test-drive alert rules against a live Elasticsearch or OpenSearch cluster without pulling the full project into your own codebase.

Use this as a basis for defining your own bespoke dockerized alerts service that you can deploy in your infrastructure.

## Features

- Simple main function with minimal alerts and notifications set up via the DSL 
- Configuration controlled via environment variables
- Uses shadow plugin to package things up
- Docker ready
- Demonstrates threshold-based firing conditions (`Max`, `AtLeast`) and the cluster health rule powered by `kt-search`

## Run It
- Adjust the default connection settings in `kt-search-alerts-demo/.env` to point at the cluster you want to monitor (host, port, target index, optional Slack/SendGrid hooks).
- Build the fat jar: `./gradlew :kt-search-alerts-demo:shadowJar`.
- Launch the demo in Docker (recommended so environment variables are picked up): `./kt-search-alerts-demo/run-docker.sh`.
- Alternatively, run it directly on the JVM with your env vars exported: `./gradlew :kt-search-alerts-demo:run`.

## Customize Notifications & Rules
- Configure notification channels via `.env`: set `SLACK_HOOK` or `SENDGRID` (API key) and adjust `ENVIRONMENT` so downstream handlers can route messages appropriately.
- Define alerts in Elasticsearch using the same index named in `ALERT_TARGET`; each stored rule document should include the match query, frequency, and metadata expected by `kt-search-lib-alerts`.
- Use `kt-search-lib-alerts/scripts/sample-alert.kts` as a template to bootstrap new rules or to seed the rules index during development.
- Extend the notification pipeline by adding new handler implementations in `kt-search-lib-alerts` and swapping them into the demo’s service wiring (see the main application class under `src/main` once you scaffold your own rules).
