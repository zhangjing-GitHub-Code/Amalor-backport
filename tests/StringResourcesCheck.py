"""Keep default/translated string keys and format arguments in sync."""

from pathlib import Path
import re
import xml.etree.ElementTree as ET


resources = Path(__file__).resolve().parents[1] / "app/src/main/res"


def strings(directory):
    items = ET.parse(resources / directory / "strings.xml").getroot().findall("string")
    result = {item.get("name"): "".join(item.itertext()) for item in items}
    assert len(result) == len(items), f"Duplicate string resource in {directory}"
    return result


base = strings("values")
for directory in ("values-zh-rCN", "values-ru"):
    localized = strings(directory)
    assert localized.keys() == base.keys(), (
        f"{directory}: missing={sorted(base.keys() - localized.keys())}, "
        f"no_default={sorted(localized.keys() - base.keys())}"
    )
    for key, value in localized.items():
        tokens = lambda text: sorted(re.findall(r"%(?:\d+\$)?[sdf]", text))
        assert tokens(value) == tokens(base[key]), f"Format arguments differ: {directory}/{key}"
print(f"String resources checked: {len(base)} matching keys across default, Chinese and Russian")
