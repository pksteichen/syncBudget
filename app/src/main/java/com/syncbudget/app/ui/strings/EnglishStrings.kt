package com.syncbudget.app.ui.strings

object EnglishStrings : AppStrings {

    override val defaultCategoryNames = DefaultCategoryNames(
        other = "Other",
        recurring = "Recurring",
        amortization = "Amortization",
        recurringIncome = "Recurring Income",
        transportation = "Transportation/Gas",
        groceries = "Groceries",
        entertainment = "Entertainment",
        homeSupplies = "Home Supplies",
        restaurants = "Restaurants",
        charity = "Charity",
        clothes = "Clothes"
    )

    override val common = CommonStrings(
        ok = "OK",
        cancel = "Cancel",
        delete = "Delete",
        save = "Save",
        back = "Back",
        help = "Help",
        reset = "Reset",
        close = "Close",
        periodDay = "day",
        periodWeek = "week",
        periodMonth = "month",
        periodDays = "days",
        periodWeeks = "weeks",
        periodMonths = "months",
        addNewIncomeTransaction = "Add New Income Transaction",
        addNewExpenseTransaction = "Add New Expense Transaction",
        sourceLabel = "Description",
        merchantLabel = "Description",
        repeatTypeDays = "Day",
        repeatTypeWeeks = "Week",
        repeatTypeBiWeekly = "Every 2 Weeks",
        repeatTypeMonths = "Month",
        repeatTypeBiMonthly = "Twice per Month",
        repeatTypeAnnual = "Annual",
        budgetPeriodDaily = "Daily",
        budgetPeriodWeekly = "Weekly",
        budgetPeriodMonthly = "Monthly",
        sourceName = "Description",
        amount = "Amount",
        repeatType = "Repeat Type",
        everyXDays = "Days Between Repeats (1-365)",
        intervalWeeks = "Weeks Between Repeats (1-52)",
        everyXMonths = "Months Between Repeats (1-12)",
        dayOfMonth = "Day of Month (1-28)",
        firstDayOfMonth = "First Day (1-28)",
        secondDayOfMonth = "Second Day (1-28)",
        pickStartDate = "Pick Start Date",
        startDateLabel = { date -> "Start Date: $date" },
        selectAStartDate = "Select a start date",
        dayOfWeekLabel = { day -> "Day of Week: $day" },
        requiredSourceExample = "Required, e.g. Grocery Store",
        exampleAmount = "e.g. 42.50",
        exampleDays = "e.g. 14",
        exampleWeeks = "e.g. 2",
        exampleMonths = "e.g. 1",
        exampleMonthDay = "e.g. 15",
        exampleBiMonthlyDay1 = "e.g. 1",
        exampleBiMonthlyDay2 = "e.g. 15",
        language = "Language",
        dateDayTooHigh = "Please select a date between the 1st and 28th of the month"
    )

    override val dashboard = DashboardStrings(
        appTitle = "SecureSync Budget",
        notConfigured = "Not configured",
        spending = "Spending",
        settings = "Settings",
        transactions = "Transactions",
        savingsGoals = "Savings Goals",
        amortization = "Amortization",
        recurringExpenses = "Recurring Expenses",
        addIncome = "Add Income",
        addExpense = "Add Expense",
        supercharge = "Supercharge",
        superchargeTitle = "Supercharge Savings Goals",
        superchargeRemaining = { remaining -> "Remaining: $remaining" },
        superchargeAllocate = "Allocate",
        range7d = "7 Days",
        range30d = "30 Days",
        range90d = "90 Days",
        rangeAll = "All Time",
        noDataAvailable = "No data available",
        budgetLabel = { amount, period -> "$amount/$period" },
        superchargeReduceContributions = "Reduce Future Contributions",
        superchargeAchieveSooner = "Achieve Goal Sooner",
        superchargeExtraShouldLabel = "Extra Contribution Should...",
        superchargeNewContribution = { amount, period -> "New: $amount/$period" },
        superchargeNewPayoff = { date -> "New payoff: $date" },
        superchargeNewCompletion = { date -> "Completes: $date" },
        superchargeAutoAdjust = "Contributions adjust automatically",
        superchargeTargetDateNote = "This will change your target date"
    )

    override val settings = SettingsStrings(
        title = "Settings",
        configureYourBudget = "Configure Your Budget",
        currency = "Currency",
        showDecimalPlaces = "Show decimal places",
        dateFormat = "Date Format",
        weekStartsOn = "Week Starts On",
        sunday = "Sunday",
        monday = "Monday",
        chartPalette = "Chart Palette",
        bright = "Bright",
        pastel = "Pastel",
        sunset = "Sunset",
        matchDays = "Match Days (\u00b1N)",
        matchPercent = "Match Percent (\u00b1%)",
        matchDollar = "Match Dollar (\u00b1\$)",
        matchChars = "Match Characters",
        matchingConfiguration = "Matching Configuration",
        paidUser = "Paid User",
        categories = "Categories",
        addCategory = "Add Category",
        editCategory = "Edit Category",
        categoryName = "Category Name",
        chooseIcon = "Choose Icon:",
        deleteCategoryTitle = { name -> "Delete $name?" },
        deleteCategoryNoTransactions = "No transactions use this category. It will be deleted immediately.",
        reassignCategoryTitle = { name, count -> "Reassign $count transactions from $name" },
        reassignCategoryBody = { name, count -> "$count transaction${if (count != 1) "s" else ""} use the \"$name\" category. Choose a replacement category before deleting." },
        moveTo = "Move to",
        moveAndDelete = "Move & Delete",
        languageLabel = "Language",
        english = "English",
        spanish = "Espa\u00f1ol"
    )

    override val budgetConfig = BudgetConfigStrings(
        title = "Budget Configuration",
        budgetPeriod = "Budget Period",
        safeBudgetAmountLabel = { symbol, amount, period -> "Safe Budget Amount: $symbol$amount/$period" },
        budgetTrackingSince = { date -> "Budget tracking since: $date" },
        refreshTime = "Refresh Time",
        startResetBudget = "Start/Reset Budget",
        manualBudgetOverride = "Manual Budget Override",
        budgetAmountPer = { period -> "Budget Amount per $period" },
        manualOverrideWarning = "Manual override disables Amortization and Savings Goal deductions.",
        incomeSourceDescription = "Add sources of consistent income that you can rely on for budgeting. If your pay varies (large check, small check), you can make more than one entry for a source.",
        addIncomeSource = "Add Income Source",
        editIncomeSource = "Edit Income Source",
        deleteSourceConfirmTitle = { name -> "Delete $name?" },
        deleteSourceConfirmBody = "This income source will be permanently removed.",
        resetBudgetConfirmTitle = "Start/Reset Budget?",
        resetBudgetConfirmBody = "This will reset the budget start date to today and set available cash to one period's budget amount. Your transactions will not be affected.",
        resetSettingsTitle = "Refresh Time Settings",
        resetHour = "Reset Hour",
        dayOfWeekLabel = "Day of Week",
        dayOfMonthReset = "Day of Month (1-28)",
        requiredPaycheckExample = "Required, e.g. Paycheck",
        exampleIncomeAmount = "e.g. 2500.00"
    )

