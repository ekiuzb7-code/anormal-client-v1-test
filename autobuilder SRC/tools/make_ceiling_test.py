"""Write a small .litematic used to test building up under a low ceiling.

One block on the ground, and one five blocks above it — nothing in between and
nothing beside either. The high block cannot be reached from the ground and has
no standable spot next to it, so the only way to it is a tower. The test world
puts a roof four blocks up, which is exactly the shape of the build the tower
used to hop at for ever: enough room to leave the ground, not enough to land a
block under.

Usage: python tools/make_ceiling_test.py "run/schematics/Auto cc ceiling test.litematic"
"""

import gzip
import struct
import sys

TAG_INT = 3
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
    width, height, length = 1, 7, 1
    # y = 0 on the ground, y = 6 six blocks up — out of arm's reach from the ground and with
    # nothing beside it to stand on, so the only way to it is a tower — and air between them.
    column = ["minecraft:air"] * height
    column[0] = "minecraft:stone"
    column[6] = "minecraft:stone"

    palette = ["minecraft:air"]
    indices = []
    for y in range(height):
        for z in range(length):
            for x in range(width):
                name = column[y]
                if name not in palette:
                    palette.append(name)
                indices.append(palette.index(name))
    volume = width * height * length
    bits = bits_for(len(palette))

    region = compound_value([
        (TAG_COMPOUND, "Position", compound_value(
            [(TAG_INT, "x", enc_int(0)), (TAG_INT, "y", enc_int(0)),
             (TAG_INT, "z", enc_int(0))])),
        (TAG_COMPOUND, "Size", compound_value(
            [(TAG_INT, "x", enc_int(width)), (TAG_INT, "y", enc_int(height)),
             (TAG_INT, "z", enc_int(length))])),
        (TAG_LIST, "BlockStatePalette", enc_list(TAG_COMPOUND, [
            compound_value([(TAG_STRING, "Name", enc_string_value(name))]) for name in palette])),
        (TAG_LONG_ARRAY, "BlockStates", enc_long_array(pack(indices, bits, volume))),
        (TAG_LIST, "Entities", enc_list(TAG_COMPOUND, [])),
        (TAG_LIST, "TileEntities", enc_list(TAG_COMPOUND, [])),
    ])

    metadata = compound_value([
        (TAG_STRING, "Name", enc_string_value("Auto cc ceiling test")),
        (TAG_STRING, "Author", enc_string_value("Auto cc")),
        (TAG_STRING, "Description", enc_string_value("Building up under a low ceiling")),
        (TAG_INT, "RegionCount", enc_int(1)),
        (TAG_INT, "TotalBlocks", enc_int(volume)),
        (TAG_INT, "TotalVolume", enc_int(volume)),
        (TAG_COMPOUND, "EnclosingSize", compound_value(
            [(TAG_INT, "x", enc_int(width)), (TAG_INT, "y", enc_int(height)),
             (TAG_INT, "z", enc_int(length))])),
    ])

    root = compound_value([
        (TAG_INT, "MinecraftDataVersion", enc_int(4671)),
        (TAG_INT, "Version", enc_int(6)),
        (TAG_INT, "SubVersion", enc_int(6)),
        (TAG_COMPOUND, "Metadata", metadata),
        (TAG_COMPOUND, "Regions", compound_value([(TAG_COMPOUND, "main", region)])),
    ])

    with gzip.open(path, "wb") as handle:
        handle.write(bytes([TAG_COMPOUND]) + enc_string("") + root)
    print("wrote %s — %dx%dx%d, %d palette entries, %d bits" %
          (path, width, height, length, len(palette), bits))


if __name__ == "__main__":
    main(sys.argv[1])
