"""Write a small .litematic used to test the build engine's harder paths.

The schematic is deliberately awkward in exactly two ways:

* it holds blocks whose state a click decides and no click at placement time can
  reach — a repeater at delay 3, a comparator in subtract mode, a lever left
  thrown — so the build has to stand at each one and click it round;
* it holds a container (a barrel) first, so the crouch is down when the clicking
  starts, which is the state the user's own build was in.

Usage: python tools/make_state_test.py "run/schematics/Auto cc state test.litematic"
"""

import gzip
import struct
import sys

# --- NBT ----------------------------------------------------------------

TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_LONG_ARRAY = 12


def enc_string(text):
    raw = text.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def enc_compound(entries):
    out = b""
    for type_id, name, payload in entries:
        out += bytes([type_id]) + enc_string(name) + payload
    return out + b"\x00"


def enc_int(value):
    return struct.pack(">i", value)


def enc_string_value(value):
    return enc_string(value)


def enc_list(type_id, payloads):
    out = bytes([type_id]) + struct.pack(">i", len(payloads))
    for payload in payloads:
        out += payload
    return out


def enc_long_array(values):
    out = struct.pack(">i", len(values))
    for value in values:
        out += struct.pack(">q", value & 0xFFFFFFFFFFFFFFFF)
    return out


def compound_value(entries):
    return enc_compound(entries)


def int_value(value):
    return enc_int(value)


# --- schematic ----------------------------------------------------------


def pack(indices, bits, volume):
    values = [0] * ((volume * bits + 63) // 64)
    mask = (1 << bits) - 1
    for index, value in enumerate(indices):
        value &= mask
        bit_index = index * bits
        start = bit_index >> 6
        offset = bit_index & 63
        end = ((index + 1) * bits - 1) >> 6
        if start == end:
            values[start] |= value << offset
        else:
            values[start] |= (value << offset) & 0xFFFFFFFFFFFFFFFF
            values[end] |= value >> (64 - offset)
    return values


def bits_for(palette_size):
    bits = 2
    while (1 << bits) < palette_size:
        bits += 1
    return bits


def main(path):
    width, height, length = 6, 2, 1

    def cell(name, properties=None):
        return (name, properties or {})

    stone = cell("minecraft:stone")
    rows = [
        # y = 0: the floor the rest needs to sit on.
        [stone] * width,
        # y = 1, left to right: a container to put the crouch down, a repeater
        # that must be clicked to delay 3, a spacer, a lever that must be
        # clicked on, a comparator that must be clicked into subtract mode, and
        # a hopper that the test stands in to see the builder step aside.
        [
            cell("minecraft:barrel", {"facing": "up", "open": "false"}),
            cell("minecraft:repeater", {"delay": "3", "facing": "north",
                                        "locked": "false", "powered": "false"}),
            stone,
            cell("minecraft:lever", {"face": "floor", "facing": "north", "powered": "true"}),
            cell("minecraft:comparator", {"facing": "north", "mode": "subtract",
                                          "powered": "false"}),
            cell("minecraft:hopper", {"enabled": "true", "facing": "down"}),
        ],
    ]

    palette = []
    index = {}

    def palette_id(entry):
        key = (entry[0], tuple(sorted(entry[1].items())))
        if key not in index:
            index[key] = len(palette)
            palette.append(entry)
        return index[key]

    palette.append(cell("minecraft:air"))
    index[(("minecraft:air"), ())] = 0

    indices = []
    for y in range(height):
        for z in range(length):
            for x in range(width):
                indices.append(palette_id(rows[y][x]))
    volume = width * height * length
    bits = bits_for(len(palette))

    palette_payloads = []
    for name, properties in palette:
        entries = [(TAG_STRING, "Name", enc_string_value(name))]
        if properties:
            entries.append((TAG_COMPOUND, "Properties", compound_value(
                [(TAG_STRING, key, enc_string_value(properties[key]))
                 for key in sorted(properties)])))
        palette_payloads.append(compound_value(entries))

    region = compound_value([
        (TAG_COMPOUND, "Position", compound_value(
            [(TAG_INT, "x", int_value(0)), (TAG_INT, "y", int_value(0)),
             (TAG_INT, "z", int_value(0))])),
        (TAG_COMPOUND, "Size", compound_value(
            [(TAG_INT, "x", int_value(width)), (TAG_INT, "y", int_value(height)),
             (TAG_INT, "z", int_value(length))])),
        (TAG_LIST, "BlockStatePalette", enc_list(TAG_COMPOUND, palette_payloads)),
        (TAG_LONG_ARRAY, "BlockStates", enc_long_array(pack(indices, bits, volume))),
        (TAG_LIST, "Entities", enc_list(TAG_COMPOUND, [])),
        (TAG_LIST, "TileEntities", enc_list(TAG_COMPOUND, [])),
    ])

    metadata = compound_value([
        (TAG_STRING, "Name", enc_string_value("Auto cc state test")),
        (TAG_STRING, "Author", enc_string_value("Auto cc")),
        (TAG_STRING, "Description", enc_string_value(
            "State stepping and stepping aside")),
        (TAG_INT, "RegionCount", int_value(1)),
        (TAG_INT, "TotalBlocks", int_value(volume)),
        (TAG_INT, "TotalVolume", int_value(volume)),
        (TAG_COMPOUND, "EnclosingSize", compound_value(
            [(TAG_INT, "x", int_value(width)), (TAG_INT, "y", int_value(height)),
             (TAG_INT, "z", int_value(length))])),
    ])

    root = compound_value([
        (TAG_INT, "MinecraftDataVersion", int_value(4671)),
        (TAG_INT, "Version", int_value(6)),
        (TAG_INT, "SubVersion", int_value(6)),
        (TAG_COMPOUND, "Metadata", metadata),
        (TAG_COMPOUND, "Regions", compound_value([(TAG_COMPOUND, "main", region)])),
    ])

    with gzip.open(path, "wb") as handle:
        handle.write(bytes([TAG_COMPOUND]) + enc_string("") + root)
    print("wrote %s — %dx%dx%d, %d palette entries, %d bits" %
          (path, width, height, length, len(palette), bits))


if __name__ == "__main__":
    main(sys.argv[1])
