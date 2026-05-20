# BudgeTrak Help Chat — Knowledge Base

This knowledge base is the chatbot's only source of factual information
about BudgeTrak. Every answer must be grounded here. If a user question
is not covered, the chatbot says so plainly and points the user at the
Email button.

---

## 1. The Big Picture

BudgeTrak is a personal-budgeting app for Android. Its central idea is a
single number on the home screen — **Available Cash** — that tells you
exactly how much you can safely spend right now without breaking any
commitment for the rest of the current budget period.

Most budgeting apps either show you what you spent (a rear-view mirror)
or hand you a static "budget" for each category that you then have to
manually track against. BudgeTrak does something different: it constantly
projects forward. It knows when your next paycheck arrives, when rent is
due, how much you're saving toward a vacation, and what big bills are
spread across the next several months. From all that, it computes one
honest number: what's truly yours to spend today.

If you have $500 in the bank, rent of $400 due in 10 days, and you're
saving $50 a month for an emergency fund, BudgeTrak doesn't tell you "you
have $500." It tells you what you can spend after honoring those
commitments. That number is what you'll see on the dashboard.

Everything else in the app — recurring expenses, savings goals,
amortized bills, the simulation, the calendar — exists to make that one
number as accurate as possible.

---

## 2. Concepts You'll See Everywhere

These terms come up across many screens. Understanding them once makes
the rest of the app feel obvious.

### Available Cash
The single number on your dashboard, shown on the flip-clock-style
display (the "Solari"). It's how much you can spend today without
overcommitting to anything else you've already set up. It's recomputed
every time you add or edit a transaction, every time you change a
recurring bill or savings goal, and every time the budget period rolls
over.

### Budget Period
The repeating window of time that your budget resets on. You pick the
length: **Daily**, **Weekly**, or **Monthly**. Each period gets its own
fresh allowance. The default is Daily for users who want fine-grained
discipline, but Weekly and Monthly work well too.

### Reset Time / Reset Day / Reset Date
When exactly the new period begins.
- **Daily**: a specific hour each day (default midnight, but you can
  shift it — many people pick 4 or 5 AM so the budget rolls over while
  they sleep instead of in the middle of a late-night shopping cart).