    override val transactions = TransactionsStrings(
        title = "Transactions",
        all = "All",
        expensesFilter = "Expenses",
        incomeFilter = "Income",
        addIncome = "Add Income",
        addExpense = "Add Expense",
        editTransaction = "Edit Transaction",
        search = "Search",
        dateSearch = "Date Search",
        textSearch = "Text Search",
        amountSearch = "Amount Search",
        searchBy = "Search by",
        searchResults = "Search Results",
        tapToClearSearch = "Tap to clear search",
        noTransactions = "No transactions",
        selectAll = "Select All",
        changeCategory = "Change Category",
        editMerchant = "Edit Merchant/Source",
        deleteSelected = "Delete Selected",
        selectedCount = { count -> "$count selected" },
        date = "Date",
        merchant = "Merchant",
        category = "Category",
        amount = "Amount",
        total = "Total",
        pieChart = "Pie chart mode",
        calculator = "Amount mode",
        percentage = "Percentage mode",
        save = "Save",
        load = "Load",
        saveTransactions = "Save Transactions",
        loadTransactions = "Import / Load",
        csv = "CSV",
        encrypted = "Encrypted",
        password = "Password",
        confirmPassword = "Confirm Password",
        selectFile = "Select File",
        usBank = "US Bank",
        secureSyncCsv = "SecureSync CSV Save File",
        secureSyncEncrypted = "SecureSync Encrypted Save File",
        duplicateDetected = "Possible Duplicate",
        duplicateExisting = "Existing:",
        duplicateNew = "New:",
        ignore = "Ignore",
        keepNew = "Keep New",
        keepExisting = "Keep Existing",
        ignoreAll = "Ignore All",
        recurringExpenseMatch = "Recurring Transaction Detected",
        recurringMatchTitle = { source -> "Matched Recurring Expense:" },
        recurringMatchBody = { source, amount -> "$source \u2014 $amount" },
        yesRecurring = "Yes, Recurring",
        noRegularExpense = "No, Not Recurring",
        amortizationMatch = "Amortization Transaction Detected",
        amortizationMatchTitle = { source -> "Matched Amortization Entry:" },
        amortizationMatchBody = { source, amount -> "$source \u2014 $amount" },
        yesAmortization = "Yes, Amortized",
        noRegularAmort = "No, Not Amortized",
        budgetIncomeMatch = "Budget Income Detected",
        budgetIncomeMatchTitle = { source -> "Matched Income Source:" },
        budgetIncomeMatchBody = { source, amount -> "$source \u2014 $amount" },
        yesBudgetIncome = "Yes, Budget Income",
        noExtraIncome = "No, Extra Income",
        dateAdvisory = "Note: This transaction's date differs from the expected schedule by more than 2 days. Consider updating your recurring expense configuration.",
        savedSuccessfully = { count -> "$count transactions saved successfully." },
        loadedSuccessfully = { loaded, total -> "$loaded of $total transactions loaded." },
        allSkipped = { total -> "0 of $total transactions loaded. Previously loaded data skipped." },
        passwordMinLength = "Password must be at least 8 characters",
        passwordsMustMatch = "Passwords do not match",
        newMerchantName = "Description",
        filterByCategory = { name -> "Filtered by: $name" },
        tapToClearFilter = "Tap to clear filter",
        startDate = "Select Start Date",
        endDate = "Select End Date",
        minAmount = "Min Amount",
        maxAmount = "Max Amount",
        searchText = "Search terms",
        from = "From",
        to = "To",
        format = "Format:",
        parseError = "Parse Error",
        unknownError = "Unknown error",
        parsedBeforeError = { count: Int -> "$count transactions parsed before error." },
        keep = "Keep",
        requiredMerchantExample = "Required, e.g. Grocery Store",
        moveCategoryValue = "Move Category Value",
        sumMismatch = "Sum Mismatch",
        maxAmount2 = { max: String -> "Max: $max" },
        includeAllData = "Include all app data (full backup)",
        fullBackupNote = "All app data will be saved including settings, categories, recurring expenses, savings goals, and amortization entries.",
        fullBackupDetected = "Full Backup Detected",
        fullBackupBody = "This file contains settings, categories, recurring expenses, savings goals, amortization entries, and all other app data. How would you like to load it?",
        loadTransactionsOnly = "Load Transactions Only",
        loadAllDataOverwrite = "Load All Data & Overwrite",
        fullBackupRestored = "Full backup restored successfully",
        fullBackupSaved = "Full backup saved successfully",
        fullBackupSyncWarning = "Restoring a full backup will dissolve the current family sync group. You will need to create a new group and share a new pairing code with family members.",
        fullBackupNonAdminBlock = "Only the group admin can restore a full backup. A full restore would corrupt sync state for all devices.",
        fullBackupGroupRecreated = "Backup restored. New family group created \u2014 share the pairing code with family members.",
        fullBackupGroupDissolved = "Backup restored. Family group dissolved."
    )

    override val futureExpenditures = FutureExpendituresStrings(
        title = "Savings Goals",
        description = "Plan and save for future expenses or financial targets. The app automatically deducts a small amount each budget period.",
        manualOverrideWarning = "Manual Budget Override is enabled. Savings Goal deductions are NOT being subtracted from your budget.",
        addSavingsGoal = "Add Savings Goal",
        editSavingsGoal = "Edit Savings Goal",
        name = "Description",
        targetAmount = "Target Amount",
        startingSavedAmount = "Starting Saved Amount",
        targetDate = "Target Date",
        fixedContribution = "Fixed Contribution",
        contributionPerPeriod = "Contribution per Period",
        selectTargetDate = "Select Target Date",
        targetDateLabel = { date -> "Target Date: $date" },
        goalReached = "Goal reached!",
        paused = "Paused",
        budgetReduction = { amount, period -> "-$amount/$period" },
        contributionLabel = { amount, period -> "$amount/$period" },
        savedOf = { saved, total -> "$saved of $total saved" },
        targetAmountBy = { amount, date -> "$amount by $date" },
        targetLabel = { amount -> "Target: $amount" },
        deleteSavingsGoal = "Delete Savings Goal?",
        deleteGoalConfirm = { name -> "Permanently delete \"$name\"?" },
        pauseAll = "Pause All",
        resumeAll = "Resume All",
        pause = "Pause",
        resume = "Resume",
        selectAFutureDate = "Select a future date",
        requiredNameExample = "Required, e.g. New Tires",
        exampleTargetAmount = "e.g. 1000.00",
        exampleContribution = "e.g. 5.00",
        mustBeLessThanTarget = "Must be less than target",
        payoffDate = { date -> "Payoff: $date" }
    )

    override val amortization = AmortizationStrings(
        title = "Amortization",
        description = "Spread a large past expense across multiple budget periods so it doesn't hit your budget all at once.",
        manualOverrideWarning = "Manual Budget Override is enabled. Amortization deductions are NOT being subtracted from your budget.",
        addEntry = "Add Amortization Entry",
        editEntry = "Edit Amortization Entry",
        sourceName = "Description",
        totalAmount = "Total Amount",
        budgetPeriods = { period -> "Budget Periods ($period)" },
        selectStartDate = "Pick Start Date",
        startDateLabel = { date -> "Start Date: $date" },
        completed = "Completed",
        xOfYComplete = { x, y, period -> "$x of $y $period complete" },
        totalPerPeriod = { symbol, total, symbol2, perPeriod -> "$symbol$total ($symbol2$perPeriod/period)" },
        deleteEntryTitle = "Delete Amortization Entry?",
        deleteEntryConfirm = { name -> "Permanently delete \"$name\"?" },
        requiredLaptopExample = "Required, e.g. Laptop",
        exampleTotalAmount = "e.g. 900.00",
        examplePeriods = "e.g. 90",
        selectAStartDate = "Select a start date"
    )

    override val recurringExpenses = RecurringExpensesStrings(
        title = "Recurring Expenses",
        description = "Register bills, subscriptions, and recurring payments so the budget calculator accounts for them automatically.",
        addExpense = "Add Recurring Expense",
        editExpense = "Edit Recurring Expense",
        deleteExpenseTitle = { name -> "Delete $name?" },
        deleteExpenseBody = "This recurring expense will be permanently removed.",
        descriptionLabel = "Description",
        requiredNetflixExample = "Required, e.g. Netflix",
        exampleAmount = "e.g. 15.99"
    )

    override val sync = SyncStrings(
        title = "Family Sync",
        familySync = "Family Sync",
        familySyncDescription = "Sync your budget across up to 5 devices in your household.",
        createGroup = "Create Family Group",
        createGroupDescription = "Start a new sync group and invite family members with a pairing code.",
        joinGroup = "Join Family Group",
        joinGroupDescription = "Enter a pairing code from a family member to join their sync group.",
        leaveGroup = "Leave Group",
        dissolveGroup = "Dissolve Group",
        syncNow = "Sync Now",
        lastSynced = { time -> "Last synced: $time" },
        syncing = "Syncing...",
        syncError = "Sync error",
        notConfigured = "Not configured",
        groupId = "Group ID",
        pairingCode = "Pairing Code",
        enterPairingCode = "Enter pairing code",
        pairingCodeExpiry = "Code expires in 10 minutes",
        generateCode = "Generate Pairing Code",
        deviceRoster = "Device Roster",
        devices = "Devices",
        thisDevice = "This device",
        admin = "Admin",
        transferAdmin = "Transfer Admin",
        removeDevice = "Remove Device",
        confirmLeave = "Are you sure you want to leave this sync group? Your local data will be kept but will no longer sync.",
        confirmDissolve = "Are you sure you want to dissolve this group? All members will be disconnected and sync data will be deleted.",
        connected = "Connected",
        disconnected = "Disconnected",
        syncStatusSynced = "Synced",
        syncStatusSyncing = "Syncing",
        syncStatusStale = "Stale",
        syncStatusError = "Error",
        syncStatusOff = "Off",
        groupCreated = "Family group created",
        joinedGroup = "Joined family group",
        leftGroup = "Left family group",
        groupDissolved = "Group dissolved",
        pairingCodeCopied = "Pairing code copied",
        invalidPairingCode = "Invalid or expired pairing code",
        encryptionKey = "Encryption Key",
        deviceName = "Device Name",
        adminOnly = "Admin only",
        familyTimezone = "Family Timezone",
        selectTimezone = "Select Timezone",
        showAttributionLabel = "Show Attribution",
        you = "You",
        staleWarning60 = "Sync soon to keep your data current",
        staleWarning75 = "15 days until sync data expires",
        staleWarning85 = "5 days to sync or local changes will need to be discarded",
        staleBlocked = "Sync blocked \u2014 full refresh required",
        claimAdmin = "Claim Admin Role",
        objectClaim = "Object",
        claimPending = "Admin claim pending",
        claimApproved = "Admin role transferred",
        claimRejected = "Admin claim rejected",
        claimExpiry = { time -> "Claim expires: $time" },
        claimBy = { name -> "$name is claiming admin role" },
        errorRemovedFromGroup = "You have been removed from this group",
        errorGroupDeleted = "This group has been dissolved",
        errorEncryption = "Encryption error \u2014 check your pairing",
        joinWarningTitle = "Replace Local Data?",
        joinWarningBody = "Joining a family group will replace your local budget data with the group's shared data. Your current transactions, goals, and settings will be overwritten. This cannot be undone."
    )

    // ── Help Screen Strings ──

