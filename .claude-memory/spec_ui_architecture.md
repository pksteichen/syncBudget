---
name: UI Architecture Specification
description: Complete screen map, navigation, layout structure, widget integration, and user flows for the syncBudget app
type: reference
---

# UI Architecture Specification

## App Structure
- Single Activity (MainActivity), Kotlin + Jetpack Compose
- Navigation via `currentScreen: String` mutable state (no Navigation Component)
- Data loaded from JSON file repositories on startup into `mutableStateListOf`
- Compose `derivedStateOf` for filtered/computed values (activeTransactions, budgetAmount, etc.)

## Screens (20 total: 10 main + 10 help)

| Screen | Key Purpose |
|--------|------------|
| **main** | Dashboard: Solari flip display, spending chart, navigation cards, +/- buttons |
| **transactions** | Add/edit/delete transactions, search, filter, receipt photos, CSV import |
| **recurring_expenses** | Setup recurring bills (daily/weekly/monthly/annual), set-aside tracking |
| **amortization** | Spread large expenses over multiple periods |
| **future_expenditures** | Savings goals (target-date or fixed-contribution) |
| **budget_config** | Income sources, budget period, reset schedule, income mode |
| **budget_calendar** | Monthly calendar view of recurring income/expense events |
| **simulation_graph** | Cash flow projection timeline |
| **settings** | Currency, language, date format, categories, backups, widget, matching config |
| **family_sync** | Group create/join, device roster, admin controls, sync status |

Each main screen has a corresponding `_help` screen accessible via help icon.

## Main Screen (Dashboard) Layout
1. **Top bar**: Logo, settings icon (left), help icon (right)
2. **Ad banner**: Shows for free users (320x50)
3. **Solari flip display**: Retro flip-clock showing available cash (bitmap rendered)
   - Sync indicator (bottom-left): green/yellow/orange/red dot, magenta flash for repairs
   - Supercharge bolt (bottom-right): animated when spare cash + eligible goals
4. **Spending chart**: Bar/line chart of expenses (user-selectable range/palette)
5. **Navigation cards**: Transactions, Recurring, Amortization, Future Expenditures, Calendar
6. **Quick-add buttons**: +Income, -Expense in bottom bar

## Widget System
- `BudgetWidgetProvider`: AppWidgetProvider managing lifecycle, updates, alarms
- `WidgetRenderer`: Canvas bitmap renderer for Solari flip-display cards
- `WidgetTransactionActivity`: Quick add expense/income from widget buttons
- `WidgetRefreshWorker`: Background refresh every 30 min via WorkManager
- Theme-aware: light mode (blue #305880), dark mode (#1A1A1A)
- Free users: 1 widget transaction/day, overlay "Upgrade" text on Solari
- Min size 2x1, default 4x1, resizable both directions

## QuickStart Guide
6-step onboarding overlay: Welcome → Budget Period → Income → Recurring → First Transaction → Done
Bilingual (English/Spanish), step indicator dots, skip option.

## Dialog System
Standard dialog style: colored header, colored footer with buttons, clear item distinction.
Confirmation dialogs for: duplicate resolution, recurring/amortization/income linking, category assignment, delete operations.
