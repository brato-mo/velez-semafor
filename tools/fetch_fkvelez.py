#!/usr/bin/env python3
"""Adds new posts from the official site fkvelez.ba to docs/data/velez.json.

Runs in GitHub Actions every 20 minutes (see .github/workflows/news.yml), so a new
official post reaches the phone quickly. It only adds the headline, date and link;
the hourly Claude check later writes the short summary ("summary" is left empty).

  python3 tools/fetch_fkvelez.py [--msg-file FILE]
"""
import argparse
import datetime as dt
import html
import json
import os
import re
import sys
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import velez_data as vd  # noqa: E402

API = "https://fkvelez.ba/wp-json/wp/v2/posts?per_page=20&_fields=id,date,link,title"
UA = "VelezSemafor/1.0 (+https://github.com/brato-mo/velez-semafor)"
YOUTH = re.compile(r"omladin|junior|kadet|pionir|\bu-?1[0-9]\b|škol", re.I)
TRANSFER = re.compile(r"novi fudbaler|nije više|napušta|potpisao|pojačanj|ugovor|posudb|odlazi", re.I)


def slug_id(url):
    path = urllib.parse.urlparse(url).path.strip("/").split("/")[-1].lower()
    s = re.sub(r"[^a-z0-9-]+", "-", path).strip("-")
    return ("fkvelez-" + s)[:150].rstrip("-")


def kind_for(title):
    up = title.upper()
    if up.startswith(("IZVJEŠTAJ", "IZVJESTAJ")):
        return "omladinci" if YOUTH.search(title) else "izvještaj"
    if up.startswith("NAJAVA"):
        return "omladinci" if YOUTH.search(title) else "najava"
    if any(w in up for w in ("SAOPŠTENJE", "SAOPĆENJE", "SAOPSTENJE")):
        return "saopštenje"
    if up.startswith(("SLUŽBENO", "SLUZBENO")) and TRANSFER.search(title):
        return "transfer"
    if YOUTH.search(title):
        return "omladinci"
    return "klub"


def plural(n, one, few, many):
    a, b = n % 10, n % 100
    if a == 1 and b != 11:
        return one
    if 2 <= a <= 4 and not 12 <= b <= 14:
        return few
    return many


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--msg-file", default="")
    args = ap.parse_args()
    req = urllib.request.Request(API, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        posts = json.loads(r.read().decode("utf-8"))
    if not isinstance(posts, list):
        print("Neočekivan odgovor sa fkvelez.ba", file=sys.stderr)
        sys.exit(1)
    data = vd.load()
    known_urls = {str(n.get("url", "")).rstrip("/").lower() for n in data["news"]}
    known_ids = {n["id"] for n in data["news"]}
    cutoff = (dt.date.today() - dt.timedelta(days=14)).isoformat()
    added = []
    for p in posts:
        url = str(p.get("link") or "")
        raw_title = (p.get("title") or {}).get("rendered", "") if isinstance(p.get("title"), dict) else ""
        title = html.unescape(re.sub(r"<[^>]+>", "", raw_title)).strip()
        date = str(p.get("date") or "")[:10]
        if not url.startswith("https://fkvelez.ba/") or not title or not vd.is_date(date) or date < cutoff:
            continue
        key = url.rstrip("/").lower()
        nid = slug_id(url)
        if key in known_urls or nid in known_ids:
            continue
        item = {"id": nid, "club": "fk", "kind": kind_for(title), "date": date, "title": title,
                "summary": "", "url": url, "source": "fkvelez.ba", "added": vd.now_iso()}
        data["news"].append(item)
        known_urls.add(key)
        known_ids.add(nid)
        added.append(item)
    if not added:
        print("Nema novih objava na fkvelez.ba.")
        return
    meta = data.setdefault("meta", {})
    meta["lastChange"] = vd.now_iso()
    if len(added) == 1:
        meta["lastChangeText"] = ("Nova vijest: " + added[0]["title"])[:200]
    else:
        meta["lastChangeText"] = f"{len(added)} {plural(len(added), 'nova vijest', 'nove vijesti', 'novih vijesti')} sa fkvelez.ba"
    vd.save(data)
    msg = "Vijesti sa fkvelez.ba: " + "; ".join(a["title"] for a in added)
    print(msg)
    if args.msg_file:
        with open(args.msg_file, "w", encoding="utf-8") as f:
            f.write(msg[:300] + "\n")


if __name__ == "__main__":
    main()