- **Weekly**: a specific day of the week.
- **Monthly**: a specific day of the month (1–28; the app coerces 29+
  to the last day of the month so February doesn't misbehave).

### Today's Floor
The minimum you should keep on hand right now to cover all your active
savings goals. If you have $300 already saved across your goals, your
floor today is $300. As you contribute more, the floor rises.

Available Cash is calculated to keep you above the floor. The
Simulation Graph draws the floor as a dashed blue line so you can see
how it grows over the next 18 months.

### Set-Aside
The portion of a recurring expense that's already been quietly reserved
from your budget toward its next payment. If rent is $1200 a month and
you're 10 days into the month, BudgeTrak has already "set aside" about
$400 of that — your Available Cash already reflects the reservation, so
when rent day comes there's no nasty surprise.

You see set-aside progress on each recurring expense as text like
"$400 of $1200 set aside." When the bill is paid (or simply when its
due-date passes), the set-aside resets to zero and starts climbing
again for the next cycle.

### Remembered Amount (Linked Transactions)
When you link a transaction to a recurring expense, income source,
amortized expense, or savings goal, BudgeTrak captures the entity's
amount at that moment as the "remembered amount" on that transaction.

This matters because if you later edit the recurring expense's amount
(rent goes up from $1200 to $1300, say), your old linked transactions
still know they were $1200 at the time — so your budget history stays
honest. The app asks "Apply to past linked transactions?" when you
change the amount, giving you the choice.

### Linking
You can connect a transaction to one of four other items:
- A **Recurring Expense** (e.g., this Netflix charge is the May
  subscription)
- An **Income Source** (this $2,400 deposit is May 1 paycheck)
- An **Amortized Expense** (this $1,200 charge is the auto-insurance
  policy we're spreading over 6 months)
- A **Savings Goal** (this $200 withdrawal is being pulled from the
  Vacation fund)

Linking changes how the transaction affects Available Cash. An unlinked
expense is a full hit to your budget. A linked one acknowledges
something the app already knows about and adjusts accordingly.

### Delete vs Unlink
This distinction is crucial because the math depends on it.

- **Delete** (you delete the recurring expense itself, or an income
  source, etc.): linked transactions keep their **remembered amount**.
  The app's reasoning is: "the bill still cost what it cost; you just
  stopped tracking it as recurring." No sudden swing in Available Cash.
- **Unlink** (you edit a transaction and remove the link manually):
  the remembered amount is **cleared to zero**. The app's reasoning is:
  "you're telling me this transaction was linked by mistake; treat it
  as a regular expense from now on." Available Cash adjusts to reflect
  the full transaction.

The rule applies to all four link types (Recurring Expense, Income
Source, Amortized Expense, Savings Goal).

For savings goals specifically, unlinking also restores the deducted
amount back into the goal's balance.

### Tiers: Free / Paid / Subscriber
Three levels of access. **Free** is the full core budgeting app
(unlimited transactions, periods, income sources, recurring expenses,
basic charts, joining a family sync group, English/Spanish). **Paid**
is a one-time purchase that unlocks file import/export (CSV/Excel/PDF),
unlimited home-screen widget transactions, cloud-synced receipt photos
in family sync groups, the Cash Flow Simulation screen, AI-assisted
auto-categorize on bank imports, full theme customization, and removes
ads. **Subscriber** is a monthly subscription on top of Paid that adds
the ability to create and admin a family sync group (up to 5 devices)
and AI receipt scanning (OCR). Subscribers also get everything Paid
includes.

If a user is a Subscriber, they automatically also have Paid features.

### Categories
The buckets you sort transactions into (Groceries, Gas, Rent,
Entertainment, etc.). BudgeTrak ships a default list and you can add
your own from Settings → Categories. A few categories are special and
can't be edited or deleted: **Other** (the fallback when nothing else
fits), **Recurring Income** (auto-assigned to income transactions),
and **Supercharge** (reserved for surplus moved into savings goals).

### Auto-Categorize
The app's attempt to assign the right category to a transaction without
you having to pick one every time. It looks at past transactions from
the same merchant and assigns the category you've used most often
there.

### Sync Group ("BudgeTrak SYNC")
A family or household setup where multiple devices share the same
budget data — same transactions, same recurring expenses, same savings
goals, all kept in step. The Subscriber who creates the group is the
admin; up to 5 devices total can be in one group. Data is encrypted
end-to-end before leaving any device.

---

## 3. Screens — A Tour

This section walks through every screen you'll see in normal use. Each
screen description covers what you see, what each control does, and the
practical reason you'd use it.

### 3.1 Dashboard (Home Screen)

The dashboard is the main screen you see when you open the app.

**Top app bar:**
- **Settings icon** (left, gear): opens Settings.
- **Logo** (center): just the BudgeTrak wordmark.
- **Help icon** (right, question mark): opens the in-app help page for
  the dashboard. The chatbot icon next to it opens the Help Chat (this
  conversation).

**Solari flip display** (the centerpiece):
A flip-clock-styled animation showing your Available Cash. The digits
flip individually with a mechanical "clack" sound as the number
changes. The currency symbol shows on the left; decimal places appear
only if you've enabled "Show decimal places" in Settings.

Below the main number is a smaller label showing the budget period
amount — for example "$100/day" or "$3,000/month". If you haven't set
up a budget yet, this area shows "Configure your budget" and tapping it
takes you to the Budget Config screen.

The flip sound can be silenced through your phone's normal sound
controls.

**Sync indicator** (bottom-left of the Solari, when you're in a sync
group):
A small icon plus colored dots, one dot per other device in your
group. The colors tell you the connection state:
- **Green**: connected and syncing normally.
- **Yellow**: connected but the listener that watches for updates
  isn't working — usually clears itself within seconds.
- **Red**: no internet.

The dots show the state of each other device in your group: green
(online now), dark blue (seen within the last hour), yellow (1–2
hours), red (more than 2 hours since seen).

**Supercharge bolt** (bottom-right of the Solari):
A yellow lightning-bolt icon that appears when you have more Available
Cash than your per-period budget AND at least one savings goal that
hasn't reached its target yet. The bolt pulses with a yellow-to-faded
animation to draw your attention.

Tap it to open the Supercharge dialog. From there you can either:
- **Reduce contributions**: lower the per-period contribution on a
  goal because you're ahead of schedule.
- **Achieve sooner**: dump some of the surplus into a goal right now
  to hit the target earlier.

**Spending chart section:**
Below the Solari is a chart of your spending by category. The header
bar above the chart has two controls:
- **Range pill** (left): tap to cycle through Today → This Week →
  7 Days → This Month → 30 Days → This Year → 365 Days → Today again.
- **View toggle pill** (right): switches between pie chart and bar
  chart.

The chart includes only EXPENSE transactions. It excludes transactions
that are linked to a savings goal (those are earmarked, not
discretionary), transactions linked to an amortized expense (already
budgeted), and any transaction marked "exclude from budget." You can
also pick which categories show up in the chart from Settings →
Categories (the "Charted" column).

**Pie chart**: each slice is one category. Slices that are 4% or more
of total spending show a small category icon in the middle of the
slice. Slices smaller than 4% get stacked in a side column with their
icons so they stay readable and tappable. Tap any slice or side-column
icon to see a toast with that category's name and total.

**Bar chart**: vertical bars left-to-right, ordered by total spent
(highest first). If you have many categories, the chart scrolls
horizontally. Tap a bar to see its category name and total.

The color palette comes from your theme's chart palette (Sunset,
Bright, Pastel, Mono, or one you've customized in Settings → Colors).

Your range and view-toggle choices persist across app restarts.

**Quick navigation bar** (bottom of dashboard):
A row of fixed icon buttons that always stay visible:
- **+** (pulsing blue plus on a person icon): opens Add Transaction
  in EXPENSE mode by default; you can toggle to INCOME at the top of
  the dialog.
- **List icon**: opens the Transactions screen.
- **Coin icon**: opens Savings Goals.
- **Clock icon**: opens Amortization.
- **Sync icon**: opens Recurring Expenses.
- **Calendar icon**: opens Budget Calendar.

**Back button on dashboard:**
Pressing back when you're already on the dashboard sends the app to
the background — it doesn't quit. Reopening returns instantly with
state preserved.

### 3.2 Transactions

The Transactions screen lists every transaction you've recorded. Tap
the list icon on the dashboard to open it.

**Top toolbar:**
- **Back arrow**: returns to wherever you came from.
- **Period selector pill**: cycles through All / Last 6 Months / Last
  3 Months. Restricts the visible list to that window.
- **Sort pill**: cycles through Date Descending (newest first) → Date
  Ascending (oldest first) → By Amount → By Category.
- **Category filter pill**: starts at "All." Tap to pick a single
  category and only show transactions in it.
- **Action icons** (right side): a plus icon (Add Expense), a minus
  icon (Add Income), a search icon, an import icon (CSV import), and a
  save icon (CSV/Excel/PDF export).

**Search menu** (from the search icon):
- **Text Search**: finds matches in merchant name and description.
- **Amount Range**: enter a minimum and maximum.
- **Date Range**: pick a start and end date.

**Transaction list:**
Each row shows:
- **Category icon** (left): tinted in the category's color if it's a
  custom category, dimmed if it's "Other" or uncategorized. Tap the
  icon to filter by that category.
- **Date** (e.g. 2026-05-19, format depends on your Settings).
- **Merchant / source name**.
- **Amount** (right): red for expenses, green for income.
- **Multi-category indicator**: if a transaction is split across
  several categories, a small list icon appears with an "N items"
  label. Tap to expand and see the per-category amounts.

**Tap a row** to open the Edit Transaction dialog (same fields as Add).

**Long-press a row** to enter selection mode. While in selection mode:
- Tap any row to add/remove it from the selection.
- Use the bar that appears at the top to bulk-edit: change category,
  change merchant text, mark as "verified" (thumbs-up), or delete.
- Tap the X to leave selection mode without acting.

**Swipe left on a row** to reveal the receipt-photo thumbnails for
that transaction. Tap a thumbnail to view it full-screen.

**Add / Edit Transaction dialog:**

This is the main dialog you'll use over and over. Fields:

- **Type toggle** (top): EXPENSE or INCOME. Add Expense and the plus
  icon button default to EXPENSE; Add Income and the minus icon
  button default to INCOME. You can flip the type either way once the
  dialog is open.
- **Date**: defaults to today; tap to pick a different date with the
  date picker.
- **Merchant / Source**: the place or person (a label that swaps
  between "Merchant" and "Source" depending on type). If you have
  Auto-Capitalize on (Settings), what you type is automatically
  title-cased ("coffee" → "Coffee").
- **Description**: optional notes, also auto-capitalized.
- **Link buttons** (a row of up to four icons): RE = Recurring
  Expense, AE = Amortized Expense, IS = Income Source, SG = Savings
  Goal. Each is enabled only if at least one entity of that type
  exists. Tapping a link button opens a radio-button picker showing
  candidates ranked by closeness (amount + date match). Choose one to
  link, or tap "Skip linking this transaction" to leave it unlinked.
  To remove a link manually, tap the X on the link badge — this
  unlinks AND clears the remembered amount.
- **Category selection**:
  - **Single-category mode** (default): a radio-button list. Pick one.
  - **Multi-category mode** (toggle a small pie icon): a pie editor.
    You can either drag slice edges to resize them or type per-category
    amounts. The amounts must add up to the total.
- **Amount**: stored as a positive number; the type (EXPENSE / INCOME)
  carries the sign. In multi-category mode, no individual slice can be
  negative.
- **Receipt photos** (up to 5 slots): tap an empty slot to capture
  from camera or pick from your photo gallery. Tap an existing slot
  to view full-screen. Long-press a thumbnail in the dialog to mark it
  as the OCR target (Subscriber feature; see §5.10).

**Save** writes the transaction. The app then runs a quick chain to
check whether the transaction matches anything:
1. **Duplicate?** If yes, a non-dismissable dialog asks Keep Existing /
   Keep New / Keep Both / Ignore All.
2. **Match a recurring expense, amortized expense, income source, or
   savings goal?** If yes, a match dialog appears with the best
   candidate pre-selected. Confirm the link or skip.

If you cancel out of these dialogs, the transaction is still saved
unlinked.

**Delete a transaction** from the row context menu or the bulk-delete
button. A confirmation dialog appears. Deletion is a soft removal —
data isn't gone immediately and will resync as deleted if you're in a
sync group. If the transaction was linked, deleting it leaves the
linked entity (RE/IS/AE/SG) alone; only the transaction goes away.

**Auto-categorize behavior during manual entry**: as you type the
merchant name, once you've typed at least the threshold number of
characters (Settings → Match Chars, default 5) and you haven't picked
a category yet, the app silently checks whether you have a clear
history of categorizing that merchant a particular way. If yes, it
fills in the category for you. It applies only if a real category
match exists (it doesn't default to "Other"). You can always override
manually.

**CSV import** (icon in the toolbar; full task recipe in §5.6):
You'll be asked to pick a format — Generic, US Bank, or BudgeTrak's
own save file. The app reads the file, optionally auto-categorizes the
rows, checks for duplicates, looks for matches against your recurring
expenses and income sources, and adds the transactions.

**Export** (save icon): pick CSV (BudgeTrak's full format that
preserves all the links), Excel (.xlsx, plain spreadsheet), or PDF
(printable expense reports with receipt photos included).

### 3.3 Recurring Expenses

This screen lists your repeating bills — rent, subscriptions,
utilities, anything that comes around on a schedule. The sync icon on
the dashboard opens it.

The list is grouped into three sections:
- **Monthly** (every-month items).
- **Annual** (yearly items).
- **Other** (daily, weekly, every-N-days, twice-monthly, every-N-months).

Each section has a sort toggle at the top — tap "A" to sort
alphabetically, tap the currency symbol to sort by amount. The choice
persists.

**Each row shows:**
- Name (bold) and optional description.
- "Next on $amount on [date]" — when the next occurrence hits.
- For items in the Other section, the cadence label (e.g., "Every 3
  days", "Every 2 weeks").
- "Set aside" progress: "$X of $Y set aside" — how much has been
  reserved toward the next payment. Turns green when the item is in
  Accelerated mode.
- A green speed-bolt icon if Accelerated mode is on.
- A delete button (red icon) on the right.

**Tap a row** to edit. **Long-press a row** to see the most recent
linked transactions (up to 10). If no transactions are linked yet,
you'll get a toast.

**Add / Edit dialog fields:**
- **Name** (required).
- **Description** (optional).
- **Amount** (required, must be > 0).
- **Repeat type**: pick from Every X Days, Every X Weeks, Every X
  Months, Twice Per Month, or Annual. ("Every 2 Weeks" as a separate
  option is no longer offered for new items — to set up a biweekly
  schedule, choose "Every X Weeks" and set the number to 2. Old
  biweekly items from before this change continue to work normally.)
- **Conditional fields** depending on type: an interval text field,
  a start-date picker, or two day-of-month numeric fields for "Twice
  Per Month."
- **Accelerated toggle** (green speed bolt at the bottom): turns
  Accelerated mode on or off. See §4.4 for what this does.

**Date validation**: for monthly intervals, the app will refuse to
let you pick a day-of-month above 28 (with a toast) so that February
doesn't misbehave. To pay on the last day of the month, pick the 28th.

### 3.4 Budget Config

This is where your budget lives — the period setup, your income
sources, and the option to override the calculated budget manually.

Reach it from Settings → "Configure Your Budget" button (or from the
Quick Start guide on first launch).

**Top of screen:**
- **Back arrow**, title "Budget Configuration", and the help icon.

**Period setup row:**
- **Budget Period dropdown**: Daily / Weekly / Monthly.
- **Reset button** (label changes with period): "Refresh Time" (Daily)
  / "Reset Day" (Weekly) / "Reset Date" (Monthly). Opens a small dialog
  to pick the hour, weekday, or day-of-month.

**Budget status:**
- **Safe Budget label**: e.g., "Safe Budget: $120.00 per Day." This is
  what BudgeTrak has calculated based on your income sources minus your
  annualized expenses, divided by your periods per year.
- **Tracking since** (small subtitle): the date you started.
- **Start / Reset Budget button**: orange-warning confirm dialog. This
  resets your "tracking since" date to today, clears the period
  history, and re-locks all your recurring expenses' and savings
  goals' set-aside counters. Use it when you want to start fresh
  without losing your transactions.

**Manual Budget Override:**
- Checkbox: when off (the default), Safe Budget is calculated
  automatically. When on, a numeric input appears so you can enter a
  fixed per-period budget that overrides the calculation.
- See §4.1 for the implications.

**Income Sources section:**
- Description text and an **Income Mode** button that cycles through
  three modes: FIXED → ACTUAL → ACTUAL_ADJUST → back to FIXED. (If
  Manual Budget Override is on, ACTUAL_ADJUST is skipped because it
  doesn't make sense in that combination.) See §4.2 for what each mode
  does.
- **Add Income Source button** (green +).

**Each income source row:**
- Name, optional description, amount, delete button.
- Tap to edit; the dialog matches the Add dialog.

**Add / Edit Income Source dialog fields:**
- **Source Name** (required).
- **Description** (optional).
- **Amount** (required, > 0).
- **Repeat type**: same options as Recurring Expenses (with the same
  biweekly note — pick "Every X Weeks" with the number 2 for biweekly
  income going forward).
- **Conditional fields** for the type you picked (interval, start
  date, or twice-monthly day numbers).

In a sync group, only the admin can change the period, the reset
timing, or the Income Mode. Members see those controls disabled with a
short "Admin only" message.

### 3.5 Savings Goals

This screen manages your goals — saving for a vacation, building an
emergency fund, anything you're accumulating for later. The coin icon
on the dashboard opens it.

**Top of screen:**
- Back arrow, a pause/play-all toggle (if you have at least one goal),
  title, and the help icon.

**"You need $X saved" info box:**
The most distinctive feature of this screen. The box tells you the
minimum cash cushion you should keep on hand right now to honor every
active goal's contribution schedule over the next 18 months without
ever dipping below the goals' growing totals. Tap the message to see
the "low-point date" — the worst day in the projection — as a toast.
A small green "Why?" link opens a longer explanation dialog.

This info box hides itself if you've turned on Manual Budget Override
in Budget Config, because the projection that drives it isn't running.

**Action buttons:**
- **Add Savings Goal** (green +).
- **View Simulation Chart** (chart icon). Paid and Subscriber feature
  — Free users see a toast prompting upgrade. Opens the Simulation
  Graph screen.

**Goals list, each row:**
- **Pause / Resume icon**: tap to pause or resume the goal's
  contributions.
- **Goal name** (bold).
- "Target: $X" subtitle.
- A line showing status:
  - "$X per [period]" for an active goal that hasn't been reached.
  - "Paused" (dimmed) for a paused goal.
  - "Goal reached" (green) when the saved amount equals or exceeds the
    target.
  - "Pays off on [date]" — when, at the current contribution rate,
    you'll reach the target.
- **Progress bar** (green) showing saved / target.
- "$saved of $target" label.
- Delete button on the right.

**Tap to edit; long-press to see linked transactions** (up to 10).

**Add / Edit dialog fields:**
- **Goal Name** (required).
- **Target Amount** (required, > 0).
- **Starting Saved Amount** (only when adding a new goal): use this to
  pre-populate if the goal already has money in it.
- **Contribution Per Period** (required, > 0): how much will be
  automatically reserved from your budget each period.
- **Calculate with Target Date button**: opens a date picker. Pick the
  date you want to reach the target by, and the app fills in the
  required Contribution Per Period (rounded). You can edit the result
  before saving. The target date itself is NOT saved — it's a one-shot
  calculator that just feeds the Contribution field.

When you set a contribution that's clearly above what your budget can
sustainably absorb, the "You need $X" message will rise to reflect
that; if it gets unrealistic, the Simulation Graph is the right place
to see when things break.

### 3.6 Amortization

Amortization is BudgeTrak's solution for big bills that come due
infrequently — a 6-month car insurance bill, an annual property tax,
the yearly Costco membership. Rather than letting those bills
torpedo your budget in the month they hit, you set them up here and
the app spreads the cost across all the periods between now and the
due date. The clock icon on the dashboard opens it.

**Each amortized entry has:**
- A name and optional description.
- A total amount.
- A number of periods to spread it over.
- A start date.

**Per-period deduction** = total amount / periods. That amount is
deducted from your Available Cash each period (it's a "set-aside"
similar to the one used for Recurring Expenses, but instead of paying
the bill at a recurring interval, it's accumulating toward a single
known event).

**Top of screen:**
- Back arrow, pause/resume-all toggle, title, help icon.

**Add Entry button** opens the dialog.

**Each row shows:**
- Pause/play icon.
- Name and description.
- "Total: $X / Per-Period: $Y" with the period label (day/week/month).
- Status: "Completed" (green) when fully amortized, "Paused" (dimmed)
  when paused, or "N of M [periods] complete" otherwise.
- Progress bar.
- "$paid / $total" line.
- Delete button.

**Long-press a row** to see the most recent linked transactions.

**Add / Edit dialog fields:**
- **Source Name** (required).
- **Description** (optional).
- **Total Amount** (required, > 0).
- **Total Number of Periods** (required, > 0).
- **Start Date** (required).

**When the actual bill comes in**, you'll record the transaction
normally and link it to the amortized entry. While the transaction is
linked to the amortized expense, it's skipped in cash calculations —
the per-period deductions have already done that work. If you later
delete the amortized entry, the transaction's "remembered amount" is
preserved so your books still balance.

### 3.7 Budget Calendar

A standard month grid that shows when income and expenses are
scheduled. The calendar icon on the dashboard opens it.

**Navigation:**
- Back arrow, title (e.g., "May 2026"), help icon.
- Arrows to change months. Swipe the grid left/right also navigates.

**Weekday headers** at the top, ordered Sunday-first or Monday-first
depending on your "Week Starts On" setting.

**Each cell** represents one day:
- **Background tint** indicates events:
  - Green = income scheduled.
  - Red = recurring expenses scheduled.
  - Split green/red = both.
  - Blue = budget reset day (only highlighted for Weekly and Monthly
    periods — Daily budgets reset every day, so no special highlight
    is needed).
  - White / default = nothing scheduled.
- **Today's cell** has a thicker primary-color border.
- **Date number** in the top corner.
- Below the number, small green text shows total income for the day
  (rounded), then small red text shows total expenses (rounded). If
  more than one event happens that day, a count appears like "[3]".

**Tap a cell with events** to open a Day Details dialog. The dialog
lists each scheduled income source and recurring expense for that
day with its amount, with totals at the bottom. Tapping outside or
the Close button dismisses.

### 3.8 Simulation Graph

A forward projection of your finances over the next 18 months. Paid
and Subscriber feature; Free users see an upgrade prompt instead. You
get here from the Savings Goals screen's "View Simulation Chart"
button.

**Top of screen:**
- Back arrow, title, help icon.

**Two input fields above the chart:**
- **Current Savings**: pre-populated with the "You need" amount from
  Savings Goals. Edit this to explore what-if scenarios — for
  example, "if I had $5000 instead of $3000, would I cross over the
  floor sooner?"
- **Over/Under Budget per [day/week/month]**: a positive or negative
  number that simulates spending more or less than planned each
  period. Positive = overspending; negative = saving more.
- If you go so far negative that your effective budget would be near
  zero or below, a red warning appears that the savings rate isn't
  sustainable.

**Chart area:**
- **Y-axis** (left): currency amounts.
- **X-axis** (bottom): dates rotated 60° for readability.
- **Solid colored line**: your projected Available Cash over time.
  Rises with income events, drops with expenses and budget deductions.
  The area below the line is shaded.
- **Dashed blue line**: the "floor" — the total amount you'd need to
  have already saved to be on track for all your goals at that moment
  in the future. It starts flat at your current total saved across
  goals, steps up at each period boundary as more contributions
  accrue, and plateaus when a goal reaches its target. Paused goals
  contribute their static saved amount.
- **Red dot**: marks the worst point in the projection — the date
  where the gap between your line and the floor is widest. That's the
  "low-point date" that the Savings Goals screen mentions.

**Zoom controls** (bottom-right, above the X-axis): minus to zoom
out, plus to zoom in. Pinch-to-zoom works too, and you can drag
left/right to pan through time.

### 3.9 Settings

A long scrollable page reached from the gear icon on the dashboard.
The sections, in order:

**Quick navigation** (non-scrollable buttons at top):
- **Quick Start Guide**: re-opens the onboarding walkthrough.
- **Configure Your Budget**: jumps to Budget Config.
- **Sync**: jumps to the Sync screen.

**Display & Format:**
- **Currency dropdown**: $ (USD) is the default; many currencies
  available (GBP, EUR, JPY, INR, KRW, MXN, and others). Affects the
  symbol and how many decimal places are shown by default. In a sync
  group, only the admin can change this.
- **Show Decimal Places checkbox**: when on, amounts display with
  decimals based on currency convention. When off, amounts round to
  whole units.
- **Date Format dropdown**: pick how dates display (ISO, US M/D/Y, EU
  D/M/Y, and others). A sample date previews the choice.
- **Week Starts On dropdown**: Sunday or Monday. Affects the calendar
  view; doesn't re-anchor weekly budgets retroactively.
- **Colors** "Edit…" button: opens the Colors screen.
- **Language dropdown**: English or Spanish.

**Matching Configuration:**
Four numeric settings that control how aggressively the app tries to
match transactions to recurring expenses, income sources, and
duplicates:
- **Match Days** (default 7): how many days before/after to look for
  a matching date.
- **Match Percent** (default 1.0): tolerance as a decimal multiplier
  (1.1 = ±10%).
- **Match Dollar** (default 1): dollar tolerance for amount match.
- **Match Chars** (default 5): how many initial characters of the
  merchant name must match. Lower = more aggressive matching (catches
  abbreviations and typos but risks false matches); higher = stricter.

In a sync group, all four are admin-only.

**Subscription:**
- Shows your current tier (Free / Paid User / Subscriber).
- Conditional upgrade buttons: "Upgrade to Paid (price)" if you're
  Free; "Subscribe Monthly (price)" if you're not already a
  Subscriber.
- **Restore Purchases** button — re-checks your Google account for
  past purchases.
- A "What does paid status do?" link that jumps to the upgrade
  callout in the help pages.

**Widget & Text Behavior:**
- **Show Widget Logo checkbox** (default on): show/hide the BudgeTrak
  logo on the home-screen widget.
- **Auto Capitalize checkbox** (default on): apply title case to
  merchant names and descriptions as you type.
- **AI CSV Categorize checkbox** (Paid+Subscriber only, default off):
  when on, CSV imports use AI to suggest categories for rows the
  basic matcher can't confidently categorize.

**Privacy:**
- **Send Crash Reports checkbox** (default on): when off, BudgeTrak
  doesn't send crash diagnostics or anonymous usage data.
- **Help Chat Consent checkbox** (default off): must be on to use
  this chatbot. Unchecking revokes consent; the consent dialog will
  re-appear next time you open the chat.

**Receipt Photos** (Paid+Subscriber section):
- Current cache size (informational).
- **Receipt Retention dropdown**: Keep All / 30 / 60 / 90 / 180 /
  365 days. Older photos are auto-deleted from local storage. Admin-
  only in a sync group.
- A button to manually push photos to cloud storage (sync feature).

**Backups:**
- **Enable Auto Backups checkbox**. When on:
  - Shows last/next backup dates.
  - **Frequency dropdown**: 1, 2, or 4 weeks.
  - **Retention dropdown**: keep 1, 10, or All backups. Default is
    10 (changed from 1 in an earlier update — heads up if you'd
    previously set it to 1 and were surprised to find 10 backup files
    on your phone).
  - **Backup Now** and **Restore Backup** buttons.
- Restore is disabled when you're in a sync group (you must leave the
  group first; restoring would clobber the group's data). An orange
  warning explains this.

**Data Management:**
- A counter showing active transactions vs. the archive threshold.
- **Archive Threshold dropdown**: 5K / 10K / 25K / Off (default 10K).
  When you exceed the threshold, the oldest transactions auto-archive
  to keep the live dataset fast. Archived transactions still exist
  but don't show in the main list. Admin-only in a sync group.

**Categories:**
A list of all your categories. For each:
- Icon + name.
- **Charted checkbox**: include in the dashboard's spending chart.
- **Widget checkbox**: make available in the widget's quick-add
  category picker.
- Tap the row to edit (rename, change icon, delete).

Protected categories (Other, Recurring Income, Supercharge) can't be
edited or deleted but their Charted/Widget toggles still work.

**Add Category** button at the bottom of the list opens a dialog to
pick an icon and name.

### 3.10 Colors

Theme customization. Reached from Settings → Colors. Free users have
access to the built-in themes; full color customization is part of
the Paid tier.

**Two built-in themes:**
- **Default**: light blue surfaces, teal headers.
- **Bubblegum**: a pink/light alternative.

Editing either built-in automatically forks it into a custom theme
called "<name> (Custom)" so you don't damage the original.

**Eight color roles** per theme (separate light/dark variants):
1. Header background
2. Header text
3. Page background
4. Window header (dialog header)
5. Window header text
6. Window background (dialog body)
7. General text
8. Solari background

**Chart palette** is independent of the theme — a separate set of 12
colors used for pie/bar charts. Built-in palettes include Bright,
Pastel, and Sunset. You can also create custom palettes.

**The Colors screen controls:**
- **Mode selector**: Light / Dark / Chart Light / Chart Dark.
- **Theme or Palette selector** (depending on mode).
- For Light/Dark modes: a **Slot selector** with 8 color names plus
  the current color swatch. Tap the swatch (or pencil icon) to open a
  full color picker (hue/saturation wheel, brightness slider, hex
  input).
- For Chart modes: a row of 12 color swatches — tap any to edit.
- **New Theme / New Palette** button to fork a current selection
  into an editable custom.
- **Undo icon** next to each slot — appears only when the slot has
  drifted from its source value; restores that one slot.
- **Delete** button (red) for custom themes/palettes only.
- **Sample preview** at the bottom showing how your choices will look
  in real dialogs or chart wedges.

Themes are local to each device — they're included in your encrypted
backups but are deliberately NOT shared between devices in a sync
group, so each family member can use their own look on their own
phone.

### 3.11 Sync Screen

Multi-device sharing of your budget data. Reached from Settings →
Sync.

**If you're not in a group yet:**
- **Create Group** button (Subscriber only — Free and Paid users can
  only join existing groups).
- **Join Group** button (available to everyone).

**Create Group flow** (Subscriber only):
1. Enter a device nickname (up to 20 characters; this is what other
   devices in the group will see).
2. Confirm.
3. You're now the admin of a new group with just your device in it.

**Join Group flow** (any user):
1. Get a 6-character pairing code from the admin (they generate it on
   their device).
2. Tap Join Group, enter the code, enter your device nickname.
3. Read and confirm the warning (joining means your budget data is
   replaced by the group's data — you're not merging, you're joining
   an existing budget).
4. Wait briefly while the initial sync runs.

**Once you're in a group:**
- **Status section** (top): a colored dot and short label.
  - Green = synced.
  - Yellow = syncing in progress.
  - Orange = changes pending upload.
  - Red = sync error (with a short message).
  - Gray = sync isn't running.
  - "Last synced" timestamp updates every few seconds.
- **Device list**: each row shows the device nickname, an admin badge
  if it's an admin, a "This device" tag on yours, an online-status
  dot (green now / yellow recent / orange stale / red long-offline),
  and a relative "online now / 2 minutes ago" time.
- Tap any device row to rename it.
- **Long-press a non-admin device** (admin only) to remove it from
  the group.

**Admin-only buttons:**
- **Generate Code**: makes a 6-character pairing code that expires
  after one use. Shows it in a dialog with a copy button.
- **Set Group Timezone**: pick one of about 25 common timezones. The
  group's budget periods all roll over in this timezone, regardless
  of where each device physically is.
- **Repair Attributions**: fix stale device names that show up
  attached to old transactions (e.g., after renaming a device).
- **Dissolve Group**: red button at the bottom. Permanently breaks
  the group; every member device is evicted. Requires confirmation.

**Member limit**: groups can have up to 5 devices. When full, the
Generate Code button is disabled and shows a "Member limit reached"
toast.

**Leaving a group**:
- Non-admin members get a **Leave Group** button (with confirmation).
- Admins must either transfer admin to another member or dissolve
  the group entirely.
- After leaving, your device reverts to solo mode with whatever data
  was last synced from the group as your starting point.

**End-to-end encryption**: your data is encrypted on each device
before it ever leaves. The cloud servers never see your transactions,
amounts, merchants, or any of the values themselves — only encrypted
blobs they pass between devices in your group. This is why you can't
be locked out of your data and also why customer support can't see
your finances.

### 3.12 Widget

A home-screen widget that shows Available Cash plus quick-add
buttons. Add it like any Android widget (long-press your home screen
→ Widgets → BudgeTrak).

**Sizes:**
- **2×1** (small): just the flip display.
- **4×1** (default): flip display + button bar.
- **Larger**: same layout scaled up.

**Buttons** (only visible on 4×1 and larger):
- **Plus button** (left): Add Income (quick-add). Opens a minimal
  version of the transaction dialog.
- **Minus button** (right): Add Expense (quick-add).

**Daily transaction limit on the widget**: Free users are limited to
1 widget transaction per day. Paid and Subscriber users have no
limit. (You can still add as many transactions as you want from the
main app; the limit only applies to the widget shortcut.)

**Tap the flip display or logo** to open the full app.

**Theme awareness**: the widget reads your system's light/dark mode
and renders the card colors accordingly. The BudgeTrak logo uses a
fixed light-blue tint so it stays visible across all themes.

**Refresh**: the widget updates automatically whenever Available Cash
changes, when a new period starts, or when data syncs from another
device. There's a brief throttle (about 5 seconds) on back-to-back
updates to avoid flicker.

---

## 4. How Your Money Math Works

Concept explanations for the non-obvious parts of the budgeting model.

### 4.1 How Available Cash is Calculated

In broad strokes, Available Cash is the difference between:
- **What you have**: the budget-period credits that have accumulated
  in your favor over time (think of these as small daily/weekly
  deposits the app makes on your behalf based on your Safe Budget),
  plus or minus the impact of your transactions.
- **What you've committed**: the set-aside reservations for upcoming
  recurring expenses, the accelerated extras if you've turned on
  acceleration, the per-period contributions to your savings goals,
  and the per-period deductions for any amortized expenses in
  progress.

The exact mechanics:

1. **Safe Budget** is computed from your income sources and expenses:
   ```
   Safe Budget per period =
       (total annual income − total annual recurring expenses
        − total annual savings contributions − total annual
        amortization deductions) / periods per year
   ```
   If this is positive, you've got room to spend that much each
   period. If it's negative, you're over-committed (and you'll see
   warnings).

2. **Period ledger**: at each period rollover, BudgeTrak records the
   Safe Budget for that period as a fresh "credit." Available Cash
   sums up all those credits since you started tracking.

3. **Transactions** then adjust the balance. Expenses subtract;
   income from a non-linked source adds; linked income behavior
   depends on your Income Mode (see §4.2).

4. **Linked transactions** can also adjust by a delta:
   `transaction amount − remembered linked amount`. If you spent more
   than expected on a linked recurring expense, the overage hits
   cash; if you spent less, the savings is credited.

**Manual Budget Override** (Settings → Budget Config) replaces step 1
with a fixed number you enter. The rest of the math still applies.
You'd use this if you want a simple round figure (say, "$100/day")
that ignores the income/expense balancing — useful for cash-envelope
budgeting or when your real income is too irregular to project.

### 4.2 Income Mode (FIXED / ACTUAL / ACTUAL_ADJUST)

This is one of the more nuanced concepts. It lives in Budget Config
and controls how transactions linked to income sources affect
Available Cash.

**FIXED (default).** Linked income transactions have ZERO effect on
Available Cash. The income is already part of your Safe Budget; when
the actual paycheck transaction lands and you link it to the income
source, the math is "yes I already counted this, nothing to add." Use
FIXED when your income is reliable and you've set the expected amount
correctly — most salaried employees should pick FIXED.

*Day 1 example:* You have a $5,000 monthly income source set up. Your
Safe Budget is computed assuming you'll get that $5,000. Day 1 of the
month, Available Cash starts at the budgeted amount. When your $5,000
paycheck lands and you link it to the income source, Available Cash
doesn't change — it was already accounted for.

**ACTUAL.** Linked income transactions affect cash by the difference
between actual and expected. If your income source expects $5,000 and
you get $5,300, that's +$300 to cash. If you get $4,700, that's −$300
from cash. Use ACTUAL when your income varies but you have a
reasonable baseline.

*Day 1 example:* You're a freelancer with a $2,000 expected income
per month. Safe Budget reflects $2,000. A client pays you $2,500 and
you link the transaction to the income source. Available Cash gains
$500 (the over-and-above). If the client had only paid $1,500,
Available Cash would have dropped by $500.

**ACTUAL_ADJUST.** The income source's amount auto-updates to match
the actual transaction. The delta is always zero (no swing in
Available Cash from the transaction itself), but the Safe Budget
recalculates for future periods based on the new amount. Use when
you want to track actual income without budget swings; future
periods will reflect the new reality. Cannot be combined with Manual
Budget Override (the dropdown skips it).

*Day 1 example:* Same $2,000 freelancer. You get $1,800. The income
source quietly updates to $1,800 and recalculates Safe Budget
downward (so next month's planning reflects the lower expected
amount). Available Cash this period doesn't swing.

**Which mode to pick:**
- FIXED: salaried jobs, regular stipends, predictable annuities.
- ACTUAL: bonuses, commissions, side gigs, hourly work with some
  variability.
- ACTUAL_ADJUST: want a self-correcting "what actually came in"
  record without budget shocks.

### 4.3 What Happens at Period Rollover

At the reset time you've configured, BudgeTrak silently runs a
sequence:

1. The Safe Budget for the new period gets recorded as a fresh
   credit.
2. Each recurring expense advances its set-aside: if an occurrence
   falls in the new period, the set-aside resets to zero; otherwise
   the next portion accumulates.
3. Each active savings goal contributes its per-period amount (or
   the remaining balance, whichever is smaller). If the goal hits
   its target, it stops contributing and is marked "Goal reached."
4. Each in-progress amortized expense deducts its per-period share.
5. Available Cash recomputes.

You won't see a dialog or notification when this happens — it's
deliberately invisible. The next time you open the app (or look at
the widget), Available Cash will reflect the rollover.

**Missed periods**: if your phone was off or the app hadn't run for
several periods, BudgeTrak catches up on all the missed rollovers at
once when you next open it. You won't lose any periods.

**Why pick a non-default reset hour:**
- **Night shift**: you finish work at 3 AM and the grocery run after
  work shouldn't count against "tomorrow's" budget. Pick a reset
  hour of 4 AM (or later in your workday) so your "day" aligns with
  your wake cycle.
- **Late-night shoppers**: setting the reset to 1 AM keeps 11 PM
  spending in the same day's allowance, which can help discipline.
- **Cross-timezone families** (sync groups): the admin picks a group
  timezone in the Sync screen so all devices roll over together.

### 4.4 Set-Aside Math for Recurring Expenses

The set-aside is the running tally of how much of an upcoming
recurring-expense payment has been quietly reserved from your budget.

**Normal rate**:
```
normal rate per period = expense amount × annual occurrences / periods per year
```

If rent is $1,200 monthly and your budget period is Daily, the
normal rate is `$1,200 × 12 / 365.25 ≈ $39.42 per day`. Each day,
$39.42 of your budget is reserved toward rent.

You see the running total as "$X of $Y set aside" on the recurring
expense row. When the bill's due date arrives, the set-aside resets
to zero and starts accumulating for the next cycle.

**Accelerated mode** (green speed-bolt toggle on the RE):

Sometimes the normal rate isn't fast enough — maybe you didn't have
the recurring expense set up until 2 weeks before it was due. With
Accelerated on, the app aims to fully fund the next occurrence in
time, even if that means a bigger per-period deduction than the
normal rate.

```
accelerated rate per period = (expense amount − amount already set aside)
                              / (periods until next occurrence)
extra deduction per period  = max(0, accelerated rate − normal rate)
```

That `extra deduction` comes out of your budget each period, which
lowers Available Cash. Once the bill is funded, the extra
deduction drops to zero automatically.

**Example.** Annual car insurance of $1,200 due in 30 days, Daily
period, no set-aside yet:
- Normal rate ≈ $3.29/day. After 30 days you'd have ~$98 set aside —
  way short of $1,200.
- Accelerated rate = $1,200 / 30 = $40/day.
- Extra deduction ≈ $36.71/day. Your Available Cash drops by
  $36.71/day for 30 days, but you finish the period with the bill
  fully funded.

The green color and bolt icon are visible cues that acceleration is
on. Once the bill is paid, the set-aside resets and acceleration
automatically backs off until needed again.

### 4.5 How Savings Goals Affect Available Cash (the Floor)

A savings goal does two things to your math:
1. It deducts the per-period contribution from your Safe Budget
   calculation, lowering the amount you can freely spend each period.
2. It contributes to the **floor** — the minimum cash cushion you
   should keep on hand to stay on track for all your goals.

The floor is what the Simulation Graph's dashed blue line shows. It
starts at the total you've already saved across all active goals and
grows over time as future periods' contributions accrue. When a goal
reaches its target, its contribution to the floor plateaus.

The "You need $X saved" message on Savings Goals tells you the
biggest gap between the floor and your projected Available Cash
balance over the next 18 months. If the projection always stays
above the floor, the message says you need $0 — you're in great
shape. If there's a future month where your projected cash dips
below the floor, the message tells you how much extra you'd need to
have today to bridge that dip.

The low-point date (the worst day in the projection) is what the
red dot on the Simulation Graph marks. Tapping the "You need" info
box on Savings Goals also surfaces this date as a toast.

**Paused goals** stop contributing to the floor's growth and stop
deducting from your budget, but their already-saved amount still
counts as part of the static floor base.

### 4.6 Amortization: Spreading a Big Bill

The model is simple: a known total cost, a known number of periods
to spread it over.

```
per-period deduction = total amount / total periods
```

Each period, that amount is subtracted from Available Cash and the
"set-aside" for that amortized entry grows by the same amount.
Tracking the progress shows you how close you are to having the
whole thing funded.

**When the actual bill comes in** — say the $1,200 insurance bill
hits your card — you record the transaction and link it to the
amortized entry. While linked, the transaction itself is invisible
to the cash calculation (it's not deducted again, because the
per-period deductions have been silently funding it all along).

**Why you might delete an amortized entry**: maybe you cancelled the
insurance, or you switched providers and want to set up a fresh
amortization. Deleting preserves the linked transactions' remembered
deductions so your history doesn't suddenly re-spike.

### 4.7 Supercharge: Surplus Cash → Savings Goals

The yellow Supercharge bolt on the dashboard appears when you have
spare cash and at least one savings goal that's not yet at target.
Tapping it opens a dialog with two modes:

**Reduce Contributions.** You're saving faster than needed, so lower
the per-period contribution rate on each goal. Frees up budget
without abandoning the goal. The goal's totalSaved is unchanged; the
contributionPerPeriod gets reduced for each active goal proportional
to your surplus.

**Achieve Sooner.** Pull from Available Cash right now and dump it
into goals to hit the target earlier. Available Cash drops; the
goal's saved amount jumps. You can optionally also increase the
contribution rate for further acceleration.

In both modes, you can pick which goals to apply the surplus to and
in what proportions.

Supercharge is a one-time action — it doesn't store a "mode" on the
goal. After you apply it, the goal carries on with the new
contribution rate and saved amount.

### 4.8 What Linking Does (And "Apply to Past?")

When you link a transaction to a recurring expense, income source,
amortized expense, or savings goal, the app:

1. Captures the entity's current amount as the "remembered amount"
   on the transaction.
2. Treats the transaction specially in the cash calculation:
   - **Linked RE/IS**: the cash effect is the delta between actual
     and remembered (zero if they match).
   - **Linked AE**: the transaction is invisible to cash math (the
     per-period deductions already covered it).
   - **Linked SG**: the amount is pulled from the goal's saved
     balance; the transaction itself doesn't dent Available Cash.

When you edit a recurring expense or income source's amount, the
app asks **"Apply this change to past linked transactions?"**

- **Yes** updates the remembered amount on every existing linked
  transaction to the new value. Available Cash recalculates with the
  new amount. Use this when the change should be retroactive (e.g.,
  you realize you'd been entering the wrong amount).
- **No** leaves past transactions with their old remembered amounts
  intact. Only future linked transactions will use the new amount.
  Use this when the change is genuinely going forward (rent went up
  starting next month).

### 4.9 Delete vs Unlink: What Gets Preserved

The pair of rules here matters because the math depends on it:

**Delete the entity** (RE, IS, AE, or SG) → linked transactions keep
their `linkedAmount`. The link ID is cleared. The transaction
remains in your books with its remembered amount intact, so
Available Cash doesn't suddenly swing as if the past expense never
happened.

**Manually unlink a transaction** (open Edit, tap the X on a link
badge) → both the link ID AND the remembered amount are cleared.
The full transaction amount applies to your budget as if it had
never been linked. For savings goals, the unlinked amount is also
restored to the goal's balance.

The same rule applies across all four link types.

**Why this asymmetry**: deleting the entity means "I'm done tracking
this category of thing, but the past expenses really happened."
Manually unlinking means "I linked this in error, treat the
transaction as normal." Two different intents, two different
mathematical outcomes.

### 4.10 Auto-Categorize: When It Fires

BudgeTrak runs auto-categorization in three different places, with
slightly different rules.

**1. Manual transaction entry** (Add or Edit Transaction dialog):
As you type the merchant name, once you've typed at least the
Match Chars threshold (Settings, default 5) AND you haven't already
picked a category, the app silently looks at past transactions from
similar merchants. If there's a clear category history, it applies
that category. It will NOT default to "Other" — if there's no real
match, it leaves the category blank for you to pick. It only fires
once per dialog session (resets if you clear the merchant name).

**2. CSV bank import**:
Every row from a bank-format CSV is auto-categorized using the same
merchant history. Falls back to "Other" if no match is found. With
**AI CSV Categorize** turned on (Settings, Paid+Subscriber only),
rows that the basic matcher couldn't confidently categorize are
also passed through AI for a smarter guess.

**3. BudgeTrak's own save-file CSV import**:
Auto-categorize is skipped entirely. The save file already includes
the categories from the originating device, so re-categorizing
would just create needless work.

**Merchant matching rules** (used by all three):
- Names are compared after stripping non-alphanumeric characters,
  case-insensitively: "Wal-Mart" matches "WALMART" matches "walmart".
- The first N characters (N = Match Chars) must match.
- Among matching merchants, the most common category wins; ties go
  to the most recent.

To make auto-categorize more aggressive, lower Match Chars (catches
abbreviations and typos but risks false matches). To make it
stricter, raise it.

### 4.11 Duplicate Detection

When a transaction is added (manually, via widget, or via CSV
import), the app checks whether it might duplicate an existing
transaction. The check uses three tolerances:

- **Match Days** (default 7): how close the dates can be.
- **Match Dollar** (default 1) and **Match Percent** (default 1.0):
  how close the amounts can be (either tolerance is enough).
- **Match Chars** (default 5): merchant name must share at least
  that many leading characters.

If all three pass, the duplicate dialog appears. It's intentionally
non-dismissable (no tap-outside, no back button) so duplicates
aren't silently dropped:

- **Keep Existing**: discard the new transaction; keep what was
  already there.
- **Keep New**: replace the existing with the new.
- **Keep Both**: add both. Use when two genuine transactions happen
  to look alike.
- **Ignore All**: keep the existing for this one AND skip the
  duplicate check for any further duplicates in the same import.

If you tighten the tolerances (smaller Days, smaller Dollar,
smaller Percent, larger Chars), you'll see fewer duplicate prompts
but might miss real duplicates. If you loosen them, you'll catch
more potential duplicates but get more dialogs.

---

## 5. Common Tasks (Recipe-Style)

Step-by-step recipes for what most users want to do.

### 5.1 Setting Up Your First Budget

1. On first launch, the Quick Start guide walks you through the
   basics. You can revisit it any time from Settings → Quick Start
   Guide.
2. Pick a budget period: Settings → Configure Your Budget → Budget
   Period (Daily / Weekly / Monthly). Daily is the most common.
3. Pick a reset time. The default is midnight; choose something
   that fits your sleep/work schedule.
4. Add at least one income source: Add Income Source button. Enter
   the name (e.g., "Paycheck"), amount, and frequency (e.g., Every
   2 Weeks).
5. Optional: turn on Manual Budget Override and enter a flat
   per-period amount if you'd rather not have the app calculate it.
6. Add your recurring expenses (rent, utilities, subscriptions) from
   the Recurring Expenses screen.
7. Back on the dashboard, you'll now see your Available Cash. Start
   adding transactions with the + button at the bottom.

### 5.2 Adding a Paycheck or Recurring Income

1. Open Settings → Configure Your Budget → Add Income Source.
2. Enter the name, amount, and frequency. For biweekly paychecks,
   pick "Every X Weeks" and set the number to 2.
3. Pick the start date (the date of your next expected paycheck).
4. Save.
5. Pick your Income Mode (FIXED, ACTUAL, or ACTUAL_ADJUST) based on
   how variable your income is — see §4.2.
6. When the actual paycheck arrives, record it as a transaction
   (use the red − button on the widget or the + → toggle INCOME on
   the dashboard). When the match dialog appears, link it to your
   income source.

### 5.3 Adding a Monthly Bill

1. Tap the sync icon on the dashboard to open Recurring Expenses.
2. Tap Add Recurring Expense.
3. Enter the name (e.g., "Rent"), amount, and frequency.
4. For a monthly bill, pick "Every X Months" with 1, and choose the
   day of the month.
5. Pick a category (Housing, Utilities, etc.).
6. Save.
7. The set-aside will start accumulating immediately. When the
   actual payment hits, record it as a transaction and link it to
   the recurring expense.

If you set this up partway through a period and want the set-aside
to catch up by the due date, tap the green speed-bolt icon in the
Add/Edit dialog to enable Accelerated mode. Available Cash will
drop more aggressively for a few periods to ensure the bill is
fully funded.

### 5.4 Saving for a Vacation (Target-Date Helper)

1. Tap the coin icon on the dashboard to open Savings Goals.
2. Tap Add Savings Goal.
3. Enter the name (e.g., "Hawaii Trip"), target amount (e.g.,
   $3,000), and starting saved amount (any money you already have
   earmarked).
4. Tap "Calculate with Target Date" and pick the date you want to
   leave by.
5. The Contribution Per Period field fills in automatically with
   the amount you'd need to save each period to hit your target by
   that date. You can edit before saving (rounded up to a friendlier
   number, for instance).
6. Save.
7. As you save, the progress bar fills. The Simulation Graph (Paid
   tier) shows how this goal fits into your overall projection.

### 5.5 Setting Up Car Insurance Amortization

You pay $1,200 every 6 months for car insurance and don't want it
to wreck the month it hits.

1. Tap the clock icon on the dashboard to open Amortization.
2. Tap Add Entry.
3. Source Name: "Car Insurance."
4. Total Amount: 1200.
5. Total Number of Periods: 6 if your budget is Monthly, ~183 if
   Daily. Pick a number that means "how many of my budget periods
   should this spread across."
6. Start Date: today (or the start of when you want the spread to
   begin).
7. Save.
8. Each period, ~$200 (or whatever the per-period amount is) will
   be silently deducted from Available Cash and credited toward
   funding the bill.
9. When the actual $1,200 insurance bill comes in, record it as a
   transaction and link it to the amortized entry. The transaction
   won't double-deduct from your budget.

### 5.6 Importing Transactions from Your Bank

This is a Paid feature.

1. Download a CSV statement from your bank's website (most banks
   offer this; look for "export transactions").
2. In BudgeTrak, open Transactions → import icon (top-right tray).
3. Pick a format:
   - **Generic**: the app auto-detects the structure. Works with
     most banks.
   - **US Bank**: specifically formatted for U.S. Bank exports.
   - **BudgeTrak CSV Save File**: only for files you previously
     exported from BudgeTrak itself (preserves all the link data).
4. Pick the file from your phone's storage.
5. The app parses the file and (for Generic/US Bank) auto-
   categorizes the rows.
6. You'll then walk through any duplicate dialogs and any match
   dialogs (where the importer recognized a recurring expense or
   income).
7. A toast confirms "Loaded N of M transactions."

If you have AI CSV Categorize on (Settings, Paid+Subscriber only),
rows the basic matcher wasn't sure about will be passed through AI
for a smarter guess.

### 5.7 Sharing a Budget with Your Family (SYNC)

This requires one Subscriber on the family who will admin the group.

**On the Subscriber's device (the admin):**
1. Settings → Sync → Create Group.
2. Enter a device nickname (e.g., "Dad's phone").
3. The group is created. You're the admin.
4. Tap Generate Code to make a 6-character pairing code.

**On each member device** (can be Free or Paid; doesn't need to be
Subscriber):
1. Make sure the device's existing data is backed up if you want to
   keep it — joining a group replaces local data with the group's
   data.
2. Settings → Sync → Join Group.
3. Enter the code and a device nickname (e.g., "Mom's phone").
4. Confirm the warning.
5. Wait briefly while the initial sync runs.

**Member limit**: 5 devices total (including the admin's). At the
limit, the Generate Code button is disabled.

**Once everyone's in**: any change made on one device propagates to
the others within seconds (when devices are online).

**Leaving a group**: members can tap Leave Group anytime. Admins
have to either transfer admin status to another member or dissolve
the group.

### 5.8 Backing Up Your Data (and Restoring Later)

**To back up:**
1. Settings → Backups section.
2. Tick Enable Auto Backups. Pick a frequency (1, 2, or 4 weeks)
   and retention (1, 10, or All copies).
3. The first backup runs immediately. Subsequent backups run on
   their schedule.
4. Backup files are saved in your phone's Download/BudgeTrak/backups/
   folder. You can copy them to Google Drive, OneDrive, or any
   other cloud service for extra safety.
5. You'll be prompted to set a backup password the first time. The
   password encrypts your backup files; the app cannot recover this
   password if you forget it.

**To restore:**
1. If you're in a sync group, leave it first (Sync → Leave Group).
   Restoring is blocked while in a group because it would clobber
   the group's data.
2. Settings → Backups → Restore Backup.
3. Pick the backup folder from your phone's file picker (usually
   Download/BudgeTrak/backups/).
4. Pick the specific backup file to restore from.
5. Enter the backup password.
6. The app replaces your current data with the backup's contents.

**Pre-restore snapshot**: just before a restore runs, BudgeTrak
saves a snapshot of your current state to
Download/BudgeTrak/support/ as an emergency-undo file in case the
restore was a mistake. It's a one-time safety net, not a regular
backup.

### 5.9 Attaching a Receipt Photo

Available to all users (locally; cloud sync is Paid+).

1. Open the transaction (Add or Edit).
2. Find the receipt photo slots near the bottom of the dialog
   (up to 5 per transaction).
3. Tap an empty slot. Pick Camera (take a new photo) or Gallery
   (pick an existing photo).
4. The photo is compressed for storage and added to the
   transaction.
5. Tap a thumbnail to view full-screen (with zoom, pan, rotate,
   delete).
6. To reorder, long-press a thumbnail in the dialog and drag it
   to a new position.
7. To remove a photo, open it full-screen and tap the red delete
   icon, or swipe-left on the thumbnail in the dialog.

In a sync group with at least one Paid member, photos sync to the
other devices in the group automatically (Paid+ on both sides;
Free users can capture locally but won't see photos from others).

### 5.10 Scanning a Receipt with OCR

Subscriber-only feature. Saves typing for paper receipts.

1. Attach the receipt photo first (§5.9). It must be in the
   transaction dialog already.
2. Long-press the receipt thumbnail to mark it as the OCR target
   (a blue outline appears).
3. Tap the sparkle icon that appears above the photo slots.
4. The app analyzes the photo and fills in the merchant name,
   date, amount, and category breakdown.
5. Review the results, edit anything that's wrong, and save the
   transaction.

OCR works best on clear, well-lit receipts with the totals
clearly visible. Crumpled or faded receipts may not extract
cleanly.

### 5.11 Customizing Your Theme

Available to all users; custom themes are part of the Paid tier.

1. Settings → Colors. The screen opens to your current theme in
   Light mode.
2. Pick a different built-in theme from the dropdown if you want
   a starting point (Default or Bubblegum).
3. To create a custom theme, tap **New Theme** and give it a name.
4. Pick the Mode you want to edit: Light, Dark, Chart Light, or
   Chart Dark.
5. For Light/Dark: pick a slot (Header background, Page background,
   etc.) and tap the color swatch to open the picker. Adjust hue,
   saturation, brightness, or paste a hex code.
6. For Chart Light/Dark: tap any of the 12 chart-color swatches to
   edit it.
7. The Sample preview at the bottom shows how your changes look in
   real dialogs or chart wedges.
8. Tap the undo icon next to any slot to revert just that slot
   back to the built-in's value.

Themes are local to each device — they're included in your
encrypted backups but are NOT shared across sync group devices, so
each family member can have their own look.

### 5.12 Upgrading and Restoring Purchases

**To upgrade:**
1. Settings → Subscription section.
2. Tap the upgrade button for the tier you want (Upgrade to Paid or
   Subscribe Monthly).
3. The Google Play purchase flow opens. Confirm your purchase.
4. Once the purchase completes, the new features unlock immediately.

**To restore purchases** (if you've reinstalled the app or switched
devices):
1. Make sure your phone is signed into the same Google account that
   you used for the original purchase.
2. Settings → Subscription → Restore Purchases.
3. The app checks with Google Play and unlocks any purchases
   associated with that account.
4. A toast confirms the result.

If you have an active Subscriber subscription, you'll see a daily
prompt if it's about to expire so you can renew. If the
subscription does expire, you'll revert to the Paid tier (you'll
keep features that one-time-purchase Paid would have, but lose the
Subscriber-only ones like creating sync groups and OCR). In a sync
group, an expired Subscriber's group enters a 7-day grace period
before being affected.

---

## 6. Errors, Warnings, and Surprising Situations

What unusual messages mean and what to do about them.

### "Available Cash is negative"
You've committed more (recurring expenses + savings contributions +
amortization deductions) than your income supports. Options:
- Reduce a recurring expense or savings goal contribution.
- Pause a savings goal.
- Re-evaluate your income source amounts (maybe one is set lower
  than reality — see §4.2 for income modes).
- Use Manual Budget Override if you'd rather pick a flat number
  while you sort it out.

The Simulation Graph is the right tool to see when and how this
unfolds — it'll show the dashed floor line crossing above the cash
line.

### "Why?" link on Savings Goals
Tap it for a longer explanation of why you need a certain cash
cushion. Tied to the math in §4.5.

### Budget Reset notifications/markers
The Budget Calendar tints reset days blue for Weekly and Monthly
budgets. (Daily budgets reset every day, so there's nothing special
to highlight.) Tapping a tinted cell shows the Day Details dialog
with the reset noted.

### "Member limit reached" (SYNC)
A group can hold up to 5 devices including the admin. You'll see
this when trying to generate another invite code while at capacity.
Remove a member (long-press a non-admin device) to make room.

### "You must leave the sync group before restoring a backup"
Restoring a backup would clobber the group's data with your local
backup, which isn't safe. Leave the group first (Settings → Sync →
Leave Group), then restore.

### "Wrong password or corrupted backup"
The password you entered doesn't decrypt the backup file. Either
you've typed it wrong, or the file is damaged. There is no
password-recovery mechanism — the app can't recover backups whose
passwords have been lost.

### "Subscription expires in N days"
A reminder before your Subscriber subscription auto-renews or
expires. Tap Renew to manage the subscription in Google Play.

### Sync status indicator shows yellow / red on the Sync screen
Yellow ("listeners down") usually means a brief network blip — wait
a few seconds and it should go back to green. Red means no
internet — check your phone's connectivity. If neither resolves
within a few minutes, force-close and reopen the app.

### "This savings rate is unsustainable" (Simulation Graph)
On the Simulation Graph, the "Over/Under Budget" field is set to a
negative amount so large that your effective budget would be near
zero. The simulation can't extrapolate from there meaningfully. Use
a smaller (less negative) number, or reduce your savings goal
targets.

### Photos that don't appear (sync group)
If you joined a group with a large photo history, photos may take
time to download as you scroll. A thumbnail placeholder appears for
photos that haven't downloaded yet — tapping shows a toast. They'll
appear automatically once the download completes.

Local photo retention (Settings → Receipt Photos) automatically
deletes photos older than your chosen age (default Keep All). If
you set retention shorter, older receipts disappear from your
device — though the transactions remain.

---

## 7. Tier Quick-Reference

### Free
- Unlimited budgets, transactions, recurring expenses, income
  sources, savings goals, amortization entries, and categories.
- Daily, Weekly, or Monthly budget periods.
- Manual transaction entry, search, sort, filter.
- Spending charts (pie + bar) on the dashboard.
- Budget Calendar.
- Auto-categorize during manual transaction entry.
- Home-screen widget with 1 quick-add transaction per day.
- Receipt photos stored locally (up to 5 per transaction).
- Join existing sync groups (cannot create or admin).
- Built-in themes (Default and Bubblegum).
- English and Spanish languages.
- Local encrypted backups with password protection.
- Ad-supported.

### Paid (one-time purchase)
Everything in Free, plus:
- CSV import (Generic, US Bank, BudgeTrak CSV Save File).
- CSV / Excel / PDF export.
- Unlimited home-screen widget transactions per day.
- Receipt photos sync to other devices in your sync group (admin
  needs Subscriber, but Paid members can both upload and view).
- Cash Flow Simulation Graph.
- AI CSV Categorize toggle in Settings.
- Full color theme customization (create your own themes and chart
  palettes).
- Ad-free.

### Subscriber (recurring)
Everything in Paid, plus:
- Create and administer a sync group (up to 5 devices).
- AI Receipt OCR (scan receipts to auto-fill merchant, date,
  amount, and category).

If a Subscriber subscription lapses, the device reverts to Paid (it
doesn't lose the Paid features). In a sync group, there's a 7-day
grace period before the group is affected.

---

## 8. What This Chatbot Can and Can't Help With

This chatbot is here to help you use BudgeTrak. It can answer
questions like:
- "How do I set up a savings goal?"
- "What's the difference between FIXED and ACTUAL income modes?"
- "Why did my Available Cash drop suddenly?"
- "How does Accelerated mode work?"
- "How do I share a budget with my family?"
- "What happens when I delete a recurring expense?"

It can't help with:
- General personal-finance advice (whether to invest, what bank to
  use, tax questions).
- Other apps or services.
- Questions about your specific financial situation that aren't
  about app behavior.
- Anything outside the app.

For those, or for any question this chatbot can't answer, the
**Email button** at the bottom of the chat sends a message
(including this conversation's transcript) to a human at Tech
Advantage support. They typically respond within a few business
days.
