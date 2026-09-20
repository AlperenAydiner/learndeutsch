"""
Gelistirme icin statik dosya sunucusu.

python -m http.server onbellek basligi gondermedigi icin tarayici
sezgisel onbellekleme yapiyor ve CSS/JS degisikligi ekrana yansimiyor.
Bu sunucu her yanita no-store koyar: kaydet, yenile, gor.

Yayinda kullanilmaz; Vercel kendi basliklarini gonderir.
"""

import sys
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer


class NoCacheHandler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store, must-revalidate")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def log_message(self, fmt, *args):
        # 200'leri susturup yalnizca hatalari goster.
        if args and str(args[1]).startswith(("4", "5")):
            super().log_message(fmt, *args)


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5500
    directory = sys.argv[2] if len(sys.argv) > 2 else "frontend"

    handler = partial(NoCacheHandler, directory=directory)
    with ThreadingHTTPServer(("127.0.0.1", port), handler) as httpd:
        print(f"Gelistirme sunucusu: http://localhost:{port}  ({directory}/)")
        print("Onbellek kapali - kaydet, yenile, gor.")
        httpd.serve_forever()


if __name__ == "__main__":
    main()
