#!/usr/bin/env python3
"""Helper for the Velež Semafor data file (docs/data/velez.json).

Every command that changes the file validates the result first and refuses
to write invalid data, so the app on the phone never receives a broken file.

  python3 tools/velez_data.py validate
  python3 tools/velez_data.py show
  python3 tools/velez_data.py upsert-news FILE        # object or list; merge by id, skip duplicate URLs
  python3 tools/velez_data.py upsert-match FILE       # object or list; partial update by id
  python3 tools/velez_data.py set-standings fk|rk FILE
  python3 tools/velez_data.py touch --by "provjera svaki sat" [--change "kratak opis promjene"]

FILE may be "-" to read JSON from stdin.
"""
import argparse
import datetime as dt
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "docs", "data", "velez.json")

KINDS = {"izvještaj", "najava", "transfer", "klub", "omladinci", "saopštenje", "obavijest"}
STATUSES = {"zakazano", "odigrano", "odgođeno", "slobodno"}
ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]{0,159}$")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
TIME_RE = re.compile(r"^\d{2}:\d{2}$")
ISO_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?Z$")
URL_RE = re.compile(r"^https?://", re.I)

NEWS_FIELDS = ["id", "club", "kind", "date", "title", "summary", "url", "source", "added"]
MATCH_FIELDS = ["id", "club", "comp", "round", "stage", "date", "time", "confirmed", "window", "home", "away",
                "velez", "venue", "status", "score", "ht", "goals", "top", "tv", "att", "note", "report",
                "source", "updated"]
MATCH_DEFAULTS = {"round": None, "date": None, "time": "", "confirmed": False, "window": "", "venue": "",
                  "score": None, "ht": None, "goals": [], "top": [], "tv": "", "att": None, "note": "",
                  "report": "", "source": ""}


def now_iso():
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def is_date(s):
    if not isinstance(s, str) or not DATE_RE.match(s):
        return False
    try:
        dt.date.fromisoformat(s)
        return True
    except ValueError:
        return False


def is_int(v):
    return isinstance(v, int) and not isinstance(v, bool)


def is_score(v):
    return isinstance(v, dict) and is_int(v.get("h")) and is_int(v.get("a")) and v["h"] >= 0 and v["a"] >= 0


