#!/usr/bin/env python3
"""Validate the repo's bounded benchmark JSONL schema without external packages."""

import json
import math
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / "benchmarks" / "results.schema.json"
PROMPTS = ROOT / "benchmarks" / "prompts.jsonl"
KEYWORDS = {"$schema", "$id", "title", "$defs", "$ref", "anyOf", "type", "enum",
            "pattern", "maxLength", "minimum", "additionalProperties", "required", "properties"}


def check_supported(schema):
    if isinstance(schema, dict):
        unknown = set(schema) - KEYWORDS
        if unknown:
            raise ValueError("Benchmark schema uses an unsupported keyword")
        for key in ("properties", "$defs"):
            for child in schema.get(key, {}).values():
                check_supported(child)
        for child in schema.get("anyOf", []):
            check_supported(child)


def type_matches(value, name):
    return {
        "null": lambda: value is None,
        "boolean": lambda: isinstance(value, bool),
        "integer": lambda: isinstance(value, int) and not isinstance(value, bool),
        "number": lambda: isinstance(value, (int, float)) and not isinstance(value, bool)
        and (not isinstance(value, float) or math.isfinite(value)),
        "string": lambda: isinstance(value, str),
        "object": lambda: isinstance(value, dict),
        "array": lambda: isinstance(value, list),
    }[name]()


def valid(value, schema, root):
    if "$ref" in schema:
        ref = schema["$ref"]
        if not ref.startswith("#/$defs/") or ref[8:] not in root["$defs"]:
            raise ValueError("Invalid benchmark schema reference")
        if not valid(value, root["$defs"][ref[8:]], root):
            return False
    if "anyOf" in schema and not any(valid(value, option, root) for option in schema["anyOf"]):
        return False
    types = schema.get("type")
    if types is not None and not any(type_matches(value, name) for name in
                                     (types if isinstance(types, list) else [types])):
        return False
    if "enum" in schema and value not in schema["enum"]:
        return False
    if "minimum" in schema and isinstance(value, (int, float)) and value < schema["minimum"]:
        return False
    if isinstance(value, str):
        if "maxLength" in schema and len(value) > schema["maxLength"]:
            return False
        if "pattern" in schema and re.search(schema["pattern"], value) is None:
            return False
    if isinstance(value, dict):
        if any(name not in value for name in schema.get("required", [])):
            return False
        properties = schema.get("properties", {})
        if schema.get("additionalProperties") is False and any(
                name not in properties for name in value):
            return False
        if any(not valid(item, properties[name], root) for name, item in value.items()
               if name in properties):
            return False
    return True


def main(paths):
    if not paths:
        print("Usage: scripts/validate_benchmarks.py RESULTS.jsonl [...]", file=sys.stderr)
        return 2
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    check_supported(schema)
    prompt_ids = {json.loads(line)["id"] for line in PROMPTS.read_text(encoding="utf-8").splitlines()
                  if line.strip()}
    good = bad = 0
    for path in paths:
        try:
            stream = open(path, "rb")
        except OSError:
            print("Could not read results file", file=sys.stderr)
            return 2
        with stream:
            for number, raw in enumerate(stream, 1):
                try:
                    if len(raw) > 64 * 1024:
                        raise ValueError("line too long")
                    value = json.loads(raw, parse_constant=lambda _: (_ for _ in ()).throw(
                        ValueError("Non-finite JSON number")))
                    if not valid(value, schema, schema) or value["prompt_id"] not in prompt_ids:
                        raise ValueError("schema mismatch")
                except (UnicodeError, ValueError, TypeError):
                    bad += 1
                    print(f"line {number}: invalid benchmark result", file=sys.stderr)
                else:
                    good += 1
    if bad:
        print(f"{bad} invalid result(s); {good} valid", file=sys.stderr)
        return 1
    print(f"{good} valid results")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
