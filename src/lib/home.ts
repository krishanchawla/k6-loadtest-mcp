import { homedir } from "node:os";
import path from "node:path";

/**
 * Where per-user state (guardrail config, run artifacts) lives. Deliberately NOT inside the
 * installed package directory: if someone installs this via `npx github:owner/k6-loadtest-mcp`, that
 * directory is an ephemeral npm cache path they can't easily find to edit. `~/.k6-loadtest-mcp` (or
 * `K6_LOADTEST_MCP_HOME` override) is stable and predictable regardless of install method.
 */
export function k6LoadtestMcpHome(): string {
  return process.env.K6_LOADTEST_MCP_HOME ?? path.join(homedir(), ".k6-loadtest-mcp");
}
