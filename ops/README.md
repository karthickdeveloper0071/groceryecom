# ops/ — local observability stack

Configuration for the monitoring containers defined in
`docker-compose.observability.yml`: Prometheus for metrics, Loki plus Promtail for
logs, Grafana for the UI. Nothing in here is used by the application at runtime;
it is all mounted read-only into containers.

The rules about what we log and what we alert on live in
[docs/engineering/observability-standard.md](../docs/engineering/observability-standard.md).
The reasoning behind this choice of tools is
[ADR-0009](../docs/architecture/adr/0009-observability-stack.md).

## Files

| File | What it is |
|------|------------|
| `prometheus/prometheus.yml` | Scrape config. 15s interval, one job `groceryecom` pointed at `app:8080/api/actuator/prometheus`. No alert rules yet. |
| `loki/loki-config.yml` | Loki as a single binary: filesystem storage, no authentication, 7 day retention enforced by the compactor. |
| `promtail/promtail-config.yml` | Collects container stdout through the Docker socket, labels each stream with the container name, parses the app's ECS JSON, labels the log level. |
| `grafana/provisioning/datasources/datasources.yml` | Prometheus (default, uid `prometheus`) and Loki (uid `loki`). |
| `grafana/provisioning/dashboards/dashboards.yml` | File provider that loads every dashboard in `dashboards/json/` into a folder called `GroceryEcom`. |
| `grafana/provisioning/dashboards/json/groceryecom-api.json` | The one dashboard: request rate, 5xx share, p95/p99 latency, slowest endpoints, 4xx breakdown, JVM heap, HikariCP pool, recent ERROR and WARN logs. |

Image versions are pinned in `docker-compose.observability.yml`. Bump them there,
in one commit, and start the stack once to check the configs still parse.

## Start and stop

The observability file is an overlay. Always pass both compose files, otherwise
the monitoring containers land on their own network and cannot reach the app.

```bash
# Infrastructure + app + monitoring
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile app --profile observability up -d

# Stop the monitoring containers, keep the app running
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile observability down

# Stop everything (add -v to also delete the metrics, log and dashboard volumes)
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile app --profile observability down
```

Running the app from your IDE instead of in Docker means Prometheus cannot reach
it: the job targets the container name `app`. Either run the app with
`--profile app`, or change the target to `host.docker.internal:8080` locally
without committing that change.

## URLs and credentials

| Service | URL | Notes |
|---------|-----|-------|
| Grafana | <http://localhost:3000> | Dashboard: GroceryEcom -> GroceryEcom API |
| Prometheus | <http://localhost:9090> | `/targets` shows whether the scrape works |
| Loki | <http://localhost:3100/ready> | Queried through Grafana, not directly |
| App metrics | <http://localhost:8080/api/actuator/prometheus> | Only from a private or loopback address |

Grafana logs in with `admin` / `admin` by default, from
`${GRAFANA_USER:-admin}` and `${GRAFANA_PASSWORD:-admin}`. **Change it** before
running this anywhere anybody else can reach: put `GRAFANA_PASSWORD=` in your
local `.env`. Loki has authentication disabled entirely, so port 3100 must never
be exposed outside a laptop. This stack is for local development; a shared
environment needs real credentials, TLS and no published ports.

## Checks when something looks empty

1. Prometheus `/targets`: the `groceryecom` target should be `UP`. A 401 there
   means the scrape is coming from an address outside the private ranges that
   `SecurityConfig` allows.
2. `docker compose logs promtail`: it needs read access to
   `/var/run/docker.sock`. Without it there are no logs and no `container` label.
3. Log levels are only parsed when the app emits JSON, which happens in the
   `prod` and `staging` profiles. With the default profile the lines arrive as
   plain text and `log_level` is empty.

## Adding a panel

Edit the JSON in Git; the dashboard is provisioned read-only, so changes made in
the Grafana UI cannot be saved.

1. Find the query first. Grafana **Explore**, datasource Prometheus, write the
   PromQL there until the graph is right. Metric names come from
   <http://localhost:8080/api/actuator/prometheus>, so grep that output rather
   than guessing.
2. Open `grafana/provisioning/dashboards/json/groceryecom-api.json` and copy an
   existing panel object of the type you want.
3. Change `id` to a number no other panel uses, set `title`, `description` and
   `targets[].expr`, and give it a `gridPos`. The grid is 24 columns wide;
   `{"h": 8, "w": 12, "x": 0, "y": 24}` is a half-width panel eight rows tall.
4. Keep the datasource reference as `{"type": "prometheus", "uid":
   "prometheus"}` or `{"type": "loki", "uid": "loki"}`. Those uids are fixed in
   `datasources.yml`. Do not use `${DS_PROMETHEUS}` inputs: they require an
   `__inputs` block and the dashboard fails to load without it.
5. Validate the file before committing. A broken dashboard does not show an
   error in the UI, it simply never appears:

   ```bash
   python -c "import json;json.load(open('ops/grafana/provisioning/dashboards/json/groceryecom-api.json'))"
   ```

6. The file provider rescans every 30 seconds, so a reload of the browser page
   is enough. No container restart needed.

Adding a whole dashboard is the same, in a new file with a new `uid`.
