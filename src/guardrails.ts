import { loadConfig, configPath } from "./lib/config.js";

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