def validate(data):
    """Return (errors, warnings) for the whole data object."""
    err, warn = [], []
    if not isinstance(data, dict):
        return ["Datoteka nije JSON objekat."], warn
    if data.get("schema") != 1:
        err.append("schema mora biti 1")
    meta = data.get("meta")
    if not isinstance(meta, dict):
        err.append("meta nedostaje")
    else:
        for k in ("lastCheck", "lastChange"):
            if meta.get(k) is not None and not (isinstance(meta[k], str) and ISO_RE.match(meta[k])):
                err.append(f"meta.{k} nije UTC ISO vrijeme (npr. 2026-10-22T14:05:00Z)")

    news = data.get("news")
    if not isinstance(news, list):
        err.append("news mora biti lista")
        news = []
    ids, urls = set(), {}
    for i, n in enumerate(news):
        where = f"news[{i}] {n.get('id') if isinstance(n, dict) else ''}"
        if not isinstance(n, dict):
            err.append(f"{where}: nije objekat")
            continue
        if not isinstance(n.get("id"), str) or not ID_RE.match(n["id"]):
            err.append(f"{where}: neispravan id (mala slova, brojke, crtica)")
        elif n["id"] in ids:
            err.append(f"{where}: dupli id")
        else:
            ids.add(n["id"])
        if n.get("club") not in ("fk", "rk", "oba"):
            err.append(f"{where}: club mora biti fk, rk ili oba")
        if n.get("kind") not in KINDS:
            err.append(f"{where}: kind mora biti jedan od {sorted(KINDS)}")
        if not is_date(n.get("date")):
            err.append(f"{where}: date mora biti YYYY-MM-DD")
        if not isinstance(n.get("title"), str) or not n["title"].strip():
            err.append(f"{where}: title je prazan")
        if not isinstance(n.get("summary", ""), str):
            err.append(f"{where}: summary mora biti tekst")
        u = n.get("url")
        if not isinstance(u, str) or not URL_RE.match(u):
            err.append(f"{where}: url mora počinjati sa http(s)://")
        else:
            key = u.rstrip("/").lower()
            if key in urls:
                err.append(f"{where}: isti url kao {urls[key]}")
            urls[key] = n.get("id")
        if not isinstance(n.get("source", ""), str):
            err.append(f"{where}: source mora biti tekst")
        if not (isinstance(n.get("added"), str) and ISO_RE.match(n["added"])):
            err.append(f"{where}: added mora biti UTC ISO vrijeme")
        extra = set(n) - set(NEWS_FIELDS)
        if extra:
            warn.append(f"{where}: nepoznata polja {sorted(extra)}")

    matches = data.get("matches")
    if not isinstance(matches, list):
        err.append("matches mora biti lista")
        matches = []
    mids = set()
    for i, m in enumerate(matches):
        where = f"matches[{i}] {m.get('id') if isinstance(m, dict) else ''}"
        if not isinstance(m, dict):
            err.append(f"{where}: nije objekat")
            continue
        if not isinstance(m.get("id"), str) or not ID_RE.match(m["id"]):
            err.append(f"{where}: neispravan id")
        elif m["id"] in mids:
            err.append(f"{where}: dupli id")
        else:
            mids.add(m["id"])
        missing = [k for k in MATCH_FIELDS if k not in m]
        if missing:
            err.append(f"{where}: nedostaju polja {missing}")
            continue
        if m["club"] not in ("fk", "rk"):
            err.append(f"{where}: club mora biti fk ili rk")
        if m["status"] not in STATUSES:
            err.append(f"{where}: status mora biti jedan od {sorted(STATUSES)}")
        if m["round"] is not None and not is_int(m["round"]):
            err.append(f"{where}: round mora biti broj ili null")
        if m["date"] is not None and not is_date(m["date"]):
            err.append(f"{where}: date mora biti YYYY-MM-DD ili null")
        if m["time"] != "" and not (isinstance(m["time"], str) and TIME_RE.match(m["time"])):
            err.append(f"{where}: time mora biti HH:MM ili prazno")
        if not isinstance(m["confirmed"], bool):
            err.append(f"{where}: confirmed mora biti true/false")
        if m["confirmed"] and m["status"] == "zakazano" and not (m["date"] and m["time"]):
            err.append(f"{where}: confirmed=true za zakazanu utakmicu traži i datum i satnicu")
        if m["velez"] not in ("home", "away"):
            err.append(f"{where}: velez mora biti home ili away")
        if m["status"] != "slobodno":
            if not (isinstance(m["home"], str) and m["home"].strip() and isinstance(m["away"], str) and m["away"].strip()):
                err.append(f"{where}: home i away moraju biti upisani")
            elif "Velež" not in (m["home"], m["away"]):
                warn.append(f"{where}: ni domaćin ni gost nije \"Velež\"")
            elif (m["home"] == "Velež") != (m["velez"] == "home"):
                err.append(f"{where}: polje velez se ne slaže sa home/away")
        for k in ("score", "ht"):
            if m[k] is not None and not is_score(m[k]):
                err.append(f"{where}: {k} mora biti {{\"h\":n,\"a\":n}} ili null")
        if m["status"] == "odigrano" and not is_score(m["score"]):
            err.append(f"{where}: odigrana utakmica mora imati score")
        if m["status"] == "zakazano" and m["score"] is not None:
            err.append(f"{where}: zakazana utakmica ne smije imati score (stavi status odigrano)")
        if not isinstance(m["goals"], list) or not isinstance(m["top"], list):
            err.append(f"{where}: goals i top moraju biti liste")
        else:
            for g in m["goals"]:
                if not (isinstance(g, dict) and isinstance(g.get("n"), str) and isinstance(g.get("m"), str) and g.get("t") in ("h", "a")):
                    err.append(f"{where}: gol mora biti {{\"n\":\"Prezime\",\"m\":\"58'\",\"t\":\"h\"|\"a\"}}")
                    break
            for g in m["top"]:
                if not (isinstance(g, dict) and isinstance(g.get("n"), str) and is_int(g.get("g")) and g.get("t") in ("h", "a")):
                    err.append(f"{where}: strijelac mora biti {{\"n\":\"Prezime\",\"g\":8,\"t\":\"h\"|\"a\"}}")
                    break
            if m["goals"] and is_score(m["score"]):
                h = sum(1 for g in m["goals"] if g.get("t") == "h")
                a = sum(1 for g in m["goals"] if g.get("t") == "a")
                if (h, a) != (m["score"]["h"], m["score"]["a"]):
                    warn.append(f"{where}: broj golova u listi ({h}:{a}) ne odgovara rezultatu")
        if m["att"] is not None and not is_int(m["att"]):
            err.append(f"{where}: att mora biti broj ili null")
        for k in ("comp", "stage", "window", "venue", "tv", "note", "report", "source", "updated"):
            if not isinstance(m[k], str):
                err.append(f"{where}: {k} mora biti tekst")
        for k in ("report", "source"):
            if m[k] and not URL_RE.match(m[k]):
                err.append(f"{where}: {k} mora biti link ili prazno")

    st = data.get("standings")
    if not isinstance(st, dict):
        err.append("standings mora biti objekat")
        st = {}
    for c in ("fk", "rk"):
        t = st.get(c)
        if not isinstance(t, dict):
            err.append(f"standings.{c} nedostaje")
            continue
        rows = t.get("rows")
        if not isinstance(rows, list) or not rows:
            err.append(f"standings.{c}.rows je prazno")
            continue
        if not any(isinstance(r, dict) and r.get("team") == "Velež" for r in rows):
            err.append(f"standings.{c}: nema reda \"Velež\"")
        win_pts = 3 if c == "fk" else 2
        for r in rows:
            if not isinstance(r, dict) or not all(is_int(r.get(k)) for k in ("pos", "p", "w", "d", "l", "pts")) or not isinstance(r.get("team"), str):
                err.append(f"standings.{c}: red {r} nema ispravne brojeve")
                continue
            if r["w"] + r["d"] + r["l"] != r["p"]:
                err.append(f"standings.{c} {r['team']}: P+N+I ({r['w']}+{r['d']}+{r['l']}) nije jednako broju utakmica {r['p']}")
            if r["w"] * win_pts + r["d"] != r["pts"]:
                warn.append(f"standings.{c} {r['team']}: bodovi {r['pts']} ne odgovaraju P/N (možda oduzeti bodovi?)")
            for k in ("gf", "ga"):
                if r.get(k) is not None and not is_int(r.get(k)):
                    err.append(f"standings.{c} {r['team']}: {k} mora biti broj ili null")
        if all(is_int(r.get("gf")) and is_int(r.get("ga")) for r in rows if isinstance(r, dict)):
            gf = sum(r["gf"] for r in rows)
            ga = sum(r["ga"] for r in rows)
            if gf != ga:
                warn.append(f"standings.{c}: zbir datih golova {gf} nije jednak zbiru primljenih {ga}")
    return err, warn


