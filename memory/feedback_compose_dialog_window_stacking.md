---
name: Compose Dialog windows stack above the main composition window
description: Overlays meant to render above an open Compose Dialog must themselves be wrapped in a Dialog. A plain Surface or Box at the top level of setContent renders in the activity's main window, which Android Window Manager always draws *under* any Dialog window.
type: feedback
---
A `Surface` (or any normal Composable) added at the top level of `setContent` lives in the activity's main window. A `Dialog` (e.g. `AdAwareDialog`, used by `TransactionDialog`) creates its own platform window via Compose's `androidx.compose.ui.window.Dialog`. Window Manager always renders Dialog windows above the main window, so any "overlay" Composable that needs to appear above an open dialog must itself be a `Dialog`.

**Why:** First attempt at the help-from-dialog overlay used a top-level `Surface(modifier = Modifier.fillMaxSize(), ...) { TransactionsHelpScreen(...) }`. The dashboard's `AdAwareDialog` (transaction dialog) kept rendering on top because its window was z-ordered above the main composition window. Wrapping the help screen in `androidx.compose.ui.window.Dialog(properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false))` made it stack as a sibling Dialog window — newest Dialog window wins z-order — and rendered as expected.

**How to apply:**
- If a feature opens a UI element from inside an open `Dialog` and the new element should *cover* the dialog: wrap the new element in a `Dialog` (or `AdAwareDialog`), not a plain `Surface`/`Box`.
- If the new element should appear *behind* the dialog (e.g. a faded backdrop tint): a plain Composable in the main window is correct — that's where backdrop dim lives anyway.
- For fullscreen Dialog overlays, set `DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)` so the Dialog isn't constrained to the default ~280dp width and can draw under system bars.
- BackHandler is unnecessary inside a Dialog overlay — `Dialog.onDismissRequest` already fires on system back. Wire `onBack` callbacks (e.g. a top-bar back arrow) to call the same close handler.
