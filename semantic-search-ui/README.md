# CMR semantic search demo UI

A dependency-free local interface for presenting `semantic-search-app`. The included Python server
serves the static UI and proxies its API requests, avoiding browser CORS restrictions and keeping
the tunnel URL out of browser code.

## Run it

From the repository root, pass the base URL of the tunnel serving `semantic-search-app`:

```bash
python3 semantic-search-ui/serve.py --api-url https://YOUR-TUNNEL.example.com
```

Open <http://127.0.0.1:4173>. Use `--port` or `--host` to change the local listener. For example, to
let another device on the same trusted network view the demo:

```bash
python3 semantic-search-ui/serve.py \
  --api-url https://YOUR-TUNNEL.example.com \
  --host 0.0.0.0 --port 8081
```

The target must expose `GET /version`, `GET /health`, and `GET /semantic-collections`. Stop the
local server with Ctrl-C. No npm install, build step, or API credentials are required.
