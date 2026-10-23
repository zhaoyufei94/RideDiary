#!/usr/bin/env python3
"""Generate Android brand vectors from the approved graph-paper SVG. No dependencies."""
from pathlib import Path
import argparse
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SVG = ET.parse(ROOT / "docs/brand/ridediary-logo.svg").getroot()
NS = {"svg": "http://www.w3.org/2000/svg"}
ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)


def attr(element, name, value):
    element.set(f"{{{ANDROID}}}{name}", str(value))


def circle_path(cx, cy, radius):
    return (f"M{cx-radius:g},{cy:g}a{radius:g},{radius:g} 0,1 0,{2*radius:g},0"
            f"a{radius:g},{radius:g} 0,1 0,{-2*radius:g},0Z")


def paths(group, monochrome=False):
    result = []
    for source in SVG.find(f"svg:g[@id='{group}']", NS):
        item = ET.Element("path")
        if source.tag.endswith("circle"):
            cx, cy, radius = (float(source.get(key)) for key in ("cx", "cy", "r"))
            stroke = float(source.get("stroke-width")) / 2
            if monochrome:
                # Transparent holes, rather than white disks, preserve the alpha silhouette.
                attr(item, "pathData", circle_path(cx, cy, radius+stroke) + circle_path(cx, cy, radius-stroke))
                attr(item, "fillType", "evenOdd")
                attr(item, "fillColor", "#ffffff")
            else:
                attr(item, "pathData", circle_path(cx, cy, radius))
                attr(item, "fillColor", source.get("fill"))
                attr(item, "strokeColor", source.get("stroke"))
                attr(item, "strokeWidth", source.get("stroke-width"))
        else:
            attr(item, "pathData", source.get("d"))
            fill = source.get("fill", "none")
            attr(item, "fillColor", "#00000000" if fill == "none" else fill)
            if source.get("stroke"):
                attr(item, "strokeColor", "#ffffff" if monochrome else source.get("stroke"))
                attr(item, "strokeWidth", source.get("stroke-width"))
            for key, android_key in [("stroke-linecap", "strokeLineCap"), ("stroke-linejoin", "strokeLineJoin")]:
                if source.get(key):
                    attr(item, android_key, source.get(key))
        result.append(item)
    return result


def vector(groups, size=108, scale=1, monochrome=False):
    root = ET.Element("vector")
    for name, value in {"width": f"{size}dp", "height": f"{size}dp", "viewportWidth": 108, "viewportHeight": 108}.items():
        attr(root, name, value)
    for name in groups:
        parent = root
        if name == "route" and scale != 1:
            parent = ET.SubElement(root, "group")
            for key, value in {"pivotX": 54, "pivotY": 54, "scaleX": scale, "scaleY": scale}.items():
                attr(parent, key, value)
        parent.extend(paths(name, monochrome))
    ET.indent(root, space="    ")
    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding="unicode") + "\n"


def adaptive():
    # Android <33 ignores the unknown monochrome child; newer launchers use it.
    monochrome = '\n    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />'
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<adaptive-icon xmlns:android="{ANDROID}">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />'
            f'{monochrome}\n</adaptive-icon>\n')


outputs = {
    "drawable/ic_ridediary_logo.xml": vector(["paper", "route"], size=48),
    "drawable/ic_launcher_background.xml": vector(["paper"]),
    # Essential foreground stays inside the centered 66 dp safe circle on a 108 dp layer.
    "drawable/ic_launcher_foreground.xml": vector(["route"], scale=0.70),
    "drawable/ic_launcher_monochrome.xml": vector(["route"], scale=0.70, monochrome=True),
    "drawable/ic_ridediary_notification.xml": vector(["route"], size=24, scale=1.12, monochrome=True),
    "mipmap-anydpi/ic_launcher.xml": adaptive(),
}
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--check", action="store_true", help="Fail if generated resources need updating")
args = parser.parse_args()
for relative, content in outputs.items():
    path = RES / relative
    if args.check:
        if not path.exists() or path.read_text() != content:
            raise SystemExit(f"Stale brand resource: {path.relative_to(ROOT)}")
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
print(f"PASS: {len(outputs)} Android brand resources {'match the SVG' if args.check else 'generated'}.")
