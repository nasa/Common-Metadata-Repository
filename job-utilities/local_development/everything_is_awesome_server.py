# ###########################################################################################100
"""
🎵 Everything Is Awesome Server
A test server to always return something good, you get a 200 with every call and verb, this
server is Awesome!!!
"""
# ###########################################################################################100

from typing import Callable
from http.server import BaseHTTPRequestHandler, HTTPServer

# ############################################################################80

class Awesome(BaseHTTPRequestHandler):
    """ Create a web server that returns 200 for every call, always """
    def _handle(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"status": "success", "message": "everything is awesome"}')

    do_GET: Callable[..., None] =_handle
    do_POST: Callable[..., None] =_handle
    do_PUT: Callable[..., None] =_handle
    do_DELETE: Callable[..., None] =_handle

# ############################################################################80

if __name__ == '__main__':
    HTTPServer(('', 3006), Awesome).serve_forever()
