"""Check shipped locales, resource coverage and positional format arguments."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
LOCALES = {"ar": "values-ar", "fr": "values-fr", "de": "values-de", "es": "values-es", "pt": "values-pt", "zh-Hans": "values-b+zh+Hans", "zgh-Latn": "values-b+zgh+Latn", "zgh-Tfng": "values-b+zgh+Tfng"}


def strings(path):
    elements = ET.parse(path).getroot().findall("string")
    names = [element.attrib["name"] for element in elements]
    assert len(names) == len(set(names)), f"Duplicate keys: {path}"
    return {element.attrib["name"]: element for element in elements}


def validate():
    base = strings(RES / "values/strings.xml")
    expected = {key for key, element in base.items() if element.get("translatable") != "false"}
    for locale, directory in LOCALES.items():
        translated = strings(RES / directory / "strings.xml")
        assert set(translated) == expected, f"{locale}: missing {expected-set(translated)}, unexpected {set(translated)-expected}"
        for key, element in translated.items():
            source = "".join(base[key].itertext())
            target = "".join(element.itertext())
            assert target.strip(), f"{locale}/{key}: empty translation"
            placeholders = lambda value: sorted(re.findall(r"%\d+\$[sd]", value))
            assert placeholders(source) == placeholders(target), f"{locale}/{key}: format mismatch"
            assert "https://github.com/Amine-AO/JustWatch" in target if key == "share_message" else True
            assert "\u202e" not in target and "\u202d" not in target, f"{locale}/{key}: directional override"
    declared = {element.get("{http://schemas.android.com/apk/res/android}name") for element in ET.parse(RES / "xml/locales_config.xml").getroot()}
    assert declared == {"en", *LOCALES}, "LocaleConfig does not match translated resources"
    labels = ET.parse(RES / "values/languages.xml").getroot().find("string-array")
    assert len(labels) == len(declared), "Language picker labels do not match supported locales"
    print(f"Validated {len(expected)} strings in {len(LOCALES)} translations and {len(declared)} language choices")


if __name__ == "__main__":
    validate()
