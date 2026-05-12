---
name: Dialog window stacking — obsolete after 2026-05-11 in-tree overlay refactor
description: The pre-2026-05-11 Compose-Dialog (separate Android window) implementation suffered from window-stacking issues where the ad bar wasn't tappable during open dialogs. AdAwareDialog is now an in-tree overlay rendered inside the main Activity window. Window-stacking concerns no longer apply.
type: feedback
---

## Current state (v2.10.20+, 2026-05-11)

`AdAwareDialog` and `AdAwareAlertDialog` no longer create separate Android windows via `androidx.compose.ui.window.Dialog`. They are state-driven in-tree overlays:

- `AdAwareDialogState` holds a `mutableStateListOf<AdAwareDialogEntry>` of active entries.
- `AdAwareDialog` registers/unregisters an entry via `DisposableEffect`; renders no UI of its own.
- `AdAwareDialogHost` is placed once inside `SyncBudgetTheme`'s outer `Box` (below status bar + ad banner, above the navigation bar). It iterates entries — sorted by a stable `sequence: Long` for predictable Z-order — and renders dim + centered content for each.
- Back press handled per-entry by `BackHandler` (Compose's stack semantics close the topmost first).
- Scrim-tap is intentionally not a dismiss path (no-op clickable on the dim layer + `dismissOnClickOutside = false` on the surrounding `DialogProperties`).
- IME push-up handled by `Modifier.imePadding()` on the content wrapper inside the host.

**Consequence:** the ad bar above the host's overlay area is no longer covered by a separate dialog window, so AdMob's `NativeAdView` in the main window receives clicks normally — even while a dialog is open. This is the whole reason for the refactor.

## Exceptions still using raw Compose Dialog

Two intentional holdouts:

- **`SwipeablePhotoRow` photo viewer** — wants a fullscreen black canvas that intentionally covers the ad bar (immersive photo viewing). Stays as `androidx.compose.ui.window.Dialog`.
- **`WidgetTransactionActivity` match dialogs** (4 sites) — that activity uses its own `MaterialTheme` wrapper, not `SyncBudgetTheme`, so it doesn't host `AdAwareDialogState`. The Dialogs in that activity stay raw.

## What changed since the original feedback entry

The original feedback (now-obsolete) advised: *"overlays meant to appear above an open Dialog must themselves be wrapped in a Dialog."* That advice applied to the old separate-window setup. With the in-tree host, there is no separate dialog window to outrank — stacking is composition-order / `sequence` driven inside a single window. Just call `AdAwareDialog` from anywhere within `SyncBudgetTheme`'s scope and the host renders it correctly.

## Related

- `feedback_dialog_safety_patterns.md` — `state?.let { value -> ... }` over `if (state != null) { state!! }` to avoid a crash class introduced by the host pattern.
- `project_ad_implementation.md` — overlay refactor details + Spanish ad targeting via per-app locale.
- `spec_ui_architecture.md` — the AdAware system at a glance.
