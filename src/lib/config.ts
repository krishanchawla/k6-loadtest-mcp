import { readFileSync, writeFileSync, existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { k6LoadtestMcpHome } from "./home.js";

export interface Config {
  allowedHosts: string[];
  /**
   * Base URL of the loadtest-dashboard service to publish reports to (e.g.
   * "https://myvps.example.com/loadtest-dashboard"). Optional -- publish_report requires it, but
   * generating/running/parsing tests never does. Not set by default; add it yourself once a
   * dashboard is deployed. The write token is a secret and deliberately lives in the
   * K6_LOADTEST_DASHBOARD_TOKEN env var instead, not in this file.
   */
  dashboardUrl?: string;
}

const DEFAULT_CONFIG: Config = { allowedHosts: ["localhost", "127.0.0.1", "::1"] };

export function configPath(): string {
  return path.join(k6LoadtestMcpHome(), "config.json");
}

export function loadConfig(): Config {
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
            "add hosts by hand once you've confirmed you're authorized to load-test them. " +
            "dashboardUrl (optional) points publish_report at a deployed loadtest-dashboard instance.",
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
