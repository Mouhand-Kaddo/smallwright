#!/usr/bin/env python3
"""Compare public/protected Java declarations in two platform class archives.

This is a mechanical ABI preflight, not a license or complete SDK-parity grant.
The parser follows JVMS 17 chapter 4: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
Resource tables are checked separately by audit_platform_inputs.py.
"""

import argparse
import json
from pathlib import Path
import struct
import sys
from zipfile import BadZipFile, ZipFile


VISIBLE = 0x0001 | 0x0004


class Reader:
    def __init__(self, data):
        self.data = data
        self.offset = 0

    def take(self, count):
        end = self.offset + count
        if count < 0 or end > len(self.data):
            raise ValueError("truncated class file")
        value = self.data[self.offset:end]
        self.offset = end
        return value

    def u1(self):
        return self.take(1)[0]

    def u2(self):
        return struct.unpack(">H", self.take(2))[0]

    def u4(self):
        return struct.unpack(">I", self.take(4))[0]

    def done(self):
        if self.offset != len(self.data):
            raise ValueError("unexpected class file trailing bytes")


class ClassFile:
    def __init__(self, data, expected_name):
        reader = Reader(data)
        if reader.u4() != 0xCAFEBABE:
            raise ValueError(f"invalid class file: {expected_name}")
        reader.u2()  # minor version
        reader.u2()  # major version: output compiler version is not API surface
        pool_size = reader.u2()
        self.pool = [None] * pool_size
        index = 1
        while index < pool_size:
            tag = reader.u1()
            if tag == 1:
                self.pool[index] = (tag, reader.take(reader.u2()).decode(
                    "utf-8", "surrogateescape"))
            elif tag in (3, 4):
                self.pool[index] = (tag, reader.take(4).hex())
            elif tag in (5, 6):
                self.pool[index] = (tag, reader.take(8).hex())
                index += 1
            elif tag in (7, 8, 16, 19, 20):
                self.pool[index] = (tag, reader.u2())
            elif tag in (9, 10, 11, 12, 17, 18):
                self.pool[index] = (tag, reader.u2(), reader.u2())
            elif tag == 15:
                self.pool[index] = (tag, reader.u1(), reader.u2())
            else:
                raise ValueError(f"unknown constant-pool tag {tag}: {expected_name}")
            index += 1
        self.flags = reader.u2()
        self.this_index = reader.u2()
        self.name = self.class_name(self.this_index)
        if self.name + ".class" != expected_name:
            raise ValueError(f"class name differs from ZIP path: {expected_name}")
        self.superclass = self.class_name(reader.u2())
        self.interfaces = tuple(self.class_name(reader.u2()) for _ in range(reader.u2()))
        self.fields = self.members(reader)
        self.methods = self.members(reader)
        self.attributes = self.attributes_of(reader)
        reader.done()

    def entry(self, index, tag=None):
        if index < 1 or index >= len(self.pool) or self.pool[index] is None:
            raise ValueError("invalid class constant-pool index")
        value = self.pool[index]
        if tag is not None and value[0] != tag:
            raise ValueError("unexpected class constant-pool tag")
        return value

    def utf8(self, index):
        return self.entry(index, 1)[1]

    def class_name(self, index):
        return self.utf8(self.entry(index, 7)[1]) if index else None

    def constant(self, index):
        entry = self.entry(index)
        if entry[0] in (3, 4, 5, 6):
            return entry
        if entry[0] == 8:
            return (8, self.utf8(entry[1]))
        raise ValueError("invalid ConstantValue entry")

    def members(self, reader):
        result = {}
        for _ in range(reader.u2()):
            flags = reader.u2()
            name = self.utf8(reader.u2())
            descriptor = self.utf8(reader.u2())
            attributes = self.attributes_of(reader)
            if flags & VISIBLE:
                key = (name, descriptor)
                if key in result:
                    raise ValueError("duplicate visible class member")
                result[key] = (flags, attributes)
        return result

    def annotation(self, reader):
        name = self.utf8(reader.u2())
        values = []
        for _ in range(reader.u2()):
            values.append((self.utf8(reader.u2()), self.element_value(reader)))
        return (name, tuple(sorted(values)))

    def element_value(self, reader):
        tag = chr(reader.u1())
        if tag in "BCDFIJSZ":
            return (tag, self.constant(reader.u2()))
        if tag == "s":
            return (tag, self.utf8(reader.u2()))
        if tag == "e":
            return (tag, self.utf8(reader.u2()), self.utf8(reader.u2()))
        if tag == "c":
            return (tag, self.utf8(reader.u2()))
        if tag == "@":
            return (tag, self.annotation(reader))
        if tag == "[":
            return (tag, tuple(self.element_value(reader) for _ in range(reader.u2())))
        raise ValueError(f"unknown annotation element tag {tag}")

    def annotations(self, reader):
        return tuple(sorted((self.annotation(reader) for _ in range(reader.u2())), key=repr))

    def type_annotation(self, reader):
        target = reader.u1()
        if target in (0x00, 0x01, 0x16):
            info = reader.take(1)
        elif target in (0x10, 0x17, 0x42, 0x43, 0x44, 0x45, 0x46):
            info = reader.take(2)
        elif target in (0x11, 0x12):
            info = reader.take(2)
        elif target in (0x13, 0x14, 0x15):
            info = b""
        elif target in (0x40, 0x41):
            info = reader.take(6 * reader.u2())
        elif 0x47 <= target <= 0x4B:
            info = reader.take(3)
        else:
            raise ValueError(f"unknown type annotation target {target}")
        path = reader.take(2 * reader.u1())
        return (target, info.hex(), path.hex(), self.annotation(reader))

    def attribute(self, name, data):
        reader = Reader(data)
        if name == "Signature":
            value = self.utf8(reader.u2())
        elif name == "ConstantValue":
            value = self.constant(reader.u2())
        elif name == "Exceptions":
            value = tuple(self.class_name(reader.u2()) for _ in range(reader.u2()))
        elif name in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
            value = self.annotations(reader)
        elif name in ("RuntimeVisibleParameterAnnotations",
                      "RuntimeInvisibleParameterAnnotations"):
            value = tuple(self.annotations(reader) for _ in range(reader.u1()))
        elif name in ("RuntimeVisibleTypeAnnotations", "RuntimeInvisibleTypeAnnotations"):
            value = tuple(sorted((self.type_annotation(reader)
                                  for _ in range(reader.u2())), key=repr))
        elif name == "AnnotationDefault":
            value = self.element_value(reader)
        elif name == "InnerClasses":
            entries = []
            for _ in range(reader.u2()):
                inner = reader.u2()
                outer = reader.u2()
                simple = reader.u2()
                flags = reader.u2()
                if inner == self.this_index:
                    entries.append((self.class_name(outer),
                                    self.utf8(simple) if simple else None, flags))
            value = tuple(entries)
        elif name == "PermittedSubclasses":
            value = tuple(self.class_name(reader.u2()) for _ in range(reader.u2()))
        elif name == "Record":
            value = tuple((self.utf8(reader.u2()), self.utf8(reader.u2()),
                           self.attributes_of(reader)) for _ in range(reader.u2()))
        elif name == "Deprecated":
            value = True
        else:
            return None
        reader.done()
        return value

    def attributes_of(self, reader):
        result = {}
        for _ in range(reader.u2()):
            name = self.utf8(reader.u2())
            value = self.attribute(name, reader.take(reader.u4()))
            if value is not None:
                if name in result:
                    raise ValueError("duplicate ABI class attribute")
                result[name] = value
        return result

    def visible(self):
        if self.flags & VISIBLE:
            return True
        return any(flags & VISIBLE for _, _, flags in self.attributes.get("InnerClasses", ()))

    def abi(self):
        return (self.flags, self.superclass, self.interfaces,
                self.attributes, self.fields, self.methods)


def classes(path):
    with ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError(f"duplicate ZIP entry in {path}")
        return {name: archive.read(name) for name in names if name.endswith(".class")}


def compare(reference_path, candidate_path):
    reference = classes(reference_path)
    candidate = classes(candidate_path)
    if not reference:
        raise ValueError("reference contains no classes")
    expected = set(reference)
    actual = set(candidate)
    different = []
    for name in sorted(expected & actual):
        left = ClassFile(reference[name], name)
        right = ClassFile(candidate[name], name)
        if left.visible() != right.visible() or left.visible() and left.abi() != right.abi():
            different.append(name)
    missing = sorted(expected - actual)
    extra = sorted(actual - expected)
    return {
        "status": "declaration_abi_preflight_only",
        "reference_class_count": len(reference),
        "candidate_class_count": len(candidate),
        "public_api_match": not (missing or extra or different),
        "missing_classes": missing,
        "extra_classes": extra,
        "different_classes": different,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = compare(args.reference, args.candidate)
    except (OSError, BadZipFile, ValueError) as error:
        parser.exit(2, f"platform ABI comparison failed: {error}\n")
    print(json.dumps(result, indent=2))
    return 0 if result["public_api_match"] else 1


if __name__ == "__main__":
    sys.exit(main())
