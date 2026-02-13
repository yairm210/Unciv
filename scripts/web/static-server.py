#!/usr/bin/env python3
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer


def main() -> None:
    parser = argparse.ArgumentParser(description="Threaded static file server for web CI")
    parser.add_argument("--port", type=int, default=18080)
    parser.add_argument("--dir", dest="directory", default="web/build/dist")
    args = parser.parse_args()

    handler = partial(SimpleHTTPRequestHandler, directory=args.directory)
    server = ThreadingHTTPServer(("0.0.0.0", args.port), handler)
    server.serve_forever()


if __name__ == "__main__":
    main()
