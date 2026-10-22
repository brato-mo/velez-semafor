#!/usr/bin/env python3
"""Smoke test for docs/index.html in headless Chromium (Playwright).

Serves docs/ on localhost, opens the page the way the phone does (also with a
fake Android bridge, offline with and without saved data) and fails on any
JavaScript error, horizontal overflow or missing content.

  python3 tools/smoke_test.py [--shots DIR]
"""
import argparse
import functools
import http.server
import json
import os
import socketserver
import sys
import threading

from playwright.sync_api import sync_playwright

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")

FAKE_BRIDGE = """
(() => {
  const st = { versionCode: 1, versionName: '1.0', sdk: 35, notif: 'off', battery: 'limited',
               lastBg: new Date(Date.now() - 20 * 60e3).toISOString(), lastBgText: 'nema novih vijesti',
               s: { news: true, results: true, reminder: true, morning: true, morningTime: '07:00' } };
  window.__calls = [];
  window.VelezApp = {
    state: () => JSON.stringify(st),
    set: (k, v) => { window.__calls.push(['set', k, v]); st.s[k] = (v === 'true' ? true : v === 'false' ? false : v); return JSON.stringify(st); },
    ready: (v) => { window.__calls.push(['ready', v]); },
    requestNotifications: () => { window.__calls.push(['notif']); st.notif = 'on'; setTimeout(() => window.VS && window.VS.native(), 10); },
    test: () => { window.__calls.push(['test']); return st.notif === 'on' ? 'ok' : 'off'; },
    checkNow: () => { window.__calls.push(['check']); setTimeout(() => window.VS && window.VS.checked('nema novih vijesti'), 30); },
    battery: () => { window.__calls.push(['battery']); }
  };
})();
"""


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a):
        pass