def load():
    with open(DATA, encoding="utf-8") as f:
        return json.load(f)


def sort_data(data):
    data["news"].sort(key=lambda n: (n.get("date") or "", n.get("added") or ""), reverse=True)
    data["matches"].sort(key=lambda m: (m.get("club", ""), m.get("date") or "9999-99-99", m.get("round") or 99, m.get("id", "")))


def save(data):
    sort_data(data)
    err, warn = validate(data)
    for w in warn:
        print("upozorenje:", w)
    if err:
        for e in err:
            print("GREŠKA:", e, file=sys.stderr)
        print("Ništa nije zapisano.", file=sys.stderr)
        sys.exit(1)
    tmp = DATA + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")
    os.replace(tmp, DATA)
    print("Zapisano:", os.path.relpath(DATA, ROOT))


def read_input(path):
    raw = sys.stdin.read() if path == "-" else open(path, encoding="utf-8").read()
    obj = json.loads(raw)
    return obj if isinstance(obj, list) else [obj]


def cmd_validate(_):
    data = load()
    err, warn = validate(data)
    for w in warn:
        print("upozorenje:", w)
    for e in err:
        print("GREŠKA:", e)
    print(f"{len(data.get('news', []))} vijesti, {len(data.get('matches', []))} utakmica · "
          f"{'ISPRAVNO' if not err else str(len(err)) + ' grešaka'}")
    sys.exit(1 if err else 0)


