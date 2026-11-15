#!/usr/bin/env python3
"""Clear fictional theatre lists and wire RealOpenDataService into the app."""
from pathlib import Path
import re

def main() -> None:
    p = Path("app/src/main/java/com/example/data/TacticalDataProvider.kt")
    text = p.read_text(encoding="utf-8")
    for name, typ in [
        ("THREAT_ZONES", "ThreatZone"),
        ("THREAT_HEATMAP_POINTS", "ThreatHeatmapPoint"),
        ("SAFE_HAVENS", "SafeHaven"),
        ("SURVEILLANCE_CAMERAS", "SurveillanceCamera"),
        ("LIVE_INCIDENTS", "LiveIncident"),
    ]:
        pat = re.compile(rf"val\s+{name}\s*(?::\s*[^=]+)?\s*=\s*listOf\(", re.M)
        m = pat.search(text)
        if not m:
            print(f"skip {name}")
            continue
        i = m.end()
        depth = 1
        while i < len(text) and depth:
            if text[i] == "(":
                depth += 1
            elif text[i] == ")":
                depth -= 1
            i += 1
        text = text[: m.start()] + f"val {name}: List<{typ}> = emptyList()\n" + text[i:]
        print(f"emptied {name}")
    p.write_text(text, encoding="utf-8")

    vm = Path("app/src/main/java/com/example/ui/viewmodel/TacticalViewModel.kt")
    v = vm.read_text(encoding="utf-8")
    if "RealOpenDataService" not in v:
        v = v.replace(
            "import com.example.data.TacticalDataProvider\n",
            "import com.example.data.TacticalDataProvider\nimport com.example.data.RealOpenDataService\n",
        )
    if "refreshRealOpenData" not in v:
        hook = '''
    fun refreshRealOpenData() {
        viewModelScope.launch {
            val lat = _uiState.value.userMovement.lat
            val lng = _uiState.value.userMovement.lng
            runCatching { RealOpenDataService.fetchAll(lat, lng) }
                .onSuccess { bundle ->
                    _uiState.update {
                        it.copy(
                            threatZones = bundle.threatZones,
                            heatmapPoints = bundle.heatmapPoints,
                            liveIncidents = bundle.liveIncidents,
                            safeHavens = if (bundle.safeHavens.isNotEmpty()) bundle.safeHavens else it.safeHavens,
                            cameras = emptyList(),
                            intelSources = bundle.intelSources,
                            threatWarning = "LIVE OPEN DATA: " + bundle.statusMessage,
                            showCctvOverlay = false,
                            geoJsonThreatPolygons = emptyList(),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(threatWarning = "Open-data fetch failed: " + (e.message ?: "error"))
                    }
                }
        }
    }
'''
        v = v.replace(
            "private var appContextRef: Context? = null\n",
            "private var appContextRef: Context? = null\n" + hook,
        )
        v = v.replace(
            "persistenceInitialized = true\n",
            "persistenceInitialized = true\n        refreshRealOpenData()\n",
            1,
        )
    vm.write_text(v, encoding="utf-8")
    print("viewmodel ready")

    main_screen = Path("app/src/main/java/com/example/ui/TacticalMainScreen.kt")
    if main_screen.exists():
        mt = main_screen.read_text(encoding="utf-8")
        if "refreshRealOpenData" not in mt:
            mt = mt.replace(
                "viewModel.initPersistence(context)",
                "viewModel.initPersistence(context)\n        viewModel.refreshRealOpenData()",
                1,
            )
            main_screen.write_text(mt, encoding="utf-8")
            print("main screen hooked")

if __name__ == "__main__":
    main()
