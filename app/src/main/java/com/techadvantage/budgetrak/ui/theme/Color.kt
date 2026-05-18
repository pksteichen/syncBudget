package com.techadvantage.budgetrak.ui.theme

import androidx.compose.ui.graphics.Color

// Dark theme
val DarkBackground = Color(0xFF2A3A2F)          // Medium green/grey page
val DarkSurface = Color(0xFF3A4E42)              // Dark green-grey cards/dialogs body
val DarkHeaderBackground = Color(0xFF1E2D23)
val DarkHeaderText = Color(0xFFE0E0E0)
val DarkPrimary = Color(0xFFE8D5A0)
val DarkOnPrimary = Color(0xFF1A1A1A)
val DarkCardBackground = Color(0xFF1A1A1A)       // Dark charcoal cards
val DarkCardText = Color(0xFFE8D5A0)             // Warm amber text
val DarkDisplayBackground = Color(0xFF383838)    // Dark grey Solari frame
val DarkSurfaceHeader = Color(0xFF004E62)        // Deep teal dialog/popup header
val DarkSurfaceHeaderText = Color(0xFFE8F5E9)    // Pale green text on dialog header

// Light theme
val LightBackground = Color(0xFFBDD5CC)          // Medium greenish-blue page
val LightSurface = Color(0xFFFFFFFF)
val LightHeaderBackground = Color(0xFF2C2C2C)
val LightHeaderText = Color(0xFFF0E8D8)
val LightPrimary = Color(0xFF2E5C80)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF1C1B1F)
val LightOnSurface = Color(0xFF1C1B1F)
val LightCardBackground = Color(0xFF305880)      // Lighter blue cards
val LightCardText = Color(0xFFFFFFFF)            // White text
val LightDisplayBackground = Color(0xFFD6E5DE)   // Light greenish Solari frame
val LightSurfaceHeader = Color(0xFF2E7D32)       // Light dialog/popup header
val LightSurfaceHeaderText = Color(0xFFFFFFFF)   // White text on light dialog header

// Semantic income/expense (shared light + dark today; themable via SyncBudgetColors).
// NOTE: the same hexes also appear as raw literals in sync-indicator state code
// (online green / offline red) — those usages are intentionally LOCKED and must
// not be migrated to these constants.
val IncomeGreen = Color(0xFF4CAF50)
val ExpenseRed = Color(0xFFF44336)