    override val dashboardHelp = DashboardHelpStrings(
        title = "Dashboard Help",
        welcomeTitle = "Welcome to SecureSync Daily Budget",
        welcomeBody = "SecureSync Daily Budget is a privacy-first budgeting app designed to give you " +
            "a clear, real-time picture of how much money you can safely spend right now. " +
            "Unlike traditional budget trackers that only show you where your money went, " +
            "this app tells you where your money can go \u2014 calculated from your actual " +
            "income schedule, recurring bills, and financial goals.",
        dailyBudgetNumberTitle = "Your Daily Budget Number",
        dailyBudgetNumberBody = "The large number on the Solari display is your Available Cash \u2014 the amount " +
            "you can spend right now without jeopardizing your bills, savings goals, or " +
            "financial commitments. Think of it as the answer to the question everyone asks: " +
            "\"How much can I afford to spend today?\"",
        solariDisplayTitle = "The Solari Display",
        solariDisplayBody = "The centerpiece of the app is the Solari-style flip display \u2014 inspired by the " +
            "split-flap departure boards found in train stations and airports. It shows two " +
            "key pieces of information:",
        availableCashTitle = "Available Cash (Main Number)",
        availableCashBody = "This is the running total of money available for discretionary spending. " +
            "It starts at one period's budget amount when you first configure your budget, " +
            "and updates in real time:",
        bullet1 = "Increases each budget period (daily, weekly, or monthly) by your budget amount",
        bullet2 = "Decreases when you record an expense",
        bullet3 = "Increases when you record extra (non-budget) income",
        bullet4 = "Shows red/negative when you've overspent",
        budgetLabelTitle = "Budget Label (Below the Number)",
        budgetLabelBody = "The label beneath the digits shows your budget rate \u2014 for example, " +
            "\"\$42.50/day\" or \"\$297.50/week\". This tells you how much is added to " +
            "your available cash each period. If your budget is not yet configured, " +
            "it shows \"Not configured\".",
        headerBarTitle = "Header Bar",
        headerBarBody = "The header bar provides access to settings and this help page:",
        headerSettingsDesc = "Open the Settings screen to configure display options, categories, and access Budget Configuration.",
        headerHelpDesc = "Opens this help page.",
        navBarTitle = "Navigation Bar",
        navBarBody = "The bottom navigation bar provides quick access to all major features:",
        navTransactionsDesc = "Record and manage your income and expenses. Import bank statements, search, filter, and categorize.",
        navSavingsDesc = "Plan and save for future expenses or financial targets. Choose a target date or fixed per-period contribution.",
        navAmortizationDesc = "Spread a past large expense across multiple budget periods so it doesn't hit your budget all at once.",
        navRecurringDesc = "Register bills, subscriptions, and loan payments so the budget calculator accounts for them automatically.",
        spendingChartTitle = "Spending Chart",
        spendingChartBody = "Below the Solari display, a spending chart visualizes how your expenses are " +
            "distributed across categories. A title bar above the chart provides controls:",
        chartTitleBarTitle = "Chart Title Bar",
        chartRangeBullet = "Range button (left) \u2014 cycles through time ranges: 7 days, 30 days, 90 days, or All Time",
        chartSpendingBullet = "\"Spending\" title (center) \u2014 the chart label",
        chartToggleBullet = "Chart type toggle (right) \u2014 switch between pie chart and bar chart views",
        chartPaletteTitle = "Chart Palette",
        chartPaletteBody = "The colors used in the chart can be changed in Settings under \"Chart Palette\". " +
            "Three palettes are available: Bright, Pastel, and Sunset. Each palette automatically " +
            "adjusts for light and dark mode.",
        quickButtonsTitle = "Quick Transaction Buttons",
        quickButtonsBody = "Below the chart, two large buttons let you quickly add transactions without " +
            "leaving the dashboard:",
        quickAddIncomeDesc = "Opens the Add Income dialog. The transaction is saved and available cash increases.",
        quickAddExpenseDesc = "Opens the Add Expense dialog. The transaction is saved and available cash decreases.",
        quickMatchingNote = "Transactions added from the dashboard go through the same matching checks as " +
            "the Transactions screen: duplicate detection, recurring expense matching, " +
            "amortization matching, and budget income detection.",
        superchargeTitle = "Supercharge",
        superchargeBody = "The bolt icon in the lower-right corner of the dashboard opens Supercharge. " +
            "This feature lets you make extra one-time contributions to your Savings Goals " +
            "from your available cash.",
        superchargeIconDesc = "Allocate extra funds to one or more Savings Goals from your current available cash.",
        superchargeDialogBody = "In the Supercharge dialog, enter an amount for each goal you want to boost. " +
            "The total is deducted from your available cash and added to the goals' saved amounts. " +
            "This is useful when you have surplus cash and want to reach a goal faster.",
        howBudgetWorksTitle = "How the Budget Works",
        howBudgetWorksBody = "The budget engine runs a cash flow simulation using your income schedule and " +
            "recurring expenses to determine a safe spending amount for each budget period.",
        safeBudgetTitle = "Safe Budget Amount",
        safeBudgetBody = "This is the maximum you can spend per period (day, week, or month) without " +
            "running out of money to cover your bills. The calculation:",
        safeBudgetStep1 = "Income projection",
        safeBudgetStep1Desc = "Your income sources and their repeat schedules are projected forward one year.",
        safeBudgetStep2 = "Expense simulation",
        safeBudgetStep2Desc = "Your recurring expenses are projected over the same period.",
        safeBudgetStep3 = "Timing safety",
        safeBudgetStep3Desc = "The engine ensures that even in months with clustered bills, the budget amount covers all obligations.",
        budgetAmountTitle = "Budget Amount",
        budgetAmountBody = "Your actual per-period budget is the Safe Budget Amount minus any active deductions:",
        budgetSavingsBullet = "Savings Goal deductions \u2014 money set aside for planned purchases and savings targets",
        budgetAmortBullet = "Amortization deductions \u2014 spreading past large expenses over time",
        budgetAmountNote = "This ensures your spending money is already adjusted for both upcoming and past large expenses.",
        availableCashSectionTitle = "Available Cash",
        availableCashSectionBody = "Available Cash is the number shown on the Solari display. Each budget period, your " +
            "budget amount is added to the total. Each expense you record is subtracted. " +
            "Extra income (income not already counted in your budget sources) is added. " +
            "The result: a single number that tells you exactly how much you can spend.",
        gettingStartedTitle = "Getting Started",
        gettingStartedBody = "Follow these steps to set up your budget for the first time:",
        step1Title = "Open Settings",
        step1Desc = "Tap the gear icon in the top left to configure your currency, display preferences, and transaction categories.",
        step2Title = "Configure Your Budget",
        step2Desc = "In Settings, tap \"Configure Your Budget\" to open Budget Configuration. Choose a budget period (Daily is recommended for most people).",
        step3Title = "Add Income Sources",
        step3Desc = "In Budget Configuration, add all reliable income sources \u2014 your salary, regular side income, etc. Set the repeat schedule for each (e.g., \"Month\" on the 1st and 15th for bi-monthly pay).",
        step4Title = "Add Recurring Expenses",
        step4Desc = "Navigate to Recurring Expenses (the sync icon on the dashboard) and add all your regular bills: rent, utilities, insurance, subscriptions, loan payments.",
        step5Title = "Start Your Budget",
        step5Desc = "Back in Budget Configuration, your safe budget is calculated automatically. Tap \"Start/Reset Budget\" to initialize your available cash.",
        step6Title = "Start Tracking",
        step6Desc = "Return to the dashboard. Your Solari display now shows your available cash. Record expenses as you spend and watch your number update in real time.",
        habitsTitle = "Building Better Financial Habits",
        habitsBody = "SecureSync Daily Budget is more than a tracker \u2014 it's a tool for building " +
            "lasting financial awareness. Here's how to get the most out of it:",
        tipKnowTitle = "Know Your Number",
        tipKnowBody = "Check the Solari display at least once a day. The simple act of knowing " +
            "how much you can spend creates mindfulness around purchases. Research shows " +
            "that people who track their spending spend 10\u201320% less on average, " +
            "simply from awareness.",
        tipRecordTitle = "Record Every Expense",
        tipRecordBody = "Small purchases are where budgets quietly fail. A coffee here, a snack " +
            "there \u2014 they add up fast. Recording every expense keeps you honest " +
            "and helps you spot patterns you might not notice otherwise. Use bank imports " +
            "for efficiency, and manually log cash purchases.",
        tipPlanTitle = "Plan for the Unexpected",
        tipPlanBody = "Use Savings Goals to plan for things like car tires, appliance " +
            "replacements, holiday gifts, or vacations. When you save a little each period, " +
            "these expenses don't become emergencies. The key to financial peace is " +
            "eliminating surprises.",
        tipPaycheckTitle = "Avoid the Paycheck Trap",
        tipPaycheckBody = "Many people overspend right after payday and scramble before the next one. " +
            "The daily budget approach smooths your income across every day, so you have " +
            "a consistent, predictable amount to spend regardless of when your paycheck arrives. " +
            "No more feast-and-famine cycles.",
        tipWatchTitle = "Watch Your Available Cash Grow",
        tipWatchBody = "If you consistently spend less than your budget amount, your available cash " +
            "will gradually increase. This surplus is your buffer for unexpected expenses " +
            "and a sign that your financial habits are working. Don't feel pressured to " +
            "spend it \u2014 let it grow.",
        keyFeaturesTitle = "Key Features at a Glance",
        featureBullet1 = "Real-time budget tracking with a beautiful Solari flip display",
        featureBullet2 = "Smart budget calculation that accounts for irregular income and expense timing",
        featureBullet3 = "Automatic recurring expense and income recognition from bank imports",
        featureBullet4 = "Savings Goals \u2014 save for big purchases and financial targets automatically",
        featureBullet5 = "Amortization \u2014 spread large past purchases over time",
        featureBullet6 = "Multi-category transaction splitting with pie chart, calculator, or percentage modes",
        featureBullet7 = "Encrypted transaction backup and restore",
        featureBullet8 = "Bank statement import with auto-categorization",
        featureBullet9 = "Duplicate transaction detection",
        featureBullet10 = "Fully customizable categories with icon selection",
        featureBullet11 = "Multiple currency and date format support",
        privacyTitle = "Privacy & Security",
        privacyBody = "Your financial data stays on your device. SecureSync Daily Budget does not " +
            "connect to the internet, does not collect analytics, and does not share your " +
            "data with anyone. When you export your transactions, you can choose encrypted " +
            "format (ChaCha20-Poly1305 with PBKDF2 key derivation) for maximum security. " +
            "Your money, your data, your control."
    )

