#!/usr/bin/env python3
"""Generate deterministic, credential-free k6 account pair fixtures."""
import argparse
import json
from pathlib import Path

parser = argparse.ArgumentParser(description="Generate synthetic VaultCore transfer fixtures")
parser.add_argument("--pairs", type=int, default=2)
parser.add_argument("--output", type=Path, default=Path("tests/performance/transfer-fixtures.local.json"))
args = parser.parse_args()
if not 1 <= args.pairs <= 10000:
    parser.error("--pairs must be 1..10000")
rows = [
    {
        "fromAccount": f"BSRC{i:08d}",
        "toAccount": f"BDST{i:08d}"
    }
    for i in range(1, args.pairs + 1)
]
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(rows, indent=2) + "\n", encoding="utf-8")
print(f"Generated {len(rows)} synthetic account pairs at {args.output}")