def cmd_show(_):
    data = load()
    meta = data.get("meta", {})
    print("meta:", json.dumps(meta, ensure_ascii=False))
    for c in ("fk", "rk"):
        ms = [m for m in data["matches"] if m["club"] == c]
        up = [m for m in ms if m["status"] in ("zakazano", "odgođeno")]
        done = sorted([m for m in ms if m["status"] == "odigrano"], key=lambda m: m.get("date") or "")
        print(f"\n== {c.upper()} — zadnji rezultati")
        for m in done[-3:]:
            print(f"  {m['date']} {m['home']} {m['score']['h']}:{m['score']['a']} {m['away']}  [{m['id']}]")
        print(f"== {c.upper()} — sljedeće")
        for m in up[:4]:
            when = f"{m['date'] or '?'} {m['time'] or ''}".strip()
            print(f"  {m['stage']}: {m['home']} – {m['away']} · {when} · {m['status']}{' · potvrđeno' if m['confirmed'] else ''}  [{m['id']}]")
    print("\n== zadnje vijesti")
    for n in data["news"][:8]:
        print(f"  {n['date']} {n['club']} {n['title'][:80]}  [{n['id']}]")


def cmd_upsert_news(a):
    data = load()
    by_id = {n["id"]: n for n in data["news"]}
    by_url = {n["url"].rstrip("/").lower(): n["id"] for n in data["news"] if isinstance(n.get("url"), str)}
    added = updated = skipped = 0
    for item in read_input(a.file):
        if not isinstance(item, dict) or not item.get("id"):
            print("GREŠKA: svaka vijest treba id", file=sys.stderr)
            sys.exit(1)
        if item["id"] in by_id:
            by_id[item["id"]].update(item)
            updated += 1
            continue
        key = str(item.get("url", "")).rstrip("/").lower()
        if key and key in by_url:
            print(f"preskačem {item['id']}: isti link već postoji kao {by_url[key]}")
            skipped += 1
            continue
        item.setdefault("summary", "")
        item.setdefault("added", now_iso())
        new = {k: item[k] for k in NEWS_FIELDS if k in item}
        new.update({k: v for k, v in item.items() if k not in new})
        data["news"].append(new)
        by_id[new["id"]] = new
        if key:
            by_url[key] = new["id"]
        added += 1
    print(f"vijesti: {added} novih, {updated} ažurirano, {skipped} preskočeno")
    save(data)


def cmd_upsert_match(a):
    data = load()
    by_id = {m["id"]: m for m in data["matches"]}
    added = updated = 0
    for item in read_input(a.file):
        if not isinstance(item, dict) or not item.get("id"):
            print("GREŠKA: svaka utakmica treba id", file=sys.stderr)
            sys.exit(1)
        item.setdefault("updated", now_iso())
        if item["id"] in by_id:
            by_id[item["id"]].update(item)
            updated += 1
        else:
            new = dict(MATCH_DEFAULTS)
            new.update(item)
            data["matches"].append({k: new[k] for k in MATCH_FIELDS if k in new} | {k: v for k, v in new.items() if k not in MATCH_FIELDS})
            by_id[item["id"]] = new
            added += 1
    print(f"utakmice: {added} novih, {updated} ažurirano")
    save(data)


def cmd_set_standings(a):
    data = load()
    obj = read_input(a.file)[0]
    if "rows" not in obj:
        print("GREŠKA: tabela treba polje rows", file=sys.stderr)
        sys.exit(1)
    old = data["standings"].get(a.club, {})
    new = dict(old)
    new.update(obj)
    new.setdefault("season", "2026/27")
    new["updated"] = obj.get("updated") or now_iso()
    data["standings"][a.club] = new
    save(data)


def cmd_touch(a):
    data = load()
    meta = data.setdefault("meta", {})
    t = now_iso()
    meta["lastCheck"] = t
    if a.by:
        meta["by"] = a.by
    if a.change:
        meta["lastChange"] = t
        meta["lastChangeText"] = a.change.strip()[:200]
    save(data)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("validate").set_defaults(fn=cmd_validate)
    sub.add_parser("show").set_defaults(fn=cmd_show)
    s = sub.add_parser("upsert-news")
    s.add_argument("file")
    s.set_defaults(fn=cmd_upsert_news)
    s = sub.add_parser("upsert-match")
    s.add_argument("file")
    s.set_defaults(fn=cmd_upsert_match)
    s = sub.add_parser("set-standings")
    s.add_argument("club", choices=["fk", "rk"])
    s.add_argument("file")
    s.set_defaults(fn=cmd_set_standings)
    s = sub.add_parser("touch")
    s.add_argument("--by", default="")
    s.add_argument("--change", default="")
    s.set_defaults(fn=cmd_touch)
    a = p.parse_args()
    a.fn(a)


if __name__ == "__main__":
    main()