    override val settingsHelp = SettingsHelpStrings(
        title = "Settings Help",
        overviewTitle = "Overview",
        overviewBody = "The Settings screen lets you customize how the app displays information " +
            "and manage your transaction categories. Access it by tapping the gear icon " +
            "on the dashboard.",
        headerTitle = "Header Bar",
        headerBody = "The header provides navigation and help access:",
        backDesc = "Return to the dashboard.",
        helpDesc = "Opens this help page.",
        configureTitle = "Configure Your Budget",
        configureBody = "The first item in Settings is a button that opens Budget Configuration. " +
            "This is where you set up your income sources, choose your budget period, " +
            "and calculate your safe daily budget. See the Budget Configuration help page " +
            "for full details.",
        currencyTitle = "Currency",
        currencyBody = "Choose the currency symbol displayed throughout the app. The dropdown includes " +
            "common symbols:",
        currencyDollar = "$ \u2014 US Dollar, Canadian Dollar, Australian Dollar, etc.",
        currencyEuro = "\u20ac \u2014 Euro",
        currencyPound = "\u00a3 \u2014 British Pound",
        currencyYen = "\u00a5 \u2014 Japanese Yen / Chinese Yuan",
        currencyRupee = "\u20b9 \u2014 Indian Rupee",
        currencyWon = "\u20a9 \u2014 Korean Won",
        currencyMore = "And more",
        currencyNote = "The currency symbol affects the Solari display, transaction amounts, budget " +
            "configuration, and all other monetary displays. Decimal places are automatically " +
            "adjusted for currencies that traditionally don't use them (e.g., Yen).",
        decimalsTitle = "Show Decimal Places",
        decimalsBody = "When checked, the Solari display shows cents/pence after a decimal point. " +
            "The number of decimal places depends on your currency (2 for most currencies, " +
            "0 for currencies like the Japanese Yen). Unchecking this rounds the display to " +
            "whole numbers for a cleaner look.",
        dateFormatTitle = "Date Format",
        dateFormatBody = "Choose how dates are displayed throughout the app, including the transaction " +
            "list, date pickers, and export files. Options include:",
        dateIso = "2026-02-17 \u2014 ISO format (default)",
        dateUs = "02/17/2026 \u2014 US format",
        dateEu = "17/02/2026 \u2014 European format",
        dateAbbrev = "Feb 17, 2026 \u2014 Abbreviated month",
        dateFull = "February 17, 2026 \u2014 Full month name",
        dateMore = "And several other international formats",
        dateNote = "The dropdown shows a sample date in each format so you can preview how " +
            "it will look before selecting.",
        weekStartTitle = "Week Starts On",
        weekStartBody = "Choose whether the week begins on Sunday or Monday. This affects the spending " +
            "chart's weekly grouping and any weekly budget period calculations.",
        chartPaletteTitle = "Chart Palette",
        chartPaletteBody = "Choose the color palette used for pie charts and bar charts throughout the app. " +
            "Three options are available:",
        paletteBright = "Bright \u2014 vivid, saturated colors for maximum contrast",
        palettePastel = "Pastel \u2014 softer, muted tones for a gentler look",
        paletteSunset = "Sunset \u2014 warm earth tones inspired by a sunset palette (default)",
        paletteNote = "Each palette automatically adjusts for light and dark mode. The palette " +
            "applies to the dashboard spending chart, the transaction pie chart editor, " +
            "and all other chart displays.",
        matchingTitle = "Matching Configuration",
        matchingBody = "These settings control how the app detects duplicate transactions and matches " +
            "transactions to recurring expenses, amortization entries, and budget income sources:",
        matchDaysBullet = "Match Days (\u00b1N) \u2014 how many days apart two transactions can be and still be considered a match",
        matchPercentBullet = "Match Percent (\u00b1%) \u2014 percentage tolerance for amount matching",
        matchDollarBullet = "Match Dollar (\u00b1\$) \u2014 absolute dollar tolerance for amount matching",
        matchCharsBullet = "Match Characters \u2014 minimum shared substring length for merchant name matching",
        matchingNote = "The defaults work well for most users. Increase the tolerances if you find " +
            "the app is missing matches, or decrease them if you're seeing too many false positives.",
        paidTitle = "Paid User",
        paidBody = "When checked, this unlocks premium features:",
        paidSave = "Save transactions \u2014 export to CSV or encrypted file",
        paidLoad = "Load transactions \u2014 import from bank CSV, app CSV, or encrypted backup",
        paidNote = "When Paid User is not enabled, the Save and Load icons on the Transactions " +
            "screen appear dimmed and are non-functional.",
        categoriesTitle = "Categories",
        categoriesBody = "Categories let you classify your transactions for better spending insight. " +
            "Each category has a name and an icon.",
        defaultCategoriesTitle = "Default Categories",
        defaultCategoriesBody = "Three categories are protected and cannot be deleted or renamed:",
        catOther = "Other \u2014 the default fallback category for uncategorized transactions",
        catRecurring = "Recurring \u2014 automatically assigned to transactions matched to recurring expenses",
        catAmortization = "Amortization \u2014 automatically assigned to transactions matched to amortization entries",
        addCategoryTitle = "Adding a Category",
        addCategoryBody = "Tap \"Add Category\" to create a new category. Enter a name and choose an icon " +
            "from the icon grid. Icons are displayed as a visual grid you can scroll through.",
        editCategoryTitle = "Editing a Category",
        editCategoryBody = "Tap any non-protected category to open the edit dialog. You can change the " +
            "name and icon. The delete button (red trash icon) appears in the dialog title bar.",
        deleteCategoryTitle = "Deleting a Category",
        deleteCategoryBody = "When deleting a category that has existing transactions:",
        deleteBullet1 = "If no transactions use the category, it is deleted immediately",
        deleteBullet2 = "If transactions exist, a reassignment dialog appears",
        deleteBullet3 = "You must choose another category to move the affected transactions to",
        deleteBullet4 = "Tap \"Move & Delete\" to reassign all affected transactions and remove the category",
        reassignmentTitle = "Category Reassignment",
        reassignmentBody = "When you delete a category, all transactions assigned to it " +
            "are moved to your chosen replacement category. This includes " +
            "multi-category transactions where only the specific category " +
            "split is affected. The reassignment is permanent.",
        tipsTitle = "Tips",
        tip1 = "Set up categories before importing transactions \u2014 the auto-categorization uses your existing transaction history to match merchants.",
        tip2 = "Create categories that match your spending habits. Common examples: Food, Transport, Entertainment, Health, Housing, Utilities, Shopping.",
        tip3 = "Use the \"Daily\" budget period if you want the most granular spending control.",
        tip4 = "The Budget Configuration button is the first thing to set up after installing the app."
    )

