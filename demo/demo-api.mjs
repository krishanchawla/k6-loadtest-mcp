// Tiny dependency-free demo API for exercising the k6-loadtest-mcp pipeline end-to-end.
// Not meant to be realistic infra -- just enough behavior variance to produce an
// interesting load-test report: a fast endpoint, a slower one, and one that
// degrades (queues up) as concurrency increases, plus a low random error rate.
import { createServer } from "node:http";

const PORT = process.env.PORT ? Number(process.env.PORT) : 4000;
let inFlight = 0;

function jsonResponse(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
}

const server = createServer(async (req, res) => {
  inFlight++;
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);

    if (url.pathname === "/users" && req.method === "GET") {
      // Fast, cheap endpoint.
      await sleep(15 + Math.random() * 15);
      return jsonResponse(res, 200, { users: [{ id: 1, name: "Ada" }] });
    }

    if (url.pathname === "/reports" && req.method === "GET") {
      // Degrades under concurrency, simulating a shared-resource bottleneck
      // (e.g. a slow downstream DB), plus a small baseline error rate.
      const congestionPenaltyMs = inFlight * 8;
      await sleep(80 + Math.random() * 40 + congestionPenaltyMs);
      if (Math.random() < 0.02) {
        return jsonResponse(res, 500, { error: "report generation failed" });
      }
      return jsonResponse(res, 200, { report: "monthly", rows: 42 });
    }

    if (url.pathname === "/orders" && req.method === "POST") {
      await sleep(30 + Math.random() * 20);
      return jsonResponse(res, 201, { orderId: Math.floor(Math.random() * 100000) });
    }

    return jsonResponse(res, 404, { error: "not found" });
  } finally {
    inFlight--;
  }
});

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

server.listen(PORT, () => {
  console.log(`demo API listening on http://localhost:${PORT}`);
});
