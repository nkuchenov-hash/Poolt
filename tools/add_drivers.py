#!/usr/bin/env python3
import argparse
import json
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description="Bulk merge Poolt driver packages into drivers/catalog.json")
    parser.add_argument("files", nargs="+", help="JSON files containing one driver object or a drivers array")
    parser.add_argument("--catalog", default="drivers/catalog.json")
    args = parser.parse_args()

    catalog_path = Path(args.catalog)
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    existing = {d["id"]: d for d in catalog.get("drivers", [])}

    for filename in args.files:
        data = json.loads(Path(filename).read_text(encoding="utf-8"))
        items = data.get("drivers", []) if isinstance(data, dict) and "drivers" in data else [data]
        for driver in items:
            driver_id = driver["id"]
            current = existing.get(driver_id)
            if current is None or int(driver.get("version", 0)) >= int(current.get("version", 0)):
                existing[driver_id] = driver

    catalog["drivers"] = sorted(existing.values(), key=lambda d: d["id"])
    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Catalog now contains {len(catalog['drivers'])} drivers")

if __name__ == "__main__":
    main()