    override val transactionsHelp = TransactionsHelpStrings(
        title = "Transactions Help",
        overviewTitle = "Overview",
        overviewBody = "The Transactions screen is where you manage all income and expenses. " +
            "You can add, edit, delete, search, filter, import, and export transactions.",
        headerTitle = "Header Bar",
        headerBody = "The header bar contains navigation and action icons:",
        backDesc = "Return to the main screen.",
        saveDesc = "Save all transactions to a file. Requires Paid User.",
        loadDesc = "Import or load transactions from a file. Requires Paid User.",
        helpDesc = "Opens this help page.",
        saveLoadNote = "The Save and Load icons appear dimmed if Paid User is not enabled in Settings.",
        actionBarTitle = "Action Bar",
        actionBarBody = "Below the header, the action bar provides quick access to common operations:",
        filterDesc = "Filter toggle \u2014 cycles through: All, Expenses, Income.",
        addIncomeDesc = "Create a new income transaction.",
        addExpenseDesc = "Create a new expense transaction.",
        searchDesc = "Open search menu with three options:",
        dateSearchBullet = "Date Search \u2014 pick a start and end date",
        textSearchBullet = "Text Search \u2014 search by merchant/source name",
        amountSearchBullet = "Amount Search \u2014 search by amount range",
        searchNote = "While search results are active, a banner appears at the top. Tap the banner to clear the search.",
        listTitle = "Transaction List",
        listBody = "Transactions are displayed in a scrollable list, sorted by date (newest first). " +
            "Each row shows:",
        listIconBullet = "Category icon (left) \u2014 colored to indicate the category",
        listDateBullet = "Date \u2014 formatted per your Settings preference",
        listMerchantBullet = "Merchant/Source \u2014 the name of the payee or payer",
        listAmountBullet = "Amount \u2014 red for expenses, green for income",
        iconColorsTitle = "Category Icon Colors",
        coloredLabel = "Colored",
        coloredDesc = " \u2014 category was set or confirmed by you",
        defaultLabel = "Default",
        defaultDesc = " \u2014 auto-assigned during import (not yet confirmed)",
        filterByIconNote = "Tap a category icon to filter the list to only that category. A filter banner will appear; tap it to clear.",
        multiCategoryTitle = "Multi-Category Transactions",
        multiCategoryBody = "A list icon indicates the transaction is split across multiple categories. " +
            "Tap it to expand and see the per-category breakdown.",
        tapEditTitle = "Tapping & Editing",
        tapBullet = "Tap a transaction to open the Edit dialog",
        longPressBullet = "Long-press a transaction to enter selection mode",
        selectionTitle = "Selection Mode",
        selectionBody = "Long-press any transaction to enter selection mode. A toolbar appears with bulk actions:",
        selectAllDesc = "Select All \u2014 toggle all visible transactions",
        changeCategoryDesc = "Set a single category for all selected transactions.",
        editMerchantDesc = "Replace the merchant/source name on all selected transactions.",
        deleteDesc = "Delete all selected transactions.",
        closeDesc = "Exit selection mode without changes.",
        addEditTitle = "Add / Edit Transaction Dialog",
        addEditBody = "When adding or editing a transaction, a full-screen dialog appears with these fields:",
        fieldDate = "Date",
        fieldDateDesc = "Tap the calendar icon to pick a date.",
        fieldMerchant = "Merchant / Source",
        fieldMerchantDesc = "Type the name of the payee (expenses) or income source.",
        fieldCategory = "Category",
        fieldCategoryDesc = "Tap to open the category picker. You can select one or multiple categories.",
        fieldAmount = "Amount",
        fieldAmountDesc = "Enter the transaction amount.",
        singleCatTitle = "Single Category",
        singleCatBody = "With one category selected, simply enter the total amount in the Amount field.",
        multiCatTitle = "Multiple Categories",
        multiCatBody = "When two or more categories are selected, you unlock three entry modes. " +
            "First enter the Total amount, then choose a mode:",
        pieChartDesc = "Drag slices on an interactive pie chart to distribute the total across categories.",
        calculatorDesc = "Enter a specific dollar amount for each category. The last empty field auto-fills.",
        percentageDesc = "Enter a percentage for each category. Percentages auto-adjust to total 100%.",
        pieChartModeTitle = "Pie Chart Mode",
        pieChartModeBody = "The interactive pie chart lets you visually distribute a transaction across categories by dragging the divider lines between slices.",
        pieChartDragNote = "Drag the boundary between any two slices to redistribute. " +
            "The category labels and dollar amounts update in real time beneath the chart.",
        autoFillTitle = "Auto-Fill Behavior",
        autoFillBody = "In both Calculator and Percentage modes, when all fields except one are filled, " +
            "the remaining field automatically fills with the balance. " +
            "For example, with a \$100 total and two categories, entering \$60 for Food " +
            "will automatically set Shopping to \$40.",
        duplicateTitle = "Duplicate Detection",
        duplicateBody = "When you save a new transaction or import from a file, the app checks for possible duplicates. " +
            "A transaction is flagged if it matches an existing one on all three criteria:",
        dupAmountBullet = "Amount within 1% of each other",
        dupDateBullet = "Date within 7 days of each other",
        dupMerchantBullet = "Merchant name shares a common substring",
        dupDialogBody = "When a duplicate is detected, you'll see a dialog with four options:",
        dupIgnore = "Ignore \u2014 keep both transactions",
        dupKeepNew = "Keep New \u2014 replace the existing with the new one",
        dupKeepExisting = "Keep Existing \u2014 discard the new transaction",
        dupIgnoreAll = "Ignore All \u2014 keep all remaining duplicates (import only)",
        savingTitle = "Saving Transactions",
        savingBody = "Tap the Save icon in the header to export all transactions to a file. Two formats are available:",
        csvFormatTitle = "CSV Format",
        csvFormatBody = "Saves your transactions as a plain-text CSV file (syncbudget_transactions.csv). " +
            "This file preserves all data including categories and can be loaded back into the app. " +
            "It can also be opened in spreadsheet software like Excel or Google Sheets for review.",
        encryptedFormatTitle = "Encrypted Format",
        encryptedFormatBody = "Saves your transactions in an encrypted file (syncbudget_transactions.enc) " +
            "protected with a password you choose. This is the recommended format for backups " +
            "and transferring data between devices, as it keeps your financial information private.",
        encryptionDetailsTitle = "Encryption Details",
        encryptionDetailsBody = "Your file is protected with ChaCha20-Poly1305 authenticated encryption \u2014 " +
            "the same family of ciphers used by modern messaging apps and VPNs. " +
            "Your password is never stored; instead, it is transformed into an encryption key " +
            "using PBKDF2 with 100,000 iterations, making brute-force attacks extremely slow.",
        passwordImportanceTitle = "Why Your Password Matters",
        passwordImportanceBody = "Encryption is only as strong as your password. A short or common password " +
            "can be guessed quickly, even with strong encryption. Here's what a modern high-end " +
            "graphics card (capable of testing billions of simple hashes per second) could achieve:",
        passwordTableHeader = "Password",
        passwordTableExample = "Example",
        passwordTableTime = "Time to Crack",
        pw8Lower = "8 chars, lowercase",
        pw8LowerEx = "password",
        pw8LowerTime = "minutes",
        pw8Mixed = "8 chars, mixed",
        pw8MixedEx = "Pa\$sw0rd",
        pw8MixedTime = "hours",
        pw10Mixed = "10 chars, mixed",
        pw10MixedEx = "K9#mP2x!qL",
        pw10MixedTime = "months",
        pw12Mixed = "12 chars, mixed",
        pw12MixedEx = "7hR!q2Lp#9Zk",
        pw12MixedTime = "millennia",
        pw16Mixed = "16+ chars, mixed",
        pw16MixedEx = "cT8!nQ#2mK@5rW9j",
        pw16MixedTime = "trillions of years",
        pw4Word = "4-word phrase",
        pw4WordEx = "maple cloud river fox",
        pw4WordTime = "trillions of years",
        pbkdfNote = "Because your password goes through 100,000 rounds of PBKDF2 before being used as an " +
            "encryption key, each guess is deliberately made very expensive. A single high-end GPU " +
            "can only attempt roughly 100,000\u2013500,000 passwords per second against this file \u2014 " +
            "millions of times slower than attacking a simple hash.",
        recommendedTitle = "Recommended Password Strategy",
        recommendedBody = "Use 12 or more characters combining uppercase letters, lowercase letters, " +
            "numbers, and symbols. A passphrase of 4\u20135 random words (e.g., \"correct horse battery staple\") " +
            "is also excellent. With a strong password of this kind, " +
            "even a nation-state adversary with thousands of GPUs " +
            "would need trillions of years to crack your file.",
        passwordMinNote = "The minimum required length is 8 characters, but longer is always better. " +
            "You must enter your password twice to confirm it before saving. " +
            "There is no password recovery \u2014 if you forget your password, " +
            "the file cannot be opened.",
        loadingTitle = "Loading & Importing",
        loadingBody = "Tap the Load icon in the header to import transactions from a file. Three formats are supported:",
        loadUsBank = "US Bank",
        loadUsBankDesc = "Import transactions from a US Bank CSV export file. " +
            "Transactions are automatically categorized based on your existing merchant history.",
        loadCsv = "SecureSync CSV Save File",
        loadCsvDesc = "Load a CSV file previously saved from this app. " +
            "All categories and data are preserved exactly as they were.",
        loadEncrypted = "SecureSync Encrypted Save File",
        loadEncryptedDesc = "Load a previously encrypted save file. " +
            "You must enter the password used when the file was saved.",
        loadPasswordNote = "For encrypted files, the password field appears automatically when you select the " +
            "encrypted format. The \"Select File\" button is disabled until you enter at least 8 characters.",
        loadDuplicateNote = "After loading, each imported transaction is checked for duplicates against your " +
            "existing transactions. If duplicates are found, you'll be prompted to resolve them " +
            "one at a time (see Duplicate Detection above).",
        autoCatTitle = "Auto-Categorization (Bank Imports)",
        autoCatBody = "When importing from a bank CSV, the app looks at your existing transactions from the " +
            "past 6 months to find matching merchants. If a match is found, the most frequently used " +
            "category is assigned automatically. Transactions without a match are assigned to \"Other\". " +
            "Auto-categorized transactions show a default-colored icon until you manually confirm " +
            "or change the category.",
        tipsTitle = "Tips",
        tip1 = "Use CSV saves for spreadsheet-compatible backups that you can review on a computer.",
        tip2 = "Use Encrypted saves for secure backups and transferring data between devices.",
        tip3 = "The same file can be loaded as many times as needed \u2014 duplicate detection prevents accidental double-entries.",
        tip4 = "Use the category filter (tap any category icon) combined with selection mode for efficient bulk edits.",
        tip5 = "After a bank import, review auto-categorized transactions and use bulk Change Category to correct any misassignments."
    )

