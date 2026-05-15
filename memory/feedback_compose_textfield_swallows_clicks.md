---
name: OutlinedTextField swallows .clickable even when readOnly
description: Compose Material3 text fields absorb pointer events for focus handling — a .clickable modifier on a readOnly field never fires; wrap in a Box with an overlay
type: feedback
---

`OutlinedTextField` (and `TextField`) in Compose Material3 consume pointer events internally for focus management, even when `readOnly = true`. A `.clickable { … }` modifier attached directly to the TextField never fires — the field will look tappable but won't navigate.

**Why:** Field interaction (focus indicator, keyboard show/hide) is wired before the modifier chain processes taps. The field doesn't propagate them up.

**How to apply:** When you want a TextField to look like an input but behave like a button (e.g. "Edit…" entry that opens a screen or picker), wrap it in a `Box` and overlay a transparent clickable Box on top:

```kotlin
Box(modifier = Modifier.weight(1f)) {
    OutlinedTextField(
        value = "Edit…",
        onValueChange = {},
        readOnly = true,
        label = { Text("Colors") },
        modifier = Modifier.fillMaxWidth()
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .clickable { onNavigate() }
    )
}
```

The overlay intercepts taps, parent Box gives `matchParentSize()` a size to match. Used in `SettingsScreen.kt` for the Colors entry; same pattern works anywhere you want input-styled tappable entry points (open file picker, navigate to sub-screen, etc.).

Don't reach for `enabled = false` to get clickability — it grays out the field's visuals.
