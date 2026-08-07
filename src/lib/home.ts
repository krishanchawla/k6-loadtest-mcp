import { homedir } from "node:os";
import path from "node:path";

/**
 * Where per-user state (guardrail config, run artifacts) lives. Deliberately NOT inside the
 * installed package directory: if someone installs this via `npx github:owner/perf-agent`, that
 * directory is an ephemeral npm cache path they can't easily find to edit. `~/.perf-agent` (or
 * `PERF_AGENT_HOME` override) is stable and predictable regardless of install method.
 */
export function perfAgentHome(): string {
  return process.env.PERF_AGENT_HOME ?? path.join(homedir(), ".perf-agent");
}
