# k6-loadtest-mcp

Describe an API in plain English (or point at example requests), and this MCP server turns that into a
runnable [k6](https://k6.io) load test, runs it, and hands back structured, deterministic metrics
(p50/p90/p95/p99 latency, error rate, RPS, per-endpoint breakdown, threshold pass/fail) for the host LLM
(Claude Desktop / Claude Code) to turn into a human-readable performance report.

It's an MCP server, not a standalone CLI: the "understanding what to test" step is done by whichever
Claude client you're using (no separate API key needed), and this server does the mechanical, deterministic
parts — script generation, execution, and result parsing — in code, so the numbers in the report are computed,
not guessed by an LLM eyeballing a log.

## Pipeline

```
you describe the API / paste example requests
        │  (host LLM turns this into a structured TestPlan)
        ▼
generate_k6_script   → deterministic templating, reviewable script.js
        ▼
smoke_test_script    → 1 VU / 1 iteration, catches syntax/runtime errors fast
        ▼
run_load_test        → full run at the VUs/duration/stages baked into the script
        ▼
get_test_metrics     → k6's summary.json parsed into structured RunMetrics
        ▼
host LLM writes the narrative report from those structured metrics
```

`run_full_test` chains all four steps in one call for convenience; the granular tools let you inspect/adjust
the script between steps or re-run without regenerating.

## Guardrail

Load tests only run against hosts listed in `~/.k6-loadtest-mcp/config.json`'s `allowedHosts`
(`localhost`/`127.0.0.1` by default — the file is created automatically on first run). **The tools cannot
expand this list themselves** — hitting a host you don't control or aren't authorized to test can look like
a denial-of-service attack. Add a host yourself, by hand, once you've confirmed you're authorized to
load-test it:

```json
{ "allowedHosts": ["localhost", "127.0.0.1", "staging.myapp.example.com"] }
```

This lives under your home directory (override with `K6_LOADTEST_MCP_HOME`), not inside the installed package —
so it's in the same predictable place whether you cloned this repo, `npm install`ed it, or ran it via
`npx github:<owner>/k6-loadtest-mcp`. Run artifacts (`runs/<timestamp>-<name>/script.js`, `summary.json`, ...)
live alongside it at `~/.k6-loadtest-mcp/runs/`.

Two more guardrails, both enforced in code rather than through anything a `TestPlan` (agent-authored, possibly
prompt-injected) can control:

- **VUs are capped at `MAX_VUS` (1000)** in `src/types.ts` — a plan asking for more is rejected before a script
  is ever generated.
- **Generated requests don't follow redirects** (`redirects: 0`). The host allowlist only vets `baseUrl`;
  without this, a 3xx response could silently send load at a host that was never approved. A redirect just
  shows up as its own status code — set `expectStatus` to the 3xx code if a request is meant to test the
  redirect itself.

## Setup

Prerequisites: Node.js 18+, and [k6](https://k6.io/docs/get-started/installation/) installed and on your
`PATH` (or set `K6_BIN` to its full path).

```bash
npm install
npm run build
```

### Try it locally first

A tiny demo API (`demo/demo-api.mjs`) is included so you can see the whole pipeline work without pointing it
at anything real:

```bash
npm run demo-api        # starts http://localhost:4000 in one terminal
npm run harness          # in another terminal: generates a script, smoke-tests, runs a staged
                          # load test against it, and prints structured metrics
```

### Register with Claude Desktop / Claude Code

Add to your MCP config (Claude Desktop: `claude_desktop_config.json`; Claude Code: `.mcp.json` or
`claude mcp add`):

```json
{
  "mcpServers": {
    "k6-loadtest-mcp": {
      "command": "node",
      "args": ["/absolute/path/to/k6-loadtest-mcp/dist/index.js"]
    }
  }
}
```

Or, once it's on a public GitHub repo, skip the local build entirely:

```json
{
  "mcpServers": {
    "k6-loadtest-mcp": {
      "command": "npx",
      "args": ["-y", "github:<owner>/k6-loadtest-mcp"]
    }
  }
}
```

Then, in conversation: describe your API (or paste a few example `curl` commands), say what load profile you
want, and ask it to run and summarize a load test. The host LLM builds the structured `TestPlan` and drives
the four tools below.

## Tools

| Tool | Purpose |
|---|---|
| `generate_k6_script` | `TestPlan` → runnable k6 script + `runDir` |
| `smoke_test_script` | 1 VU / 1 iteration sanity check |
| `run_load_test` | Full run at the script's baked-in load profile |
| `get_test_metrics` | Parsed `summary.json` → structured `RunMetrics` |
| `run_full_test` | All of the above chained, given a `TestPlan` |

### TestPlan shape

```jsonc
{
  "name": "checkout-api-smoke",
  "baseUrl": "http://localhost:4000",
  "requests": [
    { "name": "ListUsers", "method": "GET", "path": "/users", "weight": 5, "expectStatus": 200 },
    { "name": "GetReports", "method": "GET", "path": "/reports", "weight": 3, "maxDurationMs": 300 },
    { "name": "CreateOrder", "method": "POST", "path": "/orders", "weight": 2,
      "body": { "item": "widget", "qty": 1 }, "expectStatus": 201 }
  ],
  "loadProfile": {
    "type": "ramping",
    "stages": [{ "duration": "10s", "target": 10 }, { "duration": "20s", "target": 40 }, { "duration": "10s", "target": 0 }]
  },
  "thresholds": { "p95Ms": 250, "errorRatePct": 5 },
  "thinkTimeMs": 200
}
```

See `src/types.ts` for the full zod schema (also what the MCP client sees as the tool's input schema).

## Notes on k6's summary JSON (v2.1.0, verified by inspection)

- Trend metrics (latencies): `avg/min/med/max/p(90)/p(95)`. `p(99)` is **not** included by default — the
  generated script always sets `summaryTrendStats` to request it explicitly.
- `med` is the p50 — there's no `p(50)` key.
- Rate metrics (`http_req_failed`, and this project's per-endpoint `failed_<name>` metrics): `value` is
  already a 0–1 fraction; `passes` is confusingly the count where the metric was *truthy* — for a "failed"
  metric, that means `passes` = the failure count, not the success count.
- A metric's `thresholds` object is keyed by the threshold expression with a **boolean that means "was this
  breached"**, the opposite of "passed" — e.g. a passing `p(95)<250` (p95 actually 200ms) is reported as
  `false`, matching the CLI's own `✓`. `src/k6/parseSummary.ts` inverts this back to a plain `ok` boolean.
  This was caught by cross-checking the JSON against the CLI's own `✓`/`✗` output during development — worth
  re-verifying if you upgrade k6.

## What's not here yet

- **Standalone CLI mode** — same pipeline, driven by a script with its own `ANTHROPIC_API_KEY` instead of an
  MCP host, for CI use. The core (`src/k6/*`, `src/types.ts`) is already host-agnostic; this would add a
  thin agent loop on top.
- **JMeter export** — k6 is the primary engine (LLM-friendly JS, clean JSON output); a JMX export path could
  be added via `openapi-generator`'s JMeter backend for orgs standardized on JMeter.
- **Baseline diffing** — compare a run's `RunMetrics` against a saved baseline to flag regressions, for a CI
  gate.
- **OpenAPI/Postman ingestion** — deriving the request mix automatically from a spec instead of the host LLM
  inferring it from a description.
