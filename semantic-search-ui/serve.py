#!/usr/bin/env python3
"""Serve the semantic-search demo UI and proxy requests to the prototype API."""

import argparse
import http.client
import http.server
import json
import pathlib
import socketserver
import urllib.error
import urllib.parse
import urllib.request


ROOT = pathlib.Path(__file__).resolve().parent
ALLOWED_API_PATHS = frozenset({"/semantic-collections", "/health", "/version"})


def normalized_api_url(value):
    parsed = urllib.parse.urlsplit(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise argparse.ArgumentTypeError("API URL must start with http:// or https://")
    if parsed.query or parsed.fragment:
        raise argparse.ArgumentTypeError("API URL cannot contain a query or fragment")
    return value.rstrip("/")


def make_handler(api_url):
    class DemoHandler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=str(ROOT), **kwargs)

        def do_GET(self):
            parsed = urllib.parse.urlsplit(self.path)
            if parsed.path.startswith("/api/"):
                self.proxy_api(parsed)
                return
            if parsed.path == "/":
                self.path = "/index.html"
            super().do_GET()

        def proxy_api(self, request_url):
            api_path = request_url.path.removeprefix("/api")
            if api_path not in ALLOWED_API_PATHS:
                self.send_json(404, {"detail": "Unknown API route"})
                return

            upstream = f"{api_url}{api_path}"
            if request_url.query:
                upstream += f"?{request_url.query}"
            request = urllib.request.Request(
                upstream,
                headers={"Accept": "application/json", "User-Agent": "CMR-semantic-demo/1.0"},
            )
            try:
                with urllib.request.urlopen(request, timeout=45) as response:
                    body = response.read()
                    self.send_response(response.status)
                    self.send_header("Content-Type", response.headers.get_content_type())
                    self.send_header("Content-Length", str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)
            except urllib.error.HTTPError as error:
                body = error.read()
                self.send_response(error.code)
                self.send_header("Content-Type", error.headers.get("Content-Type", "application/json"))
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            except (urllib.error.URLError, TimeoutError, http.client.HTTPException) as error:
                self.send_json(502, {"detail": f"Could not reach semantic search: {error.reason if hasattr(error, 'reason') else error}"})

        def send_json(self, status, value):
            body = json.dumps(value).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, message, *args):
            print(f"[{self.log_date_time_string()}] {message % args}")

    return DemoHandler


class DemoServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-url", required=True, type=normalized_api_url,
                        help="semantic-search-app base URL (your tunnel URL)")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=4173, type=int)
    args = parser.parse_args()

    server = DemoServer((args.host, args.port), make_handler(args.api_url))
    print(f"CMR semantic search demo: http://{args.host}:{args.port}")
    print(f"Proxying API requests to: {args.api_url}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping demo server")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
