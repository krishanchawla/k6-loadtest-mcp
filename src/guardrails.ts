import { readFileSync, writeFileSync, existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { k6LoadtestMcpHome } from "./lib/home.js";

interface Config {
  allowedHosts: string[];
}

const DEFAULT_CONFIG: Config = { allowedHosts: ["localhost", "127.0.0.1", "::1"] };

function configPath(): string {
  return path.join(k6LoadtestMcpHome(), "config.json");
}

function loadConfig(): Config {
  const p = configPath();
  if (!existsSync(p)) {
    // First run: seed a config file in a place the user can actually find and edit,
    // regardless of whether this server was git-cloned, npm-installed, or run via
    // `npx github:...` (whose install directory is an ephemeral cache path).
    mkdirSync(path.dirname(p), { recursive: true });
    writeFileSync(
      p,
      JSON.stringify(
        {
          _comment:
            "Load tests only run against hosts listed here. Not editable by the agent's own tools — " +
            "add hosts by hand once you've confirmed you're authorized to load-test them.",
          ...DEFAULT_CONFIG,
        },
        null,
        2
      ),
      "utf-8"
    );
    return DEFAULT_CONFIG;
  }
  try {
    const raw = readFileSync(p, "utf-8");
    return JSON.parse(raw);
  } catch (err) {
    throw new Error(`Could not parse guardrail config at ${p}. Refusing to run any load test without it. (${(err as Error).message})`);
  }
}

/**
 * Loads never expand this list themselves — it's read-only from the tools' point of view.
 * A host is allowed if it matches exactly, or matches a leading-wildcard suffix like "*.example.com".
 */
export function assertTargetAllowed(baseUrl: string): void {
  const { allowedHosts } = loadConfig();
  const hostname = new URL(baseUrl).hostname.toLowerCase();

  const ok = allowedHosts.some((entry) => {
    const e = entry.toLowerCase();
    if (e.startsWith("*.")) {
      return hostname === e.slice(2) || hostname.endsWith(e.slice(1));
    }
    return hostname === e;
  });

  if (!ok) {
    throw new Error(
      `Target host "${hostname}" is not in the allowlist (${configPath()}). ` +
        `Load-testing a host you don't control or haven't been authorized to test can look like a denial-of-service attack. ` +
        `If you own/are authorized to test this host, add it to "allowedHosts" in that file yourself, then retry.`
    );
  }
}
