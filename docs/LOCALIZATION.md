# Localization

The default resource file is `app/src/main/res/values/strings.xml`. Translations use
standard `values-*` and BCP 47 `values-b+*` resource folders. Portuguese uses the
language-only `pt` locale; Simplified Chinese uses `zh-Hans`. Tamazight uses the
Standard Moroccan Tamazight language code `zgh` with separate `Latn` and `Tfng`
scripts, not a right-to-left override.

Language selection uses `AppCompatDelegate.setApplicationLocales`. Keep the ordered
tags in `ui/AppLanguages.kt`, native names in `values/languages.xml`, LocaleConfig,
and resource folders consistent. Only the nine shipped choices are retained in
resource configuration filtering so library translations do not introduce partly
translated unsupported languages.

Run `python3 scripts/check_translations.py` before building. It checks coverage,
duplicate keys, positional format arguments, sharing links and the supported locale
list. Do not translate resource IDs, `%1$s` / `%1$d` placeholders, URLs or brand
names. Technical upstream diagnostics intentionally retain their original language.
Use resource-backed `UiText` for messages that survive Activity recreation; do not
persist a translated sentence as the source of truth.

## Translation review status

The translations are initial drafts, not independently certified translations.
Tamazight especially needs review by fluent speakers familiar with the intended
variety and orthography before a public release. The Tifinagh file is an orthographic
conversion of the Latin draft with technical names and URLs preserved, rather than
an independently reviewed translation. Correct both script versions together.
No runtime transliteration or translation service is used.

The localized privacy text is a summary and explicitly labels the full notice as
English. It must not be mistaken for a reviewed full legal translation.

## Device checks

`LanguageSmokeTest` switches every language while retaining an unfinished link,
checks Arabic RTL and URL input direction, then returns to System default. Run it
on Android 12 or earlier and Android 13 or later. Also inspect the home screen,
settings, link wizard, error states, update dialogs, full-screen player and privacy
summary in light/dark mode, on a narrow screen and with larger font settings.
Verify Tifinagh glyph coverage on target devices. A resource coverage check cannot
validate translation quality or font rendering.