    override val budgetConfigHelp = BudgetConfigHelpStrings(
        title = "Budget Configuration Help",
        overviewTitle = "Overview",
        overviewBody = "Budget Configuration is the core setup screen where you define your income " +
            "sources, choose your budget period, and calculate your safe spending amount. " +
            "The budget engine uses this information to determine how much you can safely " +
            "spend each period.",
        periodTitle = "Budget Period",
        periodBody = "The budget period determines how often your available cash is replenished. " +
            "Choose the period that best matches how you think about spending:",
        periodDaily = "Daily",
        periodDailyDesc = "Your budget is calculated per day. Best for tight budgets or people who want maximum daily awareness.",
        periodWeekly = "Weekly",
        periodWeeklyDesc = "Your budget is calculated per week. Good for people who plan expenses by the week.",
        periodMonthly = "Monthly",
        periodMonthlyDesc = "Your budget is calculated per month. Suits people with monthly pay who prefer monthly planning.",
        periodNote = "The period also affects how Savings Goal and Amortization deductions are calculated, " +
            "since they deduct a fixed amount per period.",
        resetSettingsTitle = "Refresh Time Settings",
        resetSettingsBody = "Tap the \"Refresh Time\" button next to the Budget Period selector to configure " +
            "when your budget period rolls over:",
        resetHourTitle = "Reset Hour",
        resetHourBody = "The hour of the day when a new period begins and your budget amount is " +
            "added to available cash. Default is 12 AM (midnight). Set this to when " +
            "you typically start your day \u2014 for example, 6 AM if you're an early riser.",
        dayOfWeekTitle = "Day of Week (Weekly)",
        dayOfWeekBody = "For weekly budgets, choose which day the new week starts. For example, " +
            "if you set Monday, your budget resets every Monday at the configured reset hour.",
        dayOfMonthTitle = "Day of Month (Monthly)",
        dayOfMonthBody = "For monthly budgets, choose which day of the month the new period starts " +
            "(1\u201328). If you're paid on the 1st, set this to 1. If paid on the 15th, " +
            "set it to 15. The maximum is 28 to ensure the date exists in all months.",
        safeBudgetTitle = "Safe Budget Amount",
        safeBudgetBody = "The Safe Budget Amount is the calculated maximum you can spend per period " +
            "while still covering all your recurring expenses. It is displayed at the top " +
            "of the configuration screen.",
        howCalculatedTitle = "How It's Calculated",
        howCalculatedBody = "The engine projects your income and expenses forward one year:",
        calcStep1 = "Income summing",
        calcStep1Desc = "All income source occurrences are generated for the next 12 months based on their repeat schedules. Total annual income is computed.",
        calcStep2 = "Base amount",
        calcStep2Desc = "The base budget is annual income divided by the number of budget periods in a year (e.g., 365 for daily, 52 for weekly, 12 for monthly).",
        calcStep3 = "Timing safety",
        calcStep3Desc = "The engine simulates each period and checks that cumulative expenses never exceed the budget. If bills cluster in certain periods, the budget amount is increased to cover the worst case.",
        importantTitle = "Important",
        importantBody = "The Safe Budget Amount only considers income sources and recurring expenses " +
            "that have complete repeat schedule configurations. If a source has no repeat " +
            "settings, it will be excluded from the calculation. Make sure to configure " +
            "repeat schedules for all your income sources.",
        autoRecalcTitle = "Automatic Recalculation",
        autoRecalcBody = "The Safe Budget Amount updates automatically whenever you change income sources, " +
            "recurring expenses, or the budget period. No manual recalculation is needed.",
        startResetTitle = "Start/Reset Budget",
        startResetBody = "Tap \"Start/Reset Budget\" when you first set up or need a fresh start. This:",
        resetBullet1 = "Recalculates the safe budget amount",
        resetBullet2 = "Resets the budget start date to today",
        resetBullet3 = "Sets available cash to one period's budget amount",
        resetBullet4 = "Does NOT delete your transactions",
        whenToResetTitle = "When to Reset",
        whenToResetBody = "Use Start/Reset Budget when your available cash has drifted from reality " +
            "(e.g., after a major life change like a new job or move), or when " +
            "you've made significant changes to your income sources or expenses " +
            "and want to start fresh. Resetting will lose your accumulated surplus " +
            "or deficit, so use it deliberately.",
        manualTitle = "Manual Budget Override",
        manualBody = "Check \"Manual Budget Override\" to set your own per-period budget amount " +
            "instead of using the calculated value. When enabled:",
        manualBullet1 = "A text field appears where you enter your desired amount per period",
        manualBullet2 = "The safe budget calculation is ignored",
        manualBullet3 = "Amortization and Savings Goal deductions are disabled",
        warningTitle = "Warning",
        warningBody = "Manual override disables the safety net provided by the budget calculator. " +
            "Amortization and Savings Goal deductions will not be subtracted " +
            "from your budget. Red warning banners will appear on the Amortization " +
            "and Savings Goals screens when manual override is active.",
        incomeSourcesTitle = "Income Sources",
        incomeSourcesBody = "Income sources represent your reliable, recurring income \u2014 the money you " +
            "can count on for budgeting purposes. Add all consistent income streams: " +
            "salary, freelance retainers, pension, recurring side income, etc.",
        addingIncomeTitle = "Adding an Income Source",
        addingIncomeBody = "Tap \"Add Income Source\" and fill in:",
        incomeNameBullet = "Source Name \u2014 a descriptive name (e.g., \"Main Job Paycheck\"). This is also used for budget income detection when you add transactions.",
        incomeAmountBullet = "Amount \u2014 the amount you receive per occurrence",
        variablePayTitle = "Variable Pay",
        variablePayBody = "If your pay varies (for example, a large paycheck and a small paycheck " +
            "each month), create separate entries for each amount. The budget calculator " +
            "will handle the different amounts correctly.",
        managingTitle = "Managing Income Sources",
        manageTapBullet = "Tap a source to edit its name and amount",
        manageRepeatDesc = "Configure the income schedule (when you get paid)",
        manageDeleteDesc = "Permanently remove the income source",
        repeatTitle = "Repeat Schedules",
        repeatBody = "Every income source needs a repeat schedule so the budget calculator knows " +
            "when to expect payments. The same repeat types are available for income sources " +
            "and recurring expenses:",
        everyXDaysTitle = "Day",
        everyXDaysBody = "Income arrives every N days (1\u2013365). Requires a Start Date \u2014 the date " +
            "of any past or future occurrence. The engine calculates all future dates from " +
            "this reference point.",
        everyXWeeksTitle = "Week",
        everyXWeeksBody = "Income arrives every N weeks (1\u201352). Requires a Start Date. The day of " +
            "the week is determined by your start date (e.g., if your start date falls on " +
            "a Friday, income repeats every N Fridays).",
        biWeeklyTitle = "",
        biWeeklyBody = "",
        everyXMonthsTitle = "Month",
        everyXMonthsBody = "Income arrives on a specific day of the month, every N months (1\u201312). " +
            "Pick a start date to set the day and phase. Days 29\u201331 are allowed when " +
            "the interval is 12 (yearly); otherwise days are limited to 1\u201328.",
        biMonthlyTitle = "Twice per Month (Bi-Monthly)",
        biMonthlyBody = "Income arrives on two specific days each month. Enter both the First Day and " +
            "Second Day (1\u201328 each). For example, if you're paid on the 1st and 15th, " +
            "enter 1 and 15. This results in exactly 24 occurrences per year.",
        annualTitle = "Annual",
        annualBody = "Income arrives once per year on a specific date. Pick a start date \u2014 any day " +
            "of any month is allowed, including the 29th\u201331st. The engine handles leap years " +
            "and short months automatically.",
        dayLimitTitle = "Day Limit: 1\u201328",
        dayLimitBody = "Day-of-month values are limited to 28 for most repeat types to ensure the date " +
            "exists in all months, including February. Annual and 12-month intervals allow " +
            "days 29\u201331 since they target a specific month.",
        budgetIncomeTitle = "Budget Income Detection",
        budgetIncomeBody = "When you add an income transaction in the Transactions screen, the app " +
            "checks whether it matches one of your configured income sources (by name " +
            "and amount). If a match is found, you're asked whether this is:",
        budgetIncomeBullet = "Budget income \u2014 already accounted for in your budget (does NOT increase available cash)",
        extraIncomeBullet = "Extra income \u2014 unexpected or additional income (DOES increase available cash)",
        budgetIncomeNote = "This prevents your paycheck from being double-counted \u2014 once in the budget " +
            "calculation and again as a manual income entry.",
        tipsTitle = "Tips",
        tip1 = "Set up all income sources and recurring expenses before tapping Start/Reset Budget for the best result.",
        tip2 = "The safe budget recalculates automatically whenever you change income or expenses.",
        tip3 = "Use Start/Reset Budget sparingly \u2014 it wipes your accumulated surplus/deficit.",
        tip4 = "For variable income, create separate entries for each pay amount to improve accuracy.",
        tip5 = "Use descriptive source names like \"Acme Corp Paycheck\" \u2014 the name is used for automatic budget income matching.",
        tip6 = "Only include reliable, recurring income. Don't add one-time windfalls \u2014 record those as extra income in Transactions."
    )

