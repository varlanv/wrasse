#!/usr/bin/env python3
"""Renders bench-projects/results/<size>.csv files as markdown tables (medians over iterations)."""
import csv
import statistics
import sys
from collections import defaultdict
from pathlib import Path

RESULTS = Path(__file__).resolve().parent / "bench-projects" / "results"
TOOLS = ["compile", "wrasse", "ktlint", "ktfmt", "detekt"]
TIME_SCENARIOS = [
    ("cold-check", "cold check (fresh daemon, empty caches)"),
    ("warm-noop-check", "warm no-op check"),
    ("cached-check", "check after `rm -rf build` (build-cache hit)"),
    ("cold-format", "cold format"),
    ("cold-format-apply", "cold format, apply step"),
    ("post-format-compile", "compile right after format"),
    ("post-format-check", "check right after format"),
    ("cold-compile", "cold compile"),
    ("inc-format", "format after one broken file"),
    ("inc-format-apply", "format after one broken file, apply step"),
    ("inc-compile", "compile after that format"),
    ("inc-check", "check after that change"),
]
PEAKS = [("check-session-peak", "peak RSS, check session"), ("format-session-peak", "peak RSS, format session")]


def load(size):
    path = RESULTS / f"{size}.csv"
    if not path.exists():
        return None
    rows = defaultdict(list)
    with path.open() as f:
        for row in csv.DictReader(f):
            rows[(row["tool"], row["scenario"])].append(row)
    return rows


def median(values):
    values = [v for v in values if v not in ("", None)]
    return statistics.median(float(v) for v in values) if values else None


def fmt_seconds(v):
    return "-" if v is None else f"{v:.1f} s"


def fmt_mb(v):
    return "-" if v is None else f"{int(v) // 1024} MB"


def fmt_int(v):
    return "-" if v is None else str(int(v))


def table(header, lines):
    out = ["| " + " | ".join(header) + " |", "|" + "|".join("---" for _ in header) + "|"]
    out += ["| " + " | ".join(line) + " |" for line in lines]
    return "\n".join(out)


def render(size):
    rows = load(size)
    if rows is None:
        return f"_no results for {size}_"
    tools = [t for t in TOOLS if any(k[0] == t for k in rows)]
    iterations = max((len(v) for v in rows.values()), default=0)
    out = [f"### {size} LOC ({iterations} iteration{'s' if iterations != 1 else ''}, medians)", ""]

    lines = []
    for key, label in TIME_SCENARIOS:
        cells = [fmt_seconds(median([r["seconds"] for r in rows.get((t, key), [])])) for t in tools]
        if any(c != "-" for c in cells):
            lines.append([label] + cells)
    out += [table(["scenario"] + tools, lines), ""]

    lines = []
    for key, label in PEAKS:
        cells = [fmt_mb(median([r["peak_kb"] for r in rows.get((t, key), [])])) for t in tools]
        if any(c != "-" for c in cells):
            lines.append([label] + cells)
    out += [table(["memory"] + tools, lines), ""]

    lines = []
    for key in ("cold-check", "post-format-check", "inc-check"):
        cells = [fmt_int(median([r["findings"] for r in rows.get((t, key), [])])) for t in tools]
        if any(c != "-" for c in cells):
            lines.append([f"findings, {key}"] + cells)
    internal = median([r["internal_errors"] for r in rows.get(("wrasse", "cold-check"), [])])
    journal = median([r["findings"] for r in rows.get(("wrasse", "journal-bytes"), [])])
    failures = sorted({f'{k[0]} {k[1]}' for k, v in rows.items() for r in v if r["exit"] not in ("", "0")})
    lines.append(["wrasse internal errors, cold check"] + [fmt_int(internal) if t == "wrasse" else "-" for t in tools])
    lines.append(["wrasse patch journal after format session"] + [
        (f"{int(journal) // 1024} KB" if journal is not None else "-") if t == "wrasse" else "-" for t in tools
    ])
    out += [table(["correctness"] + tools, lines), ""]
    if failures:
        out += ["Non-zero Gradle exits: " + ", ".join(failures), ""]
    return "\n".join(out)


if __name__ == "__main__":
    sizes = sys.argv[1:] or ["5k", "50k", "1m"]
    print("\n".join(render(size) for size in sizes))
