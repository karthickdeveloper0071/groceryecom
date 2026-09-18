# ADR-0009: Prometheus, Grafana and Loki as the observability stack

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

The application already produces the raw signals. Micrometer with
`micrometer-registry-prometheus` exposes metrics at `/api/actuator/prometheus`,
with percentile histograms on `http.server.requests` and the common tag
`application=groceryecom`. Logs are one JSON object per line (Elastic Common
Schema) in the `prod` and `staging` profiles, every line carrying a trace id from
MDC key `traceId`. Health, liveness and readiness probes exist.

Nothing collects any of it. A developer reads logs with `docker compose logs` and
metrics by curling the actuator endpoint, which works for one instance on a
laptop and for nothing else.

Constraints that shaped the choice: there is one service
([ADR-0001](0001-modular-monolith.md)), no cloud account and no monitoring
budget, no staging or production environment yet
([staging](../../development/staging.md)), and at least one developer has no
Docker installation, so the stack cannot be required to run the application or
the tests.

## Problem

How do we collect and look at metrics and logs, starting from a developer laptop?

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| Nothing: actuator endpoints and `docker compose logs` | zero setup; already works | no history, no aggregation across instances, no dashboards; a latency question cannot be answered at all |
| Datadog or New Relic agent | one product for metrics, logs, traces and alerts; no infrastructure to run | needs an account, a card and a per-host price nobody has approved; log ingestion cost grows with traffic; an agent and a vendor SDK in the application for a decision we cannot yet fund |
| Elasticsearch, Logstash, Kibana plus a separate metrics store | strong log search; familiar | heaviest option by memory; JVM to operate; still needs Prometheus for metrics; too much for one service |
| Prometheus + Grafana + Loki in an opt-in compose overlay | Micrometer already speaks Prometheus, so no application change; Loki indexes labels instead of full text, so it is cheap; Grafana reads both; all configuration is files in Git; identical to what a managed setup would later look like | four more containers to keep pinned and working; Loki's query language has to be learned; no alert delivery unless Alertmanager is added |
| OpenTelemetry collector as the single ingestion point | one pipeline for logs, metrics and traces; vendor neutral, so a later move to a SaaS is a config change | there is nothing to trace: one service, no cross-service calls, no spans in the code; adds a collector and an SDK to gain nothing today |

## Decision

Prometheus, Grafana, Loki and Promtail run as a separate compose file,
`docker-compose.observability.yml`, with all configuration under
[`ops/`](../../../ops/README.md). Every service is behind
`profiles: ["observability"]` and joins the existing `ecom_network`, so the stack
is started only when asked for:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile app --profile observability up -d
```

Prometheus scrapes `app:8080/api/actuator/prometheus` every 15 seconds.
Promtail reads container stdout through the Docker socket, parses the ECS JSON
and ships it to Loki with a 7 day retention. Grafana is provisioned from files:
two datasources with fixed uids, and one dashboard, `groceryecom-api`.

The application gains nothing but the Micrometer registry it already has. No
agent, no vendor SDK, no code change to turn monitoring on or off.

## Reason

Micrometer already produces a Prometheus scrape endpoint, so Prometheus is the
option with no application work at all. Loki was picked over Elasticsearch
because it indexes labels rather than message text, which is the right trade for
a project whose main log query is "everything with this trace id": cheap to run,
and a laptop can host it. Grafana reads both, so there is one UI.

Datadog and New Relic would be less work to operate and more work to approve.
There is no account and no budget line, and an application-level agent is a
commitment we would have to unpick if the answer is later no. The same signals
feed a SaaS later — a Prometheus scrape endpoint and JSON logs on stdout are
what every vendor ingests.

The OpenTelemetry collector is the option we would pick if we had more than one
service. We have one. A trace with a single span per request is exactly the
information the existing `traceId` in the logs already gives, so the collector
would be infrastructure with no question it answers.

Keeping the stack in a separate file with its own profile matters for the same
reason [ADR-0008](0008-real-postgresql-in-tests.md) matters: `./mvnw verify` and
`docker compose --profile app up` must keep working on a machine that never
starts Grafana.

## Consequences

**Positive**

- Latency, error rate and pool pressure per endpoint are answerable, with
  history, from a dashboard that is in Git and reviewed like code.
- One trace id query in Loki returns every line of a request, which is the
  documented way to debug a customer report
  ([observability standard](../../engineering/observability-standard.md)).
- No application dependency on the monitoring choice, so replacing it later
  touches `ops/` and one compose file.
- The same configuration is the starting point for staging and production.

**Negative**

- Four image versions to keep pinned and occasionally bump.
- Nothing pages anyone: there is no Alertmanager, so alerting still depends on
  someone looking at a screen. Thresholds are written down but not wired up.
- The local stack has no authentication on Loki and a default Grafana password,
  so it must never be exposed beyond a laptop.
- Log levels are only parsed when the app emits JSON, which the default local
  profile does not, so local log panels are less useful than staging ones would
  be.
- Promtail needs the Docker socket read-only to label streams with container
  names.

**Revisit when**

- A second deployable service appears, or any call crosses a process boundary. At
  that point add OpenTelemetry tracing and a collector; the trace id in the logs
  stops being sufficient.
- There is a budget and an owner for a SaaS. Then compare the operational cost of
  running this stack in staging and production against a per-host price, knowing
  the application side is already vendor neutral.
- Alerting becomes necessary, which is the first time anyone is on call. Then wire
  Alertmanager to the thresholds in the observability standard rather than
  inventing new ones.