    override val futureExpendituresHelp = FutureExpendituresHelpStrings(
        title = "Savings Goals Help",
        whatTitle = "What Are Savings Goals?",
        whatBody = "Savings Goals let you plan and save for future expenses or financial " +
            "targets without blowing your budget. Instead of a large expense hitting " +
            "your available cash all at once, the app automatically reduces your budget " +
            "by a small amount each period to save up over time.",
        exampleTitle = "Example",
        exampleBody = "You want to build a \$3,000 emergency fund. Create a fixed contribution " +
            "goal of \$5/day. On a daily budget, you barely notice the deduction, " +
            "but after 20 months your emergency fund is fully funded. Or set a " +
            "target date of 6 months to save \$600 for new tires \u2014 the app " +
            "deducts about \$3.29/day automatically. No surprise, no stress.",
        twoTypesTitle = "Two Goal Types",
        twoTypesBody = "Savings Goals supports two different approaches to saving:",
        targetDateTitle = "Target Date",
        targetDateBody = "Set a date by which you need the money. The app automatically calculates " +
            "how much to deduct each period based on the remaining amount and remaining " +
            "time. As you get closer to the date, the deduction adjusts dynamically.",
        fixedContribTitle = "Fixed Contribution",
        fixedContribBody = "Set a fixed amount to contribute each budget period. There's no target date \u2014 " +
            "the app simply deducts your chosen amount every period until the goal is " +
            "reached. This is ideal for open-ended savings like an emergency fund.",
        headerTitle = "Header Bar",
        headerBody = "The header provides navigation and bulk actions:",
        backDesc = "Return to the dashboard.",
        pauseAllDesc = "Pause all active goals at once. Toggles to Play when all are paused.",
        helpDesc = "Opens this help page.",
        pauseAllNote = "The Pause All button only appears when you have at least one goal.",
        addingTitle = "Adding a Savings Goal",
        addingBody = "Tap \"Add Savings Goal\" and fill in:",
        addStep1 = "Name",
        addStep1Desc = "What you're saving for (e.g., \"New Tires\", \"Vacation to Hawaii\", \"Emergency Fund\").",
        addStep2 = "Target Amount",
        addStep2Desc = "The total cost you need to save.",
        addStep3 = "Starting Saved Amount",
        addStep3Desc = "Optional. If you already have some money saved toward this goal, enter it here to pre-fill the progress bar.",
        addStep4 = "Goal Type",
        addStep4Desc = "Choose \"Target Date\" to set a deadline, or \"Fixed Contribution\" for a regular per-period amount.",
        addStep5 = "Target Date / Contribution",
        addStep5Desc = "Depending on the goal type, select a target date or enter a contribution per period.",
        deductionsTitle = "How Budget Deductions Work",
        deductionsBody = "For each active (non-paused) goal, the app calculates a per-period deduction:",
        targetDateDeductionTitle = "Target Date Goals",
        targetDateDeductionFormula = "Deduction = (Target Amount \u2212 Saved So Far) \u00f7 Remaining Periods until Target Date",
        fixedDeductionTitle = "Fixed Contribution Goals",
        fixedDeductionBody = "The deduction equals the contribution per period you set when creating the goal. " +
            "It stays constant until the goal is reached.",
        deductionNote = "These deductions are subtracted from your Safe Budget Amount to produce " +
            "your actual Budget Amount. The \"Saved So Far\" automatically increases each " +
            "budget period based on the deduction amount.",
        progressTitle = "Progress Tracking",
        progressBody = "Each goal in the list shows:",
        progressName = "Name \u2014 what you're saving for",
        progressTarget = "Target amount (and target date for date-based goals)",
        progressDeduction = "Budget deduction or contribution per period",
        progressBar = "Progress bar \u2014 visual indicator of how close you are to the target",
        progressSaved = "Saved amount \u2014 green text showing accumulated savings vs. target",
        progressGoalReached = "\"Goal reached!\" label when fully saved",
        actionsTitle = "Actions",
        pauseDesc = "Temporarily stop deductions for this goal. Budget returns to normal while paused.",
        resumeDesc = "Resume deductions. The per-period amount recalculates based on remaining time and savings.",
        deleteDesc = "Permanently remove the savings goal.",
        editNote = "Tap any goal to edit its name, target amount, goal type, or other settings.",
        statusTitle = "Status Indicators",
        activeTitle = "Active",
        activeBody = "Normal state \u2014 the deduction is being applied each period and savings accumulate.",
        pausedTitle = "Paused",
        pausedBody = "Deductions are temporarily stopped. The goal appears dimmed. Pausing is useful " +
            "when you have a tight month and need the full budget temporarily. Savings progress " +
            "is preserved. When you resume, the deduction recalculates with the reduced remaining " +
            "time (for target-date goals), so it will be slightly higher.",
        goalReachedTitle = "Goal Reached",
        goalReachedBody = "Shows \"Goal reached!\" in green when Saved So Far meets or exceeds the target. " +
            "No further deductions are taken. You can delete the goal or keep it as a record.",
        manualOverrideTitle = "Manual Budget Override",
        manualOverrideBody = "If Manual Budget Override is enabled in Budget Configuration, a red warning " +
            "banner appears at the top of this screen. When manual override is active, " +
            "Savings Goal deductions are NOT subtracted from your budget \u2014 you must account " +
            "for these expenses yourself.",
        tipsTitle = "Tips",
        tip1 = "Create target-date goals as early as possible \u2014 the more time you have, the smaller each period's deduction.",
        tip2 = "Use fixed contribution goals for open-ended savings like emergency funds or general savings.",
        tip3 = "Use Pause strategically during tight months, but resume promptly to avoid a spike in deductions as the target date approaches.",
        tip4 = "Enter a starting saved amount when creating a goal if you already have money set aside.",
        tip5 = "Common uses: car maintenance, medical procedures, holiday gifts, vacations, electronics, furniture, annual subscriptions, emergency fund.",
        tip6 = "Pair this with Amortization: use Savings Goals to save before a purchase, and Amortization to spread costs after an unexpected purchase."
    )

    override val amortizationHelp = AmortizationHelpStrings(
        title = "Amortization Help",
        whatTitle = "What Is Amortization?",
        whatBody = "Amortization lets you spread the impact of a large expense across " +
            "multiple budget periods. Instead of the full cost destroying your budget " +
            "in a single day/week/month, the cost is divided evenly and deducted " +
            "from your budget over time.",
        exampleTitle = "Example",
        exampleBody = "Your car unexpectedly needs a \$900 repair. On a daily budget of \$40/day, " +
            "that would wipe out more than 22 days of budget. Instead, you create an " +
            "amortization entry for \$900 over 90 days. Your budget is reduced by only " +
            "\$10/day for 90 days, keeping you above water while the cost is absorbed gradually.",
        vsGoalsTitle = "Amortization vs. Savings Goals",
        vsGoalsBody = "These two features are complementary:",
        goalsBullet = "Savings Goals \u2014 save BEFORE a planned expense (proactive)",
        amortBullet = "Amortization \u2014 spread AFTER an unplanned or past expense (reactive)",
        headerTitle = "Header Bar",
        backDesc = "Return to the dashboard.",
        helpDesc = "Opens this help page.",
        addingTitle = "Adding an Amortization Entry",
        addingBody = "Tap \"Add Amortization Entry\" and fill in:",
        addStep1 = "Source Name",
        addStep1Desc = "A descriptive name for the expense (e.g., \"Car Repair\", \"Emergency Room Visit\", \"New Laptop\"). Important: this name is matched against bank transaction merchant names for automatic recognition.",
        addStep2 = "Total Amount",
        addStep2Desc = "The full cost of the expense.",
        addStep3 = "Budget Periods",
        addStep3Desc = "How many periods to spread the cost over. The label shows your current period type (days, weeks, or months).",
        addStep4 = "Start Date",
        addStep4Desc = "When the amortization begins (usually the date of the expense).",
        deductionsTitle = "How Deductions Work",
        deductionsBody = "The per-period deduction is straightforward:",
        deductionFormula = "Deduction = Total Amount \u00f7 Number of Budget Periods",
        deductionNote = "This deduction is subtracted from your Safe Budget Amount (along with any " +
            "Savings Goal deductions) to produce your actual Budget Amount. The deduction remains " +
            "constant for the full amortization period, then stops automatically.",
        entryListTitle = "Entry List",
        entryListBody = "Each amortization entry displays:",
        entrySource = "Source name",
        entryTotal = "Total amount and per-period deduction",
        entryProgress = "Progress \u2014 \"X of Y [periods] complete\" or \"Completed\" in green",
        actionsTitle = "Actions",
        editNote = "Tap an entry to edit its details (source name, amount, periods, start date).",
        deleteDesc = "Permanently remove the amortization entry.",
        matchingTitle = "Automatic Transaction Matching",
        matchingBody = "When you add a transaction (manually or via bank import), the app checks " +
            "whether the merchant name and amount match any of your amortization entries. " +
            "If a match is found, you're shown a confirmation dialog:",
        yesAmortBullet = "\"Yes, Amortization\" \u2014 the transaction is tagged as amortized and does NOT reduce your available cash (since the cost is already being deducted from your budget over time)",
        noRegularBullet = "\"No, Regular\" \u2014 the transaction is treated as a normal expense",
        sourceMatchingTitle = "Source Name Matching",
        sourceMatchingBody = "Use descriptive names for your amortization sources. The matching " +
            "algorithm looks for common substrings between the source name and " +
            "the transaction merchant name. For example, a source named \"Toyota Service\" " +
            "would match a bank transaction from \"TOYOTA SERVICE CENTER\".",
        manualOverrideTitle = "Manual Budget Override",
        manualOverrideBody = "If Manual Budget Override is enabled in Budget Configuration, a red warning " +
            "banner appears at the top of this screen. When manual override is active, " +
            "amortization deductions are NOT subtracted from your budget \u2014 you must " +
            "account for these costs yourself.",
        tipsTitle = "Tips",
        tip1 = "Choose a number of periods that results in a comfortable per-period deduction. If \$10/day feels too much, spread over more days.",
        tip2 = "Use amortization for any expense that would otherwise devastate your budget: medical bills, car repairs, appliance replacements, emergency travel.",
        tip3 = "Completed entries (all periods elapsed) can be deleted to keep the list clean.",
        tip4 = "Remember to also record the actual transaction \u2014 amortization only adjusts your budget rate, it doesn't record the expense itself.",
        tip5 = "If you knew about the expense in advance, Savings Goals would have been the better tool. Use Amortization for surprises."
    )

