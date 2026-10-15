#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Generates the remaining Uz/Ru translation entries for every module setting
name and every ModeSetting option, using the base-word table in tr_part1.py.

V-prefixed settings ("V1 Place Range") are produced automatically by
translating the base name once and re-attaching the prefix, so the table only
has to contain the base words.
"""
import io
import os
import re
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__)))
from tr_part1 import PART1  # noqa: E402
from tr_part2 import PART2  # noqa: E402
from tr_part3 import PART3  # noqa: E402
from tr_part4 import PART4  # noqa: E402
from tr_part5 import PART5  # noqa: E402
from tr_part6 import PART6  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
IMPL = os.path.join(ROOT, "src", "main", "java", "com", "anormal", "client", "module", "impl")
UZ = os.path.join(ROOT, "src", "main", "java", "com", "anormal", "client", "i18n", "Uz.java")
RU = os.path.join(ROOT, "src", "main", "java", "com", "anormal", "client", "i18n", "Ru.java")

BASE = {en: (uz, ru) for en, uz, ru in PART1}
for _en, _uz, _ru in PART2 + PART3 + PART4 + PART5 + PART6:
    BASE.setdefault(_en, (_uz, _ru))
PREFIX = re.compile(r"^(V[1-4])\s+(.+)$")


def keys_of(path):
    s = io.open(path, encoding="utf-8").read()
    return {k for k, _ in re.findall(r'\{"((?:[^"\\]|\\.)*)",\s*"((?:[^"\\]|\\.)*)"\}', s)}


def translate(text):
    """@return (uz, ru) or None."""
    hit = BASE.get(text)
    if hit:
        return hit
    m = PREFIX.match(text)
    if m:
        base = BASE.get(m.group(2))
        if base:
            return (m.group(1) + " " + base[0], m.group(1) + " " + base[1])
    return None


def collect():
    """Every setting name and mode option used by a module."""
    names, options = set(), set()
    for root, _d, files in os.walk(IMPL):
        for f in files:
            if not f.endswith(".java"):
                continue
            s = io.open(os.path.join(root, f), encoding="utf-8").read()
            if "extends Module" not in s:
                continue
            for m in re.finditer(r'new\s+\w*Setting(?:<[^>]*>)?\(\s*"([^"]+)"\s*,\s*"([^"]+)"', s):
                names.add(m.group(1))
                names.add(m.group(2))
            for m in re.finditer(r'new ModeSetting\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,([^)]*)\)', s):
                names.add(m.group(1))
                options.update(re.findall(r'"([^"]+)"', m.group(4)))
    return names, options


def esc(x):
    return x.replace("\\", "\\\\").replace('"', '\\"')


def main():
    names, options = collect()
    wanted = sorted(names | options)

    for path, idx in ((UZ, 0), (RU, 1)):
        existing = keys_of(path)
        lines, done, unknown = [], 0, []
        for text in wanted:
            if text in existing:
                continue
            tr = translate(text)
            if tr is None:
                unknown.append(text)
                continue
            lines.append('            {{"{}", "{}"}},'.format(esc(text), esc(tr[idx])))
            done += 1

        s = io.open(path, encoding="utf-8").read()
        marker = "            // ---------------- Module names ----------------"
        block = ("            // ---------------- Setting names / mode options ----------------\n"
                 + "\n".join(lines) + "\n\n")
        s = s.replace(marker, block + marker, 1)
        io.open(path, "w", encoding="utf-8").write(s)
        print("{}: +{} yangi yozuv".format(os.path.basename(path), done))

    # Report anything the table does not cover.
    missing = sorted({t for t in wanted if translate(t) is None})
    if missing:
        print("\n=== TARJIMASI TOPILMAGAN ({} ta) ===".format(len(missing)))
        for m in missing:
            print("   ", m)
    else:
        print("\nBarcha sozlama nomlari va opsiyalar qamrab olindi.")


if __name__ == "__main__":
    main()