def serve():
    handler = functools.partial(Quiet, directory=DOCS)
    httpd = socketserver.TCPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    return httpd, f"http://127.0.0.1:{httpd.server_address[1]}/index.html"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--shots", default="")
    args = ap.parse_args()
    if args.shots:
        os.makedirs(args.shots, exist_ok=True)
    httpd, url = serve()
    problems = []
    data = json.load(open(os.path.join(DOCS, "data", "velez.json"), encoding="utf-8"))

    with sync_playwright() as p:
        browser = p.chromium.launch()

        def page_for(name, w, h, scheme, bridge=False, offline=False, ctx=None):
            own = ctx is None
            if own:
                ctx = browser.new_context(viewport={"width": w, "height": h}, color_scheme=scheme)
            pg = ctx.new_page()
            pg.on("pageerror", lambda e: problems.append(f"{name}: JS greška: {e}"))
            pg.on("console", lambda m: problems.append(f"{name}: console.{m.type}: {m.text}") if m.type == "error" and "ERR_" not in m.text and "Failed to load resource" not in m.text else None)

            def route(r):
                u = r.request.url
                if not u.startswith("http://127.0.0.1"):
                    return r.abort()
                if offline and "/data/" in u:
                    return r.abort()
                return r.continue_()
            pg.route("**/*", route)
            if bridge:
                pg.add_init_script(FAKE_BRIDGE)
            return ctx, pg, own

        def check(name, pg, must=()):
            pg.wait_for_timeout(700)
            if pg.evaluate("document.documentElement.scrollWidth > document.documentElement.clientWidth + 1"):
                problems.append(f"{name}: stranica je šira od ekrana (horizontalni scroll)")
            body = pg.inner_text("body")
            low = body.lower()  # innerText follows CSS text-transform (uppercase headings)
            for t in must:
                if t.lower() not in low:
                    problems.append(f"{name}: nedostaje tekst {t!r}")
            if args.shots:
                pg.screenshot(path=os.path.join(args.shots, f"{name}.png"), full_page=True)
            return body

        # 1. obični pregled, telefon, svijetla tema
        ctx, pg, _ = page_for("telefon-pregled", 400, 860, "light")
        pg.goto(url)
        nxt = [m for m in data["matches"] if m["club"] == "fk" and m["status"] == "zakazano"]
        check("telefon-pregled", pg, must=["Dalje u rasporedu", "Najnovije vijesti", "Velež"])
        # 2. utakmice / rezultati, tamna tema
        pg.click("[data-tab=utakmice]")
        pg.click("#sub-rezultati")
        first = pg.query_selector(".rs details summary")
        if first:
            first.click()
        else:
            problems.append("rezultati: nema nijedne odigrane utakmice")
        has_fk_scorers = any(m["club"] == "fk" and m["status"] == "odigrano" and any(g.get("t") == ("h" if m["velez"] == "home" else "a") for g in m.get("goals") or []) for m in data["matches"])
        check("telefon-rezultati", pg, must=["Rezultati"] + (["Strijelci sezone"] if has_fk_scorers else []))
        # same view in dark mode
        ctxd, pgd, _ = page_for("telefon-rezultati-tamno", 400, 860, "dark")
        pgd.goto(url)
        pgd.click("[data-tab=utakmice]")
        pgd.click("#sub-rezultati")
        check("telefon-rezultati-tamno", pgd, must=["Rezultati"])
        ctxd.close()
        pg.click("[data-tab=tabela]")
        check("telefon-tabela", pg, must=["Tabela"])
        pg.click("[data-tab=vijesti]")
        check("telefon-vijesti", pg, must=["Vijesti"])
        # 3. bez interneta, sa sačuvanim podacima (isti kontekst => isti localStorage)
        _, pg2, _ = page_for("bez-interneta-kes", 400, 860, "light", offline=True, ctx=ctx)
        pg2.goto(url)
        body = check("bez-interneta-kes", pg2, must=["Bez veze", "Dalje u rasporedu"])
        ctx.close()
        # 4. bez interneta i bez sačuvanih podataka
        ctx, pg, _ = page_for("bez-interneta-prazno", 400, 860, "dark", offline=True)
        pg.goto(url)
        check("bez-interneta-prazno", pg, must=["Podaci se ne mogu učitati", "Pokušaj ponovo"])
        ctx.close()
        # 5. Android most: postavke, uključivanje obavijesti, probna obavijest, prekidači
        ctx, pg, _ = page_for("android-postavke", 400, 860, "dark", bridge=True)
        pg.goto(url)
        check("android-pregled", pg, must=["Obavijesti su isključene", "Dalje u rasporedu"])
        pg.click("#open-settings")
        check("android-postavke", pg, must=["Obavijesti na telefonu", "Šta je novo", "Jutarnji pregled"])
        pg.click("[data-act=notif]")
        pg.wait_for_timeout(200)
        check("android-postavke-ukljuceno", pg, must=["Obavijesti su uključene"])
        pg.click("[data-act=test]")
        pg.uncheck("input[data-set=reminder]", force=True)
        pg.select_option("select[data-set=morningTime]", "06:30")
        pg.click("[data-act=check]")
        pg.wait_for_timeout(400)
        calls = pg.evaluate("window.__calls")
        kinds = [c[0] for c in calls]
        for want in ("ready", "notif", "test", "set", "check"):
            if want not in kinds:
                problems.append(f"android: most nije pozvan za {want!r} (pozivi: {calls})")
        ready = [c for c in calls if c[0] == "ready"]
        meta_v = pg.evaluate("document.querySelector('meta[name=velez-version]').content")
        if not ready or ready[0][1] != meta_v:
            problems.append(f"android: ready() nije javio verziju {meta_v} ({ready})")
        if ["set", "reminder", "false"] not in calls or ["set", "morningTime", "06:30"] not in calls:
            problems.append(f"android: postavke nisu poslane ({calls})")
        ok = pg.evaluate("window.VS && window.VS.back() === true")
        if not ok:
            problems.append("android: VS.back() nije vratio na pregled")
        ctx.close()
        # 6. široki ekran
        ctx, pg, _ = page_for("desktop", 1100, 900, "light")
        pg.goto(url)
        check("desktop", pg, must=["Fudbal", "Rukomet"])
        ctx.close()
        browser.close()
    httpd.shutdown()
    if problems:
        print("\n".join("PROBLEM: " + p for p in problems))
        sys.exit(1)
    print("Smoke test prošao: 6 scenarija bez grešaka.")


if __name__ == "__main__":
    main()
