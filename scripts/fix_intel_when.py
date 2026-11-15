#!/usr/bin/env python3
from pathlib import Path

p = Path("app/src/main/java/com/example/ui/sources/IntelSourceManagerView.kt")
t = p.read_text(encoding="utf-8")
if "PLACEHOLDER" in t or len(t) < 500:
    raise SystemExit("IntelSourceManagerView.kt is corrupted; restore from git history first")
if "OPEN_API_JSON -> TacticalCyan" in t:
    print("already patched")
    raise SystemExit(0)

replacements = [
(
"""    val typeColor = when (source.type) {
        IntelSourceType.X_TWITTER -> DivisionOrange
        IntelSourceType.FEDIVERSE_MASTODON -> Color(0xFF6364FF)
        IntelSourceType.RSS_FEED -> Color(0xFFFF9800)
        IntelSourceType.HOMEMADE_SDR -> DivisionAmber
        IntelSourceType.RADIO_RADAR -> TacticalCyan
    }""",
"""    val typeColor = when (source.type) {
        IntelSourceType.X_TWITTER -> DivisionOrange
        IntelSourceType.FEDIVERSE_MASTODON -> Color(0xFF6364FF)
        IntelSourceType.RSS_FEED -> Color(0xFFFF9800)
        IntelSourceType.OPEN_API_JSON -> TacticalCyan
        IntelSourceType.DEEP_WEB_ONION -> Color(0xFF9C27B0)
        IntelSourceType.HOMEMADE_SDR -> DivisionAmber
        IntelSourceType.RADIO_RADAR -> TacticalCyan
    }"""
),
(
"""                                            when (type) {
                                                IntelSourceType.X_TWITTER -> endpointUrl = "https://x.com/hey_itsmyturn?lang=en"
                                                IntelSourceType.FEDIVERSE_MASTODON -> endpointUrl = "https://mastodon.social/tags/irancrisis.rss"
                                                IntelSourceType.RSS_FEED -> endpointUrl = "https://reliefweb.int/updates/rss.xml"
                                                IntelSourceType.HOMEMADE_SDR -> {
                                                    endpointUrl = "rtl_tcp://192.168.1.150:1234"
                                                    frequencyText = "1090.00"
                                                }
                                                IntelSourceType.RADIO_RADAR -> {
                                                    endpointUrl = "sdr://local-radio/scanner"
                                                    frequencyText = "142.80"
                                                }
                                            }""",
"""                                            when (type) {
                                                IntelSourceType.X_TWITTER -> endpointUrl = "https://x.com/hey_itsmyturn?lang=en"
                                                IntelSourceType.FEDIVERSE_MASTODON -> endpointUrl = "https://mastodon.social/tags/irancrisis.rss"
                                                IntelSourceType.RSS_FEED -> endpointUrl = "https://reliefweb.int/updates/rss.xml"
                                                IntelSourceType.OPEN_API_JSON -> endpointUrl = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson"
                                                IntelSourceType.DEEP_WEB_ONION -> endpointUrl = "http://example.onion/"
                                                IntelSourceType.HOMEMADE_SDR -> {
                                                    endpointUrl = "rtl_tcp://192.168.1.150:1234"
                                                    frequencyText = "1090.00"
                                                }
                                                IntelSourceType.RADIO_RADAR -> {
                                                    endpointUrl = "sdr://local-radio/scanner"
                                                    frequencyText = "142.80"
                                                }
                                            }"""
),
(
"""                                when (selectedType) {
                                    IntelSourceType.X_TWITTER -> "@hey_itsmyturn OSINT Feed"
                                    IntelSourceType.FEDIVERSE_MASTODON -> "Fediverse Crisis Stream"
                                    IntelSourceType.RSS_FEED -> "Crisis News RSS"
                                    IntelSourceType.HOMEMADE_SDR -> "Homemade RTL-SDR Node"
                                    IntelSourceType.RADIO_RADAR -> "Radio Radar Scanner"
                                }""",
"""                                when (selectedType) {
                                    IntelSourceType.X_TWITTER -> "@hey_itsmyturn OSINT Feed"
                                    IntelSourceType.FEDIVERSE_MASTODON -> "Fediverse Crisis Stream"
                                    IntelSourceType.RSS_FEED -> "Crisis News RSS"
                                    IntelSourceType.OPEN_API_JSON -> "USGS / Open API JSON"
                                    IntelSourceType.DEEP_WEB_ONION -> "Onion source (optional)"
                                    IntelSourceType.HOMEMADE_SDR -> "Homemade RTL-SDR Node"
                                    IntelSourceType.RADIO_RADAR -> "Radio Radar Scanner"
                                }"""
),
]
for a, b in replacements:
    if a not in t:
        raise SystemExit("pattern not found for when-fix")
    t = t.replace(a, b)
p.write_text(t, encoding="utf-8")
print("fixed exhaustive when")