    override val recurringExpensesHelp = RecurringExpensesHelpStrings(
        title = "Recurring Expenses Help",
        whatTitle = "What Are Recurring Expenses?",
        whatBody = "Recurring expenses are bills and payments that repeat on a regular schedule: " +
            "rent, mortgage, utilities, insurance, subscriptions, loan payments, and similar " +
            "obligations. By registering them here, the budget calculator accounts for these " +
            "costs automatically, so your daily/weekly/monthly budget reflects only what's " +
            "truly available for discretionary spending.",
        whyTitle = "Why This Matters",
        whyBody = "Without recurring expenses in the budget calculator, your budget amount " +
            "would be based on income alone. You'd see a high daily budget, spend freely, " +
            "and then scramble when rent is due. Registering expenses ensures the budget " +
            "reserves enough for bills, even in months where several bills cluster together.",
        headerTitle = "Header Bar",
        backDesc = "Return to the dashboard.",
        helpDesc = "Opens this help page.",
        addingTitle = "Adding a Recurring Expense",
        addingBody = "Tap \"Add Recurring Expense\" and fill in:",
        addStep1 = "Description",
        addStep1Desc = "A descriptive name for the expense (e.g., \"Rent\", \"Netflix\", \"Car Insurance\"). Important: this name is matched against bank transaction merchant names for automatic recognition.",
        addStep2 = "Amount",
        addStep2Desc = "The amount per occurrence.",
        repeatTitle = "Repeat Settings",
        repeatBody = "Every recurring expense needs a repeat schedule so the budget calculator " +
            "knows when to expect the charge. Tap the sync icon on any expense to configure:",
        everyXDaysTitle = "Day",
        everyXDaysBody = "The expense occurs every N days (1\u2013365). Requires a Start Date. " +
            "Useful for irregular-interval expenses like medication refills.",
        everyXWeeksTitle = "Week",
        everyXWeeksBody = "The expense occurs every N weeks (1\u201352). Requires a Start Date. " +
            "The day of the week is determined by the start date.",
        biWeeklyTitle = "",
        biWeeklyBody = "",
        everyXMonthsTitle = "Month",
        everyXMonthsBody = "Occurs on a specific day of the month, every N months (1\u201312). " +
            "Pick a start date to set the day and phase. Most bills use this type: rent on the 1st, " +
            "phone bill on the 15th, etc. Days 29\u201331 are allowed when the interval is 12.",
        biMonthlyTitle = "Twice per Month (Bi-Monthly)",
        biMonthlyBody = "Occurs on two specific days each month. Enter both days (1\u201328 each). " +
            "Useful for expenses that bill twice monthly.",
        annualTitle = "Annual",
        annualBody = "Occurs once per year on a specific date. Pick a start date \u2014 any day is allowed, " +
            "including the 29th\u201331st. Useful for annual insurance premiums, memberships, or subscriptions.",
        dayLimitNote = "Day-of-month values are limited to 28 for most repeat types to ensure the date " +
            "exists in every month. Annual and 12-month intervals allow days 29\u201331.",
        expenseListTitle = "Expense List",
        expenseListBody = "Each recurring expense in the list shows:",
        expenseSource = "Description",
        expenseAmount = "Amount per occurrence",
        actionsTitle = "Actions",
        editNote = "Tap an expense to edit its name and amount.",
        repeatSettingsDesc = "Configure or change the repeat schedule.",
        deleteDesc = "Permanently remove the recurring expense.",
        budgetEffectTitle = "How Recurring Expenses Affect Your Budget",
        budgetEffectBody = "Recurring expenses play two roles in the budget system:",
        timingSafetyTitle = "1. Budget Calculation (Timing Safety)",
        timingSafetyBody = "The budget calculator projects all recurring expenses forward one year and " +
            "simulates each budget period. It ensures your budget amount is high enough " +
            "to cover bills even in months where multiple expenses cluster together. " +
            "Without this, you might have enough money overall but not enough in a " +
            "particular week or month.",
        autoMatchTitle = "2. Automatic Transaction Matching",
        autoMatchBody = "When you add a transaction (manually or via bank import), the app checks " +
            "whether the merchant name and amount match any recurring expense. If a match " +
            "is found, you're shown a confirmation dialog:",
        yesRecurringBullet = "\"Yes, Recurring\" \u2014 the transaction is tagged as a recurring expense and does NOT reduce your available cash (since it's already accounted for in the budget)",
        noRegularBullet = "\"No, Regular\" \u2014 the transaction is treated as a normal expense",
        whyMatchingTitle = "Why Matching Matters",
        whyMatchingBody = "Your budget amount already has recurring expenses \"baked in\" \u2014 the " +
            "calculator reserved money for them. If a recurring expense also subtracted " +
            "from available cash, it would be double-counted. The matching system prevents " +
            "this by recognizing recurring transactions and keeping them from affecting " +
            "your spending money.",
        sourceMatchTitle = "Source Name Matching",
        sourceMatchBody = "The automatic recognition system matches transaction merchant names against " +
            "your recurring expense source names. For best results:",
        matchBullet1 = "Use descriptive names that overlap with how the expense appears on bank statements",
        matchBullet2 = "For example, \"State Farm\" will match \"STATE FARM INSURANCE\" from your bank",
        matchBullet3 = "The match looks for common substrings, so partial matches work",
        matchBullet4 = "Amount must also be within 1% for the match to trigger",
        tipsTitle = "Tips",
        tip1 = "Add ALL recurring expenses, even small ones like streaming subscriptions. They add up and the budget calculator needs the full picture.",
        tip2 = "If an expense amount varies slightly (like a utility bill), use the average amount.",
        tip3 = "Your budget recalculates automatically when you add or remove recurring expenses.",
        tip4 = "Common expenses to add: rent/mortgage, utilities (electric, gas, water), insurance (car, health, home), subscriptions (streaming, gym, software), loan payments, phone bill.",
        tip5 = "If an expense is truly one-time, don't add it here. Use Amortization instead to spread it over time.",
        tip6 = "Check your bank statements to make sure you haven't missed any recurring charges."
    )

    override val familySyncHelp = FamilySyncHelpStrings(
        title = "Family Sync Help",
        whatTitle = "What is Family Sync?",
        whatBody = "Family Sync lets you share a single household budget across up to 5 devices. " +
            "All transactions, income sources, recurring expenses, savings goals, and settings " +
            "are kept in sync automatically using end-to-end encrypted cloud relay. " +
            "No one \u2014 not even the server \u2014 can read your financial data.",
        adminRoleTitle = "The Admin Role",
        adminRoleBody = "The person who creates the family group becomes the admin. The admin can " +
            "change shared budget settings (currency, budget period, reset schedule), " +
            "generate pairing codes to invite new devices, set the family timezone, " +
            "enable transaction attribution, and dissolve the group. Non-admin members " +
            "can view settings but cannot change them.",
        gettingStartedTitle = "Getting Started",
        gettingStartedBody = "To set up Family Sync: Open Settings, tap Family Sync, and tap " +
            "\"Create Family Group\". A sync group is created with you as admin. " +
            "Then tap \"Generate Pairing Code\" and share the 6-character code with " +
            "family members. They enter the code on their device to join. " +
            "Codes expire after 10 minutes for security.",
        joiningTitle = "Joining a Group",
        joiningBody = "To join an existing group, tap \"Join Family Group\" and enter the " +
            "6-character pairing code. Important: joining replaces your local budget data " +
            "with the group's shared data. Your current transactions, goals, and settings " +
            "will be overwritten. Make sure to back up first if needed.",
        syncStatusTitle = "Sync Status",
        syncStatusBody = "The colored dot on the dashboard and the status card on this screen " +
            "show the current sync state: Green (Synced) means all data is up to date. " +
            "Yellow (Syncing) means a sync is in progress. Orange (Stale) means " +
            "it has been a while since the last sync. Red (Error) means the last " +
            "sync attempt failed.",
        staleWarningsTitle = "Stale Warnings",
        staleWarningsBody = "If your device hasn't synced for an extended period, escalating " +
            "warnings appear on the dashboard: at 60 days a gentle reminder, " +
            "at 75 days a countdown, at 85 days an urgent warning, and at 90 days " +
            "sync is blocked and a full refresh from the group snapshot is required. " +
            "Sync regularly to avoid this.",
        attributionTitle = "Transaction Attribution",
        attributionBody = "When enabled by the admin, each transaction in the list shows which device " +
            "created it. Your own transactions show \"You\" and transactions from other " +
            "devices show the device name. This helps families see who recorded each expense.",
        adminClaimsTitle = "Admin Claims",
        adminClaimsBody = "If the admin device is lost or unavailable, any member can claim the " +
            "admin role. Tap \"Claim Admin Role\" on the Family Sync screen. Other members " +
            "have 24 hours to object. If no one objects, the claim is approved and you " +
            "become the new admin. If someone objects, the claim is rejected.",
        leavingTitle = "Leaving or Dissolving",
        leavingBody = "Non-admin members can leave a group at any time. Your local data is kept " +
            "but stops syncing. The admin can dissolve the group entirely, which " +
            "disconnects all members and deletes all sync data from the server. " +
            "Local data on each device is preserved.",
        privacyTitle = "Privacy & Security",
        privacyBody = "All sync data is encrypted end-to-end with a 256-bit key generated " +
            "when the group is created. The key is shared only via the pairing code " +
            "mechanism. The server stores only encrypted blobs \u2014 it cannot read " +
            "your transactions, amounts, merchant names, or any financial data. " +
            "Your money, your data, your control."
    )
}
