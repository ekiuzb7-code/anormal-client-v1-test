"""Dump a .litematic palette and block counts, for picking build fixtures.

Reads the gzipped NBT by hand: the format is small enough that a reader is a few
dozen lines, and having one means a test schematic can be written as easily as
read. Usage: python tools/litematic_dump.py <file.litematic>
"""

import gzip
import struct
import sys
from collections import Counter


def read_tag(buf, i):
    tag = buf[i]
    i += 1
    if tag == 0:
        return None, i
    name_len = struct.unpack_from(">H", buf, i)[0]
    i += 2
    name = buf[i:i + name_len].decode("utf-8", "replace")
    i += name_len
    value, i = read_payload(buf, i, tag)
    return (name, tag, value), i


def read_payload(buf, i, tag):
    if tag == 1:
        return struct.unpack_from(">b", buf, i)[0], i + 1
    if tag == 2:
        return struct.unpack_from(">h", buf, i)[0], i + 2
    if tag == 3:
        return struct.unpack_from(">i", buf, i)[0], i + 4
    if tag == 4:
        return struct.unpack_from(">q", buf, i)[0], i + 8
    if tag == 5:
        return struct.unpack_from(">f", buf, i)[0], i + 4
    if tag == 6:
        return struct.unpack_from(">d", buf, i)[0], i + 8
    if tag == 7:
        n = struct.unpack_from(">i", buf, i)[0]
        return list(buf[i + 4:i + 4 + n]), i + 4 + n
    if tag == 8:
        n = struct.unpack_from(">H", buf, i)[0]
        return buf[i + 2:i + 2 + n].decode("utf-8", "replace"), i + 2 + n
    if tag == 9:
        item_tag = buf[i]
        n = struct.unpack_from(">i", buf, i + 1)[0]
        i += 5
        items = []
        for _ in range(n):
            value, i = read_payload(buf, i, item_tag)
            items.append(value)
        return items, i
    if tag == 10:
        out = {}
        while True:
            entry, i = read_tag(buf, i)
            if entry is None:
                return out, i
            name, _tag, value = entry
            out[name] = value
    if tag == 11:
        n = struct.unpack_from(">i", buf, i)[0]
        return list(struct.unpack_from(">%di" % n, buf, i + 4)), i + 4 + 4 * n
    if tag == 12:
        n = struct.unpack_from(">i", buf, i)[0]
        return list(struct.unpack_from(">%dq" % n, buf, i + 4)), i + 4 + 8 * n
    raise ValueError("unknown tag %d" % tag)


def main(path):
    with gzip.open(path, "rb") as handle:
        buf = handle.read()
    entry, _ = read_tag(buf, 0)
    root = entry[2]
    print("data version:", root.get("MinecraftDataVersion"))
    regions = root.get("Regions", {})
    for name, region in regions.items():
        palette = [
            render(entry) for entry in region.get("BlockStatePalette", [])
        ]
        states = region.get("BlockStates", [])
        size = region.get("Size", {})
        print("region %s  size %sx%sx%s  palette %d" % (
            name, size.get("x"), size.get("y"), size.get("z"), len(palette)))
        counts = unpack(palette, states, size)
        for label, count in sorted(counts.items(), key=lambda kv: -kv[1]):
            print("  %5d  %s" % (count, label))


def render(entry):
    name = entry.get("Name", "?")
    properties = entry.get("Properties", {})
    if not properties:
        return name
    inner = ",".join("%s=%s" % (k, properties[k]) for k in sorted(properties))
    return "%s[%s]" % (name, inner)


def unpack(palette, states, size):
    bits = max(2, (len(palette) - 1).bit_length())
    per_long = 64 // bits
    mask = (1 << bits) - 1
    volume = size.get("x", 0) * size.get("y", 0) * size.get("z", 0)
    counts = Counter()
    for index in range(volume):
        long_index = index // per_long
        if long_index >= len(states):
            break
        offset = (index % per_long) * bits
        value = (states[long_index] >> offset) & mask
        if value < len(palette):
            counts[palette[value]] += 1
    return counts


if __name__ == "__main__":
    main(sys.argv[1])
