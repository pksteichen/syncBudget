package com.syncbudget.app.ui.strings

object SpanishStrings : AppStrings {

    override val defaultCategoryNames = DefaultCategoryNames(
        other = "Otros",
        recurring = "Recurrente",
        amortization = "Amortizaci\u00f3n",
        recurringIncome = "Ingreso Recurrente",
        transportation = "Transporte/Gasolina",
        groceries = "Supermercado",
        entertainment = "Entretenimiento",
        homeSupplies = "Hogar",
        restaurants = "Restaurantes",
        charity = "Caridad",
        clothes = "Ropa"
    )

    override val common = CommonStrings(
        ok = "OK",
        cancel = "Cancelar",
        delete = "Eliminar",
        save = "Guardar",
        back = "Volver",
        help = "Ayuda",
        reset = "Restablecer",
        close = "Cerrar",
        periodDay = "d\u00eda",
        periodWeek = "semana",
        periodMonth = "mes",
        periodDays = "d\u00edas",
        periodWeeks = "semanas",
        periodMonths = "meses",
        addNewIncomeTransaction = "Nueva transacci\u00f3n de ingreso",
        addNewExpenseTransaction = "Nueva transacci\u00f3n de gasto",
        sourceLabel = "Descripci\u00f3n",
        merchantLabel = "Descripci\u00f3n",
        repeatTypeDays = "D\u00eda",
        repeatTypeWeeks = "Semana",
        repeatTypeBiWeekly = "Cada 2 semanas",
        repeatTypeMonths = "Mes",
        repeatTypeBiMonthly = "Dos veces al mes",
        repeatTypeAnnual = "Anual",
        budgetPeriodDaily = "Diario",
        budgetPeriodWeekly = "Semanal",
        budgetPeriodMonthly = "Mensual",
        sourceName = "Descripci\u00f3n",
        amount = "Monto",
        repeatType = "Tipo de repetici\u00f3n",
        everyXDays = "D\u00edas entre repeticiones (1-365)",
        intervalWeeks = "Semanas entre repeticiones (1-52)",
        everyXMonths = "Meses entre repeticiones (1-12)",
        dayOfMonth = "D\u00eda del mes (1-28)",
        firstDayOfMonth = "Primer d\u00eda (1-28)",
        secondDayOfMonth = "Segundo d\u00eda (1-28)",
        pickStartDate = "Elegir fecha de inicio",
        startDateLabel = { date -> "Fecha de inicio: $date" },
        selectAStartDate = "Selecciona una fecha de inicio",
        dayOfWeekLabel = { day -> "D\u00eda de la semana: $day" },
        requiredSourceExample = "Obligatorio, ej. Supermercado",
        exampleAmount = "ej. 42.50",
        exampleDays = "ej. 14",
        exampleWeeks = "ej. 2",
        exampleMonths = "ej. 1",
        exampleMonthDay = "ej. 15",
        exampleBiMonthlyDay1 = "ej. 1",
        exampleBiMonthlyDay2 = "ej. 15",
        language = "Idioma",
        dateDayTooHigh = "Selecciona una fecha entre el 1 y el 28 del mes"
    )

    override val dashboard = DashboardStrings(
        appTitle = "SecureSync Presupuesto",
        notConfigured = "Sin configurar",
        spending = "Gastos",
        settings = "Ajustes",
        transactions = "Transacciones",
        savingsGoals = "Metas de Ahorro",
        amortization = "Amortizaci\u00f3n",
        recurringExpenses = "Gastos Recurrentes",
        addIncome = "Agregar ingreso",
        addExpense = "Agregar gasto",
        supercharge = "Potenciar",
        superchargeTitle = "Potenciar Metas de Ahorro",
        superchargeRemaining = { remaining -> "Restante: $remaining" },
        superchargeAllocate = "Asignar",
        range7d = "7 d\u00edas",
        range30d = "30 d\u00edas",
        range90d = "90 d\u00edas",
        rangeAll = "Todo",
        noDataAvailable = "No hay datos disponibles",
        budgetLabel = { amount, period -> "$amount/$period" },
        superchargeReduceContributions = "Reducir contribuciones futuras",
        superchargeAchieveSooner = "Alcanzar meta antes",
        superchargeExtraShouldLabel = "La contribuci\u00f3n extra debe...",
        superchargeNewContribution = { amount, period -> "Nuevo: $amount/$period" },
        superchargeNewPayoff = { date -> "Nuevo pago final: $date" },
        superchargeNewCompletion = { date -> "Se completa: $date" },
        superchargeAutoAdjust = "Las contribuciones se ajustan automáticamente",
        superchargeTargetDateNote = "Esto cambiará tu fecha objetivo"
    )

    override val settings = SettingsStrings(
        title = "Ajustes",
        configureYourBudget = "Configurar tu presupuesto",
        currency = "Moneda",
        showDecimalPlaces = "Mostrar decimales",
        dateFormat = "Formato de fecha",
        weekStartsOn = "La semana empieza el",
        sunday = "Domingo",
        monday = "Lunes",
        chartPalette = "Paleta de gr\u00e1ficos",
        bright = "Vibrante",
        pastel = "Pastel",
        sunset = "Atardecer",
        matchDays = "D\u00edas de coincidencia (\u00b1N)",
        matchPercent = "Porcentaje de coincidencia (\u00b1%)",
        matchDollar = "Monto de coincidencia (\u00b1\$)",
        matchChars = "Caracteres de coincidencia",
        matchingConfiguration = "Configuraci\u00f3n de coincidencias",
        paidUser = "Usuario de pago",
        categories = "Categor\u00edas",
        addCategory = "Agregar categor\u00eda",
        editCategory = "Editar categor\u00eda",
        categoryName = "Nombre de la categor\u00eda",
        chooseIcon = "Elige un icono:",
        deleteCategoryTitle = { name -> "\u00bfEliminar $name?" },
        deleteCategoryNoTransactions = "Ninguna transacci\u00f3n usa esta categor\u00eda. Se eliminar\u00e1 de inmediato.",
        reassignCategoryTitle = { name, count -> "Reasignar $count transacciones de $name" },
        reassignCategoryBody = { name, count -> "$count transacci\u00f3n${if (count != 1) "es" else ""} usa${if (count != 1) "n" else ""} la categor\u00eda \"$name\". Elige una categor\u00eda de reemplazo antes de eliminar." },
        moveTo = "Mover a",
        moveAndDelete = "Mover y eliminar",
        languageLabel = "Idioma",
        english = "English",
        spanish = "Espa\u00f1ol"
    )

    override val budgetConfig = BudgetConfigStrings(
        title = "Configuraci\u00f3n del presupuesto",
        budgetPeriod = "Per\u00edodo del presupuesto",
        safeBudgetAmountLabel = { symbol, amount, period -> "Presupuesto seguro: $symbol$amount/$period" },
        budgetTrackingSince = { date -> "Seguimiento del presupuesto desde: $date" },
        refreshTime = "Hora de refresco",
        startResetBudget = "Iniciar/Restablecer",
        manualBudgetOverride = "Presupuesto manual",
        budgetAmountPer = { period -> "Monto por $period" },
        manualOverrideWarning = "El modo manual desactiva las deducciones de Amortizaci\u00f3n y Metas de Ahorro.",
        incomeSourceDescription = "Agrega fuentes de ingreso estables con las que puedas contar para tu presupuesto. Si tu pago var\u00eda (cheque grande, cheque peque\u00f1o), puedes crear m\u00e1s de una entrada por fuente.",
        addIncomeSource = "Agregar fuente de ingreso",
        editIncomeSource = "Editar fuente de ingreso",
        deleteSourceConfirmTitle = { name -> "\u00bfEliminar $name?" },
        deleteSourceConfirmBody = "Esta fuente de ingreso se eliminar\u00e1 permanentemente.",
        resetBudgetConfirmTitle = "\u00bfIniciar/Restablecer presupuesto?",
        resetBudgetConfirmBody = "Esto restablecer\u00e1 la fecha de inicio a hoy y fijar\u00e1 el efectivo disponible en el monto de un per\u00edodo. Tus transacciones no se ver\u00e1n afectadas.",
        resetSettingsTitle = "Ajustes de hora de refresco",
        resetHour = "Hora de reinicio",
        dayOfWeekLabel = "D\u00eda de la semana",
        dayOfMonthReset = "D\u00eda del mes (1-28)",
        requiredPaycheckExample = "Obligatorio, ej. N\u00f3mina",
        exampleIncomeAmount = "ej. 2500.00"
    )

    override val transactions = TransactionsStrings(
        title = "Transacciones",
        all = "Todas",
        expensesFilter = "Gastos",
        incomeFilter = "Ingresos",
        addIncome = "Agregar ingreso",
        addExpense = "Agregar gasto",
        editTransaction = "Editar transacci\u00f3n",
        search = "Buscar",
        dateSearch = "Buscar por fecha",
        textSearch = "Buscar por texto",
        amountSearch = "Buscar por monto",
        searchBy = "Buscar por",
        searchResults = "Resultados de b\u00fasqueda",
        tapToClearSearch = "Toca para borrar la b\u00fasqueda",
        noTransactions = "Sin transacciones",
        selectAll = "Seleccionar todo",
        changeCategory = "Cambiar categor\u00eda",
        editMerchant = "Editar comercio/fuente",
        deleteSelected = "Eliminar seleccionados",
        selectedCount = { count -> "$count seleccionado${if (count != 1) "s" else ""}" },
        date = "Fecha",
        merchant = "Comercio",
        category = "Categor\u00eda",
        amount = "Monto",
        total = "Total",
        pieChart = "Modo gr\u00e1fico circular",
        calculator = "Modo por montos",
        percentage = "Modo por porcentajes",
        save = "Guardar",
        load = "Cargar",
        saveTransactions = "Guardar transacciones",
        loadTransactions = "Importar / Cargar",
        csv = "CSV",
        encrypted = "Cifrado",
        password = "Contrase\u00f1a",
        confirmPassword = "Confirmar contrase\u00f1a",
        selectFile = "Seleccionar archivo",
        usBank = "US Bank",
        secureSyncCsv = "Archivo CSV de SecureSync",
        secureSyncEncrypted = "Archivo cifrado de SecureSync",
        duplicateDetected = "Posible duplicado",
        duplicateExisting = "Existente:",
        duplicateNew = "Nuevo:",
        ignore = "Ignorar",
        keepNew = "Conservar nuevo",
        keepExisting = "Conservar existente",
        ignoreAll = "Ignorar todos",
        recurringExpenseMatch = "Transacci\u00f3n recurrente detectada",
        recurringMatchTitle = { source -> "Gasto recurrente encontrado:" },
        recurringMatchBody = { source, amount -> "$source \u2014 $amount" },
        yesRecurring = "S\u00ed, es recurrente",
        noRegularExpense = "No, no es recurrente",
        amortizationMatch = "Transacci\u00f3n de amortizaci\u00f3n detectada",
        amortizationMatchTitle = { source -> "Entrada de amortizaci\u00f3n encontrada:" },
        amortizationMatchBody = { source, amount -> "$source \u2014 $amount" },
        yesAmortization = "S\u00ed, es amortizaci\u00f3n",
        noRegularAmort = "No, no es amortizaci\u00f3n",
        budgetIncomeMatch = "Ingreso presupuestado detectado",
        budgetIncomeMatchTitle = { source -> "Fuente de ingreso encontrada:" },
        budgetIncomeMatchBody = { source, amount -> "$source \u2014 $amount" },
        yesBudgetIncome = "S\u00ed, ingreso presupuestado",
        noExtraIncome = "No, ingreso extra",
        dateAdvisory = "Nota: La fecha de esta transacci\u00f3n difiere del calendario esperado por m\u00e1s de 2 d\u00edas. Considera actualizar la configuraci\u00f3n de tu gasto recurrente.",
        savedSuccessfully = { count -> "$count transacciones guardadas correctamente." },
        loadedSuccessfully = { loaded, total -> "$loaded de $total transacciones cargadas." },
        allSkipped = { total -> "0 de $total transacciones cargadas. Datos previamente cargados omitidos." },
        passwordMinLength = "La contrase\u00f1a debe tener al menos 8 caracteres",
        passwordsMustMatch = "Las contrase\u00f1as no coinciden",
        newMerchantName = "Descripci\u00f3n",
        filterByCategory = { name -> "Filtrado por: $name" },
        tapToClearFilter = "Toca para borrar el filtro",
        startDate = "Seleccionar fecha de inicio",
        endDate = "Seleccionar fecha de fin",
        minAmount = "Monto m\u00ednimo",
        maxAmount = "Monto m\u00e1ximo",
        searchText = "T\u00e9rminos de b\u00fasqueda",
        from = "Desde",
        to = "Hasta",
        format = "Formato:",
        parseError = "Error de lectura",
        unknownError = "Error desconocido",
        parsedBeforeError = { count: Int -> "$count transacciones le\u00eddas antes del error." },
        keep = "Conservar",
        requiredMerchantExample = "Obligatorio, ej. Supermercado",
        moveCategoryValue = "Mover valor de categor\u00eda",
        sumMismatch = "La suma no coincide",
        maxAmount2 = { max: String -> "M\u00e1x: $max" },
        includeAllData = "Incluir todos los datos (respaldo completo)",
        fullBackupNote = "Se guardar\u00e1n todos los datos de la app incluyendo ajustes, categor\u00edas, gastos recurrentes, metas de ahorro y entradas de amortizaci\u00f3n.",
        fullBackupDetected = "Respaldo completo detectado",
        fullBackupBody = "Este archivo contiene ajustes, categor\u00edas, gastos recurrentes, metas de ahorro, entradas de amortizaci\u00f3n y todos los dem\u00e1s datos. \u00bfC\u00f3mo desea cargarlo?",
        loadTransactionsOnly = "Cargar solo transacciones",
        loadAllDataOverwrite = "Cargar todo y sobrescribir",
        fullBackupRestored = "Respaldo completo restaurado exitosamente",
        fullBackupSaved = "Respaldo completo guardado exitosamente",
        fullBackupSyncWarning = "Restaurar un respaldo completo disolverá el grupo de sincronización familiar actual. Deberás crear un nuevo grupo y compartir un nuevo código de emparejamiento con los miembros de la familia.",
        fullBackupNonAdminBlock = "Solo el administrador del grupo puede restaurar un respaldo completo. Una restauración completa corrompería el estado de sincronización de todos los dispositivos.",
        fullBackupGroupRecreated = "Respaldo restaurado. Nuevo grupo familiar creado \u2014 comparte el código de emparejamiento con los miembros de la familia.",
        fullBackupGroupDissolved = "Respaldo restaurado. Grupo familiar disuelto."
    )

    override val futureExpenditures = FutureExpendituresStrings(
        title = "Metas de Ahorro",
        description = "Planifica y ahorra para gastos futuros u objetivos financieros. La app deduce autom\u00e1ticamente una peque\u00f1a cantidad en cada per\u00edodo.",
        manualOverrideWarning = "El presupuesto manual est\u00e1 activado. Las deducciones de Metas de Ahorro NO se est\u00e1n restando de tu presupuesto.",
        addSavingsGoal = "Agregar meta de ahorro",
        editSavingsGoal = "Editar meta de ahorro",
        name = "Descripci\u00f3n",
        targetAmount = "Monto objetivo",
        startingSavedAmount = "Monto ya ahorrado",
        targetDate = "Fecha objetivo",
        fixedContribution = "Contribuci\u00f3n fija",
        contributionPerPeriod = "Contribuci\u00f3n por per\u00edodo",
        selectTargetDate = "Seleccionar fecha objetivo",
        targetDateLabel = { date -> "Fecha objetivo: $date" },
        goalReached = "\u00a1Meta alcanzada!",
        paused = "Pausada",
        budgetReduction = { amount, period -> "-$amount/$period" },
        contributionLabel = { amount, period -> "$amount/$period" },
        savedOf = { saved, total -> "$saved de $total ahorrado" },
        targetAmountBy = { amount, date -> "$amount para el $date" },
        targetLabel = { amount -> "Objetivo: $amount" },
        deleteSavingsGoal = "\u00bfEliminar meta de ahorro?",
        deleteGoalConfirm = { name -> "\u00bfEliminar permanentemente \"$name\"?" },
        pauseAll = "Pausar todas",
        resumeAll = "Reanudar todas",
        pause = "Pausar",
        resume = "Reanudar",
        selectAFutureDate = "Selecciona una fecha futura",
        requiredNameExample = "Obligatorio, ej. Llantas nuevas",
        exampleTargetAmount = "ej. 1000.00",
        exampleContribution = "ej. 5.00",
        mustBeLessThanTarget = "Debe ser menor que el objetivo",
        payoffDate = { date -> "Pago final: $date" }
    )

    override val amortization = AmortizationStrings(
        title = "Amortizaci\u00f3n",
        description = "Distribuye un gasto grande a lo largo de varios per\u00edodos para que no golpee tu presupuesto de una sola vez.",
        manualOverrideWarning = "El presupuesto manual est\u00e1 activado. Las deducciones de amortizaci\u00f3n NO se est\u00e1n restando de tu presupuesto.",
        addEntry = "Agregar entrada de amortizaci\u00f3n",
        editEntry = "Editar entrada de amortizaci\u00f3n",
        sourceName = "Descripci\u00f3n",
        totalAmount = "Monto total",
        budgetPeriods = { period -> "Per\u00edodos ($period)" },
        selectStartDate = "Elegir fecha de inicio",
        startDateLabel = { date -> "Fecha de inicio: $date" },
        completed = "Completada",
        xOfYComplete = { x, y, period -> "$x de $y $period completados" },
        totalPerPeriod = { symbol, total, symbol2, perPeriod -> "$symbol$total ($symbol2$perPeriod/per\u00edodo)" },
        deleteEntryTitle = "\u00bfEliminar entrada de amortizaci\u00f3n?",
        deleteEntryConfirm = { name -> "\u00bfEliminar permanentemente \"$name\"?" },
        requiredLaptopExample = "Obligatorio, ej. Laptop",
        exampleTotalAmount = "ej. 900.00",
        examplePeriods = "ej. 90",
        selectAStartDate = "Selecciona una fecha de inicio"
    )

    override val recurringExpenses = RecurringExpensesStrings(
        title = "Gastos Recurrentes",
        description = "Registra facturas, suscripciones y pagos recurrentes para que la calculadora de presupuesto los tenga en cuenta autom\u00e1ticamente.",
        addExpense = "Agregar gasto recurrente",
        editExpense = "Editar gasto recurrente",
        deleteExpenseTitle = { name -> "\u00bfEliminar $name?" },
        deleteExpenseBody = "Este gasto recurrente se eliminar\u00e1 permanentemente.",
        descriptionLabel = "Descripci\u00f3n",
        requiredNetflixExample = "Obligatorio, ej. Netflix",
        exampleAmount = "ej. 15.99"
    )

    override val sync = SyncStrings(
        title = "Sincronizaci\u00f3n Familiar",
        familySync = "Sincronizaci\u00f3n Familiar",
        familySyncDescription = "Sincroniza tu presupuesto en hasta 5 dispositivos de tu hogar.",
        createGroup = "Crear Grupo Familiar",
        createGroupDescription = "Inicia un nuevo grupo de sincronizaci\u00f3n e invita a familiares con un c\u00f3digo de emparejamiento.",
        joinGroup = "Unirse a Grupo Familiar",
        joinGroupDescription = "Ingresa un c\u00f3digo de emparejamiento de un familiar para unirte a su grupo.",
        leaveGroup = "Salir del Grupo",
        dissolveGroup = "Disolver Grupo",
        syncNow = "Sincronizar Ahora",
        lastSynced = { time -> "\u00daltima sincronizaci\u00f3n: $time" },
        syncing = "Sincronizando...",
        syncError = "Error de sincronizaci\u00f3n",
        notConfigured = "Sin configurar",
        groupId = "ID del Grupo",
        pairingCode = "C\u00f3digo de Emparejamiento",
        enterPairingCode = "Ingresa el c\u00f3digo de emparejamiento",
        pairingCodeExpiry = "El c\u00f3digo expira en 10 minutos",
        generateCode = "Generar C\u00f3digo",
        deviceRoster = "Lista de Dispositivos",
        devices = "Dispositivos",
        thisDevice = "Este dispositivo",
        admin = "Administrador",
        transferAdmin = "Transferir Admin",
        removeDevice = "Eliminar Dispositivo",
        confirmLeave = "\u00bfEst\u00e1s seguro de que quieres salir de este grupo? Tus datos locales se conservar\u00e1n pero dejar\u00e1n de sincronizarse.",
        confirmDissolve = "\u00bfEst\u00e1s seguro de que quieres disolver este grupo? Todos los miembros ser\u00e1n desconectados y los datos de sincronizaci\u00f3n se eliminar\u00e1n.",
        connected = "Conectado",
        disconnected = "Desconectado",
        syncStatusSynced = "Sincronizado",
        syncStatusSyncing = "Sincronizando",
        syncStatusStale = "Desactualizado",
        syncStatusError = "Error",
        syncStatusOff = "Desactivado",
        groupCreated = "Grupo familiar creado",
        joinedGroup = "Te uniste al grupo familiar",
        leftGroup = "Saliste del grupo familiar",
        groupDissolved = "Grupo disuelto",
        pairingCodeCopied = "C\u00f3digo copiado",
        invalidPairingCode = "C\u00f3digo inv\u00e1lido o expirado",
        encryptionKey = "Clave de Cifrado",
        deviceName = "Nombre del Dispositivo",
        adminOnly = "Solo administrador",
        familyTimezone = "Zona Horaria Familiar",
        selectTimezone = "Seleccionar Zona Horaria",
        showAttributionLabel = "Mostrar Atribuci\u00f3n",
        you = "T\u00fa",
        staleWarning60 = "Sincroniza pronto para mantener tus datos actualizados",
        staleWarning75 = "15 d\u00edas para que los datos de sincronizaci\u00f3n expiren",
        staleWarning85 = "5 d\u00edas para sincronizar o los cambios locales deber\u00e1n descartarse",
        staleBlocked = "Sincronizaci\u00f3n bloqueada \u2014 se requiere actualizaci\u00f3n completa",
        claimAdmin = "Reclamar Rol de Admin",
        objectClaim = "Objetar",
        claimPending = "Reclamo de admin pendiente",
        claimApproved = "Rol de admin transferido",
        claimRejected = "Reclamo de admin rechazado",
        claimExpiry = { time -> "El reclamo expira: $time" },
        claimBy = { name -> "$name est\u00e1 reclamando el rol de admin" },
        errorRemovedFromGroup = "Has sido eliminado de este grupo",
        errorGroupDeleted = "Este grupo ha sido disuelto",
        errorEncryption = "Error de cifrado \u2014 verifica tu emparejamiento",
        joinWarningTitle = "\u00bfReemplazar Datos Locales?",
        joinWarningBody = "Unirte a un grupo familiar reemplazar\u00e1 tus datos locales con los datos compartidos del grupo. Tus transacciones, metas y configuraciones actuales ser\u00e1n sobrescritas. Esto no se puede deshacer."
    )

    // ── Help Screen Strings ──

    override val dashboardHelp = DashboardHelpStrings(
        title = "Ayuda del panel principal",
        welcomeTitle = "Bienvenido a SecureSync Presupuesto Diario",
        welcomeBody = "SecureSync Presupuesto Diario es una app de presupuesto que prioriza tu privacidad, " +
            "dise\u00f1ada para darte una imagen clara y en tiempo real de cu\u00e1nto puedes gastar " +
            "con tranquilidad en este momento. A diferencia de los rastreadores tradicionales que solo " +
            "te muestran en qu\u00e9 gastaste, esta app te dice en qu\u00e9 puedes gastar \u2014 " +
            "calculado a partir de tu calendario de ingresos real, facturas recurrentes y metas financieras.",
        dailyBudgetNumberTitle = "Tu n\u00famero de presupuesto diario",
        dailyBudgetNumberBody = "El n\u00famero grande en la pantalla Solari es tu Efectivo Disponible \u2014 la cantidad " +
            "que puedes gastar ahora mismo sin poner en riesgo tus facturas, metas de ahorro ni " +
            "compromisos financieros. Pi\u00e9nsalo como la respuesta a la pregunta que todos se hacen: " +
            "\"\u00bfCu\u00e1nto me puedo permitir gastar hoy?\"",
        solariDisplayTitle = "La pantalla Solari",
        solariDisplayBody = "La pieza central de la app es la pantalla tipo Solari \u2014 inspirada en los " +
            "tableros de solapas de las estaciones de tren y aeropuertos. Muestra dos datos clave:",
        availableCashTitle = "Efectivo disponible (n\u00famero principal)",
        availableCashBody = "Es el total acumulado de dinero disponible para gastos discrecionales. " +
            "Comienza con el monto de un per\u00edodo cuando configuras tu presupuesto por primera vez " +
            "y se actualiza en tiempo real:",
        bullet1 = "Aumenta en cada per\u00edodo (diario, semanal o mensual) seg\u00fan tu monto de presupuesto",
        bullet2 = "Disminuye cuando registras un gasto",
        bullet3 = "Aumenta cuando registras ingresos extra (no presupuestados)",
        bullet4 = "Se muestra en rojo/negativo cuando has gastado de m\u00e1s",
        budgetLabelTitle = "Etiqueta del presupuesto (debajo del n\u00famero)",
        budgetLabelBody = "La etiqueta debajo de los d\u00edgitos muestra la tasa de tu presupuesto \u2014 por ejemplo, " +
            "\"\$42.50/d\u00eda\" o \"\$297.50/semana\". Esto indica cu\u00e1nto se agrega a " +
            "tu efectivo disponible en cada per\u00edodo. Si tu presupuesto a\u00fan no est\u00e1 configurado, " +
            "muestra \"Sin configurar\".",
        headerBarTitle = "Barra superior",
        headerBarBody = "La barra superior da acceso a los ajustes y a esta p\u00e1gina de ayuda:",
        headerSettingsDesc = "Abre la pantalla de Ajustes para configurar opciones de visualizaci\u00f3n, categor\u00edas y acceder a la Configuraci\u00f3n del presupuesto.",
        headerHelpDesc = "Abre esta p\u00e1gina de ayuda.",
        navBarTitle = "Barra de navegaci\u00f3n",
        navBarBody = "La barra de navegaci\u00f3n inferior da acceso r\u00e1pido a todas las funciones principales:",
        navTransactionsDesc = "Registra y gestiona tus ingresos y gastos. Importa extractos bancarios, busca, filtra y categoriza.",
        navSavingsDesc = "Planifica y ahorra para gastos futuros u objetivos financieros. Elige una fecha objetivo o una contribuci\u00f3n fija por per\u00edodo.",
        navAmortizationDesc = "Distribuye un gasto grande del pasado a lo largo de varios per\u00edodos para que no golpee tu presupuesto de una sola vez.",
        navRecurringDesc = "Registra facturas, suscripciones y pagos de pr\u00e9stamos para que la calculadora de presupuesto los tenga en cuenta autom\u00e1ticamente.",
        spendingChartTitle = "Gr\u00e1fico de gastos",
        spendingChartBody = "Debajo de la pantalla Solari, un gr\u00e1fico de gastos muestra c\u00f3mo se distribuyen " +
            "tus gastos por categor\u00eda. Una barra de t\u00edtulo encima del gr\u00e1fico ofrece controles:",
        chartTitleBarTitle = "Barra de t\u00edtulo del gr\u00e1fico",
        chartRangeBullet = "Bot\u00f3n de rango (izquierda) \u2014 alterna entre rangos de tiempo: 7 d\u00edas, 30 d\u00edas, 90 d\u00edas o Todo",
        chartSpendingBullet = "T\u00edtulo \"Gastos\" (centro) \u2014 la etiqueta del gr\u00e1fico",
        chartToggleBullet = "Selector de tipo de gr\u00e1fico (derecha) \u2014 alterna entre gr\u00e1fico circular y gr\u00e1fico de barras",
        chartPaletteTitle = "Paleta de gr\u00e1ficos",
        chartPaletteBody = "Los colores del gr\u00e1fico se pueden cambiar en Ajustes en \"Paleta de gr\u00e1ficos\". " +
            "Hay tres paletas disponibles: Vibrante, Pastel y Atardecer. Cada paleta se ajusta " +
            "autom\u00e1ticamente para el modo claro y oscuro.",
        quickButtonsTitle = "Botones r\u00e1pidos de transacci\u00f3n",
        quickButtonsBody = "Debajo del gr\u00e1fico, dos botones grandes te permiten agregar transacciones r\u00e1pidamente " +
            "sin salir del panel principal:",
        quickAddIncomeDesc = "Abre el di\u00e1logo de Agregar ingreso. La transacci\u00f3n se guarda y el efectivo disponible aumenta.",
        quickAddExpenseDesc = "Abre el di\u00e1logo de Agregar gasto. La transacci\u00f3n se guarda y el efectivo disponible disminuye.",
        quickMatchingNote = "Las transacciones agregadas desde el panel principal pasan por las mismas verificaciones " +
            "que en la pantalla de Transacciones: detecci\u00f3n de duplicados, coincidencia con gastos recurrentes, " +
            "coincidencia con amortizaciones y detecci\u00f3n de ingresos presupuestados.",
        superchargeTitle = "Potenciar",
        superchargeBody = "El icono de rayo en la esquina inferior derecha del panel principal abre Potenciar. " +
            "Esta funci\u00f3n te permite hacer contribuciones extra puntuales a tus Metas de Ahorro " +
            "desde tu efectivo disponible.",
        superchargeIconDesc = "Asigna fondos extra a una o m\u00e1s Metas de Ahorro desde tu efectivo disponible actual.",
        superchargeDialogBody = "En el di\u00e1logo de Potenciar, introduce un monto para cada meta que quieras impulsar. " +
            "El total se descuenta de tu efectivo disponible y se suma al ahorro acumulado de las metas. " +
            "Es \u00fatil cuando tienes un excedente y quieres alcanzar una meta m\u00e1s r\u00e1pido.",
        howBudgetWorksTitle = "C\u00f3mo funciona el presupuesto",
        howBudgetWorksBody = "El motor de presupuesto ejecuta una simulaci\u00f3n de flujo de caja usando tu calendario " +
            "de ingresos y gastos recurrentes para determinar un monto seguro de gasto por per\u00edodo.",
        safeBudgetTitle = "Presupuesto seguro",
        safeBudgetBody = "Es lo m\u00e1ximo que puedes gastar por per\u00edodo (d\u00eda, semana o mes) sin quedarte " +
            "sin dinero para cubrir tus facturas. El c\u00e1lculo:",
        safeBudgetStep1 = "Proyecci\u00f3n de ingresos",
        safeBudgetStep1Desc = "Tus fuentes de ingreso y sus calendarios de repetici\u00f3n se proyectan un a\u00f1o hacia adelante.",
        safeBudgetStep2 = "Simulaci\u00f3n de gastos",
        safeBudgetStep2Desc = "Tus gastos recurrentes se proyectan en el mismo per\u00edodo.",
        safeBudgetStep3 = "Protecci\u00f3n de timing",
        safeBudgetStep3Desc = "El motor se asegura de que incluso en meses con muchas facturas juntas, el monto del presupuesto cubra todas las obligaciones.",
        budgetAmountTitle = "Monto del presupuesto",
        budgetAmountBody = "Tu presupuesto real por per\u00edodo es el Presupuesto Seguro menos las deducciones activas:",
        budgetSavingsBullet = "Deducciones de Metas de Ahorro \u2014 dinero reservado para compras planeadas y objetivos de ahorro",
        budgetAmortBullet = "Deducciones de Amortizaci\u00f3n \u2014 distribuyendo gastos grandes del pasado a lo largo del tiempo",
        budgetAmountNote = "Esto asegura que tu dinero para gastar ya est\u00e9 ajustado tanto para gastos grandes futuros como pasados.",
        availableCashSectionTitle = "Efectivo disponible",
        availableCashSectionBody = "El Efectivo Disponible es el n\u00famero que se muestra en la pantalla Solari. En cada per\u00edodo, " +
            "tu monto de presupuesto se suma al total. Cada gasto que registras se resta. " +
            "Los ingresos extra (no contabilizados en tus fuentes de ingreso) se suman. " +
            "El resultado: un solo n\u00famero que te dice exactamente cu\u00e1nto puedes gastar.",
        gettingStartedTitle = "Primeros pasos",
        gettingStartedBody = "Sigue estos pasos para configurar tu presupuesto por primera vez:",
        step1Title = "Abre Ajustes",
        step1Desc = "Toca el icono de engranaje arriba a la izquierda para configurar tu moneda, preferencias de visualizaci\u00f3n y categor\u00edas de transacciones.",
        step2Title = "Configura tu presupuesto",
        step2Desc = "En Ajustes, toca \"Configurar tu presupuesto\" para abrir la Configuraci\u00f3n del presupuesto. Elige un per\u00edodo (Diario es lo recomendado para la mayor\u00eda).",
        step3Title = "Agrega fuentes de ingreso",
        step3Desc = "En Configuraci\u00f3n del presupuesto, agrega todas tus fuentes de ingreso confiables \u2014 tu salario, ingresos extra regulares, etc. Configura el calendario de repetici\u00f3n de cada una (ej. \"Mes\" el 1 y el 15 para pagos quincenales).",
        step4Title = "Agrega gastos recurrentes",
        step4Desc = "Ve a Gastos Recurrentes (el icono de sincronizaci\u00f3n en el panel principal) y agrega todas tus facturas regulares: alquiler, servicios, seguros, suscripciones, pagos de pr\u00e9stamos.",
        step5Title = "Inicia tu presupuesto",
        step5Desc = "De vuelta en Configuraci\u00f3n del presupuesto, tu presupuesto seguro se calcula autom\u00e1ticamente. Toca \"Iniciar/Restablecer\" para inicializar tu efectivo disponible.",
        step6Title = "Empieza a registrar",
        step6Desc = "Vuelve al panel principal. Tu pantalla Solari ahora muestra tu efectivo disponible. Registra gastos a medida que gastas y observa c\u00f3mo se actualiza el n\u00famero en tiempo real.",
        habitsTitle = "Construyendo mejores h\u00e1bitos financieros",
        habitsBody = "SecureSync Presupuesto Diario es m\u00e1s que un rastreador \u2014 es una herramienta para crear " +
            "conciencia financiera duradera. As\u00ed puedes sacarle el m\u00e1ximo provecho:",
        tipKnowTitle = "Conoce tu n\u00famero",
        tipKnowBody = "Revisa la pantalla Solari al menos una vez al d\u00eda. El simple hecho de saber " +
            "cu\u00e1nto puedes gastar genera conciencia sobre tus compras. Los estudios muestran " +
            "que las personas que registran sus gastos gastan entre un 10\u201320% menos en promedio, " +
            "simplemente por la toma de conciencia.",
        tipRecordTitle = "Registra cada gasto",
        tipRecordBody = "Las compras peque\u00f1as son donde los presupuestos fallan en silencio. Un caf\u00e9 por aqu\u00ed, " +
            "un snack por all\u00e1 \u2014 se acumulan r\u00e1pido. Registrar cada gasto te mantiene honesto " +
            "y te ayuda a detectar patrones que de otro modo pasar\u00edas por alto. Usa las importaciones " +
            "bancarias para mayor eficiencia y registra manualmente las compras en efectivo.",
        tipPlanTitle = "Prep\u00e1rate para lo inesperado",
        tipPlanBody = "Usa las Metas de Ahorro para planificar cosas como llantas, reemplazo de electrodom\u00e9sticos, " +
            "regalos navide\u00f1os o vacaciones. Cuando ahorras un poco en cada per\u00edodo, " +
            "estos gastos no se convierten en emergencias. La clave de la tranquilidad financiera " +
            "es eliminar las sorpresas.",
        tipPaycheckTitle = "Evita la trampa del d\u00eda de pago",
        tipPaycheckBody = "Muchas personas gastan de m\u00e1s justo despu\u00e9s del d\u00eda de pago y andan cortos antes del siguiente. " +
            "El enfoque de presupuesto diario distribuye tu ingreso a lo largo de todos los d\u00edas, para que tengas " +
            "una cantidad constante y predecible para gastar sin importar cu\u00e1ndo llegue tu pago. " +
            "Se acabaron los ciclos de abundancia y escasez.",
        tipWatchTitle = "Observa crecer tu efectivo disponible",
        tipWatchBody = "Si gastas menos que tu monto de presupuesto de forma constante, tu efectivo disponible " +
            "aumentar\u00e1 gradualmente. Este excedente es tu colch\u00f3n para gastos inesperados " +
            "y una se\u00f1al de que tus h\u00e1bitos financieros est\u00e1n funcionando. No te sientas presionado " +
            "a gastarlo \u2014 d\u00e9jalo crecer.",
        keyFeaturesTitle = "Funciones principales de un vistazo",
        featureBullet1 = "Seguimiento del presupuesto en tiempo real con una elegante pantalla Solari de solapas",
        featureBullet2 = "C\u00e1lculo inteligente del presupuesto que considera ingresos irregulares y el momento de cada gasto",
        featureBullet3 = "Reconocimiento autom\u00e1tico de gastos recurrentes e ingresos desde importaciones bancarias",
        featureBullet4 = "Metas de Ahorro \u2014 ahorra autom\u00e1ticamente para compras grandes y objetivos financieros",
        featureBullet5 = "Amortizaci\u00f3n \u2014 distribuye compras grandes del pasado a lo largo del tiempo",
        featureBullet6 = "Divisi\u00f3n de transacciones en m\u00faltiples categor\u00edas con modos de gr\u00e1fico circular, calculadora o porcentaje",
        featureBullet7 = "Respaldo y restauraci\u00f3n cifrada de transacciones",
        featureBullet8 = "Importaci\u00f3n de extractos bancarios con categorizaci\u00f3n autom\u00e1tica",
        featureBullet9 = "Detecci\u00f3n de transacciones duplicadas",
        featureBullet10 = "Categor\u00edas totalmente personalizables con selecci\u00f3n de iconos",
        featureBullet11 = "Soporte para m\u00faltiples monedas y formatos de fecha",
        privacyTitle = "Privacidad y seguridad",
        privacyBody = "Tus datos financieros permanecen en tu dispositivo. SecureSync Presupuesto Diario no " +
            "se conecta a internet, no recopila an\u00e1lisis y no comparte tus datos " +
            "con nadie. Al exportar tus transacciones, puedes elegir formato cifrado " +
            "(ChaCha20-Poly1305 con derivaci\u00f3n de clave PBKDF2) para m\u00e1xima seguridad. " +
            "Tu dinero, tus datos, tu control."
    )

    override val settingsHelp = SettingsHelpStrings(
        title = "Ayuda de Ajustes",
        overviewTitle = "Descripci\u00f3n general",
        overviewBody = "La pantalla de Ajustes te permite personalizar c\u00f3mo muestra la informaci\u00f3n la app " +
            "y gestionar tus categor\u00edas de transacciones. Acc\u00e9dela tocando el icono de engranaje " +
            "en el panel principal.",
        headerTitle = "Barra superior",
        headerBody = "La barra superior ofrece navegaci\u00f3n y acceso a la ayuda:",
        backDesc = "Volver al panel principal.",
        helpDesc = "Abre esta p\u00e1gina de ayuda.",
        configureTitle = "Configurar tu presupuesto",
        configureBody = "El primer elemento en Ajustes es un bot\u00f3n que abre la Configuraci\u00f3n del presupuesto. " +
            "Aqu\u00ed es donde configuras tus fuentes de ingreso, eliges tu per\u00edodo de presupuesto " +
            "y calculas tu presupuesto diario seguro. Consulta la p\u00e1gina de ayuda de Configuraci\u00f3n " +
            "del presupuesto para m\u00e1s detalles.",
        currencyTitle = "Moneda",
        currencyBody = "Elige el s\u00edmbolo de moneda que se muestra en toda la app. El men\u00fa desplegable incluye " +
            "s\u00edmbolos comunes:",
        currencyDollar = "$ \u2014 D\u00f3lar estadounidense, canadiense, australiano, etc.",
        currencyEuro = "\u20ac \u2014 Euro",
        currencyPound = "\u00a3 \u2014 Libra esterlina",
        currencyYen = "\u00a5 \u2014 Yen japon\u00e9s / Yuan chino",
        currencyRupee = "\u20b9 \u2014 Rupia india",
        currencyWon = "\u20a9 \u2014 Won surcoreano",
        currencyMore = "Y m\u00e1s",
        currencyNote = "El s\u00edmbolo de moneda afecta la pantalla Solari, los montos de transacciones, la " +
            "configuraci\u00f3n del presupuesto y todas las dem\u00e1s visualizaciones monetarias. Los decimales se " +
            "ajustan autom\u00e1ticamente para monedas que tradicionalmente no los usan (ej. Yen).",
        decimalsTitle = "Mostrar decimales",
        decimalsBody = "Al activarlo, la pantalla Solari muestra centavos despu\u00e9s del punto decimal. " +
            "La cantidad de decimales depende de tu moneda (2 para la mayor\u00eda, " +
            "0 para monedas como el Yen japon\u00e9s). Desactivarlo redondea la pantalla a " +
            "n\u00fameros enteros para una apariencia m\u00e1s limpia.",
        dateFormatTitle = "Formato de fecha",
        dateFormatBody = "Elige c\u00f3mo se muestran las fechas en toda la app, incluyendo la lista de transacciones, " +
            "los selectores de fecha y los archivos exportados. Las opciones incluyen:",
        dateIso = "2026-02-17 \u2014 Formato ISO (predeterminado)",
        dateUs = "02/17/2026 \u2014 Formato estadounidense",
        dateEu = "17/02/2026 \u2014 Formato europeo",
        dateAbbrev = "Feb 17, 2026 \u2014 Mes abreviado",
        dateFull = "February 17, 2026 \u2014 Mes completo",
        dateMore = "Y otros formatos internacionales",
        dateNote = "El men\u00fa desplegable muestra una fecha de ejemplo en cada formato para que puedas " +
            "ver c\u00f3mo se ver\u00e1 antes de seleccionarlo.",
        weekStartTitle = "La semana empieza el",
        weekStartBody = "Elige si la semana comienza el domingo o el lunes. Esto afecta la agrupaci\u00f3n " +
            "semanal del gr\u00e1fico de gastos y los c\u00e1lculos del per\u00edodo semanal del presupuesto.",
        chartPaletteTitle = "Paleta de gr\u00e1ficos",
        chartPaletteBody = "Elige la paleta de colores para los gr\u00e1ficos circulares y de barras en toda la app. " +
            "Hay tres opciones:",
        paletteBright = "Vibrante \u2014 colores vivos y saturados para m\u00e1ximo contraste",
        palettePastel = "Pastel \u2014 tonos m\u00e1s suaves y atenuados para una apariencia m\u00e1s delicada",
        paletteSunset = "Atardecer \u2014 tonos c\u00e1lidos terrosos inspirados en una paleta de atardecer (predeterminada)",
        paletteNote = "Cada paleta se ajusta autom\u00e1ticamente para el modo claro y oscuro. La paleta " +
            "se aplica al gr\u00e1fico de gastos del panel principal, al editor de gr\u00e1fico circular de transacciones " +
            "y a todas las dem\u00e1s visualizaciones de gr\u00e1ficos.",
        matchingTitle = "Configuraci\u00f3n de coincidencias",
        matchingBody = "Estos ajustes controlan c\u00f3mo la app detecta transacciones duplicadas y las asocia " +
            "con gastos recurrentes, entradas de amortizaci\u00f3n y fuentes de ingreso del presupuesto:",
        matchDaysBullet = "D\u00edas de coincidencia (\u00b1N) \u2014 cu\u00e1ntos d\u00edas de diferencia pueden tener dos transacciones y a\u00fan considerarse coincidencia",
        matchPercentBullet = "Porcentaje de coincidencia (\u00b1%) \u2014 tolerancia porcentual para la coincidencia de montos",
        matchDollarBullet = "Monto de coincidencia (\u00b1\$) \u2014 tolerancia absoluta en dinero para la coincidencia de montos",
        matchCharsBullet = "Caracteres de coincidencia \u2014 longitud m\u00ednima de subcadena compartida para la coincidencia de nombres de comercio",
        matchingNote = "Los valores predeterminados funcionan bien para la mayor\u00eda. Aumenta las tolerancias si la " +
            "app no detecta coincidencias, o reduc\u00e9las si ves demasiados falsos positivos.",
        paidTitle = "Usuario de pago",
        paidBody = "Al activarlo, se desbloquean funciones premium:",
        paidSave = "Guardar transacciones \u2014 exportar a CSV o archivo cifrado",
        paidLoad = "Cargar transacciones \u2014 importar desde CSV bancario, CSV de la app o respaldo cifrado",
        paidNote = "Cuando el Usuario de pago no est\u00e1 activado, los iconos de Guardar y Cargar en la pantalla " +
            "de Transacciones aparecen atenuados y no funcionan.",
        categoriesTitle = "Categor\u00edas",
        categoriesBody = "Las categor\u00edas te permiten clasificar tus transacciones para un mejor an\u00e1lisis de gastos. " +
            "Cada categor\u00eda tiene un nombre y un icono.",
        defaultCategoriesTitle = "Categor\u00edas predeterminadas",
        defaultCategoriesBody = "Tres categor\u00edas est\u00e1n protegidas y no se pueden eliminar ni renombrar:",
        catOther = "Otros \u2014 la categor\u00eda predeterminada para transacciones sin clasificar",
        catRecurring = "Recurrente \u2014 se asigna autom\u00e1ticamente a transacciones que coinciden con gastos recurrentes",
        catAmortization = "Amortizaci\u00f3n \u2014 se asigna autom\u00e1ticamente a transacciones que coinciden con entradas de amortizaci\u00f3n",
        addCategoryTitle = "Agregar una categor\u00eda",
        addCategoryBody = "Toca \"Agregar categor\u00eda\" para crear una nueva. Escribe un nombre y elige un icono " +
            "de la cuadr\u00edcula de iconos. Los iconos se muestran en una cuadr\u00edcula visual por la que puedes desplazarte.",
        editCategoryTitle = "Editar una categor\u00eda",
        editCategoryBody = "Toca cualquier categor\u00eda no protegida para abrir el di\u00e1logo de edici\u00f3n. Puedes cambiar el " +
            "nombre y el icono. El bot\u00f3n de eliminar (icono rojo de papelera) aparece en la barra de t\u00edtulo del di\u00e1logo.",
        deleteCategoryTitle = "Eliminar una categor\u00eda",
        deleteCategoryBody = "Al eliminar una categor\u00eda que tiene transacciones existentes:",
        deleteBullet1 = "Si ninguna transacci\u00f3n usa la categor\u00eda, se elimina de inmediato",
        deleteBullet2 = "Si existen transacciones, aparece un di\u00e1logo de reasignaci\u00f3n",
        deleteBullet3 = "Debes elegir otra categor\u00eda a la cual mover las transacciones afectadas",
        deleteBullet4 = "Toca \"Mover y eliminar\" para reasignar todas las transacciones afectadas y eliminar la categor\u00eda",
        reassignmentTitle = "Reasignaci\u00f3n de categor\u00eda",
        reassignmentBody = "Al eliminar una categor\u00eda, todas las transacciones asignadas a ella " +
            "se mueven a la categor\u00eda de reemplazo que elijas. Esto incluye " +
            "transacciones con m\u00faltiples categor\u00edas donde solo la divisi\u00f3n espec\u00edfica " +
            "se ve afectada. La reasignaci\u00f3n es permanente.",
        tipsTitle = "Consejos",
        tip1 = "Configura las categor\u00edas antes de importar transacciones \u2014 la categorizaci\u00f3n autom\u00e1tica usa tu historial de transacciones existente para identificar comercios.",
        tip2 = "Crea categor\u00edas que reflejen tus h\u00e1bitos de gasto. Ejemplos comunes: Comida, Transporte, Entretenimiento, Salud, Vivienda, Servicios, Compras.",
        tip3 = "Usa el per\u00edodo \"Diario\" si quieres el control de gastos m\u00e1s detallado.",
        tip4 = "El bot\u00f3n de Configuraci\u00f3n del presupuesto es lo primero que debes configurar despu\u00e9s de instalar la app."
    )

    override val transactionsHelp = TransactionsHelpStrings(
        title = "Ayuda de Transacciones",
        overviewTitle = "Descripci\u00f3n general",
        overviewBody = "La pantalla de Transacciones es donde gestionas todos tus ingresos y gastos. " +
            "Puedes agregar, editar, eliminar, buscar, filtrar, importar y exportar transacciones.",
        headerTitle = "Barra superior",
        headerBody = "La barra superior contiene iconos de navegaci\u00f3n y acciones:",
        backDesc = "Volver a la pantalla principal.",
        saveDesc = "Guardar todas las transacciones en un archivo. Requiere Usuario de pago.",
        loadDesc = "Importar o cargar transacciones desde un archivo. Requiere Usuario de pago.",
        helpDesc = "Abre esta p\u00e1gina de ayuda.",
        saveLoadNote = "Los iconos de Guardar y Cargar aparecen atenuados si el Usuario de pago no est\u00e1 activado en Ajustes.",
        actionBarTitle = "Barra de acciones",
        actionBarBody = "Debajo de la barra superior, la barra de acciones da acceso r\u00e1pido a operaciones comunes:",
        filterDesc = "Filtro \u2014 alterna entre: Todas, Gastos, Ingresos.",
        addIncomeDesc = "Crear una nueva transacci\u00f3n de ingreso.",
        addExpenseDesc = "Crear una nueva transacci\u00f3n de gasto.",
        searchDesc = "Abrir el men\u00fa de b\u00fasqueda con tres opciones:",
        dateSearchBullet = "Buscar por fecha \u2014 selecciona una fecha de inicio y fin",
        textSearchBullet = "Buscar por texto \u2014 busca por nombre de comercio/fuente",
        amountSearchBullet = "Buscar por monto \u2014 busca por rango de monto",
        searchNote = "Mientras los resultados de b\u00fasqueda est\u00e1n activos, aparece un banner en la parte superior. Toca el banner para borrar la b\u00fasqueda.",
        listTitle = "Lista de transacciones",
        listBody = "Las transacciones se muestran en una lista desplazable, ordenadas por fecha (m\u00e1s recientes primero). " +
            "Cada fila muestra:",
        listIconBullet = "Icono de categor\u00eda (izquierda) \u2014 coloreado seg\u00fan la categor\u00eda",
        listDateBullet = "Fecha \u2014 con el formato seg\u00fan tu preferencia en Ajustes",
        listMerchantBullet = "Comercio/Fuente \u2014 el nombre del beneficiario o pagador",
        listAmountBullet = "Monto \u2014 rojo para gastos, verde para ingresos",
        iconColorsTitle = "Colores de los iconos de categor\u00eda",
        coloredLabel = "Con color",
        coloredDesc = " \u2014 la categor\u00eda fue establecida o confirmada por ti",
        defaultLabel = "Predeterminado",
        defaultDesc = " \u2014 asignada autom\u00e1ticamente durante la importaci\u00f3n (a\u00fan sin confirmar)",
        filterByIconNote = "Toca un icono de categor\u00eda para filtrar la lista solo a esa categor\u00eda. Aparecer\u00e1 un banner de filtro; t\u00f3calo para borrarlo.",
        multiCategoryTitle = "Transacciones con m\u00faltiples categor\u00edas",
        multiCategoryBody = "Un icono de lista indica que la transacci\u00f3n est\u00e1 dividida en m\u00faltiples categor\u00edas. " +
            "T\u00f3calo para expandir y ver el desglose por categor\u00eda.",
        tapEditTitle = "Tocar y editar",
        tapBullet = "Toca una transacci\u00f3n para abrir el di\u00e1logo de edici\u00f3n",
        longPressBullet = "Mant\u00e9n presionada una transacci\u00f3n para entrar en modo de selecci\u00f3n",
        selectionTitle = "Modo de selecci\u00f3n",
        selectionBody = "Mant\u00e9n presionada cualquier transacci\u00f3n para entrar en modo de selecci\u00f3n. Aparece una barra de herramientas con acciones masivas:",
        selectAllDesc = "Seleccionar todo \u2014 selecciona o deselecciona todas las transacciones visibles",
        changeCategoryDesc = "Asignar una categor\u00eda a todas las transacciones seleccionadas.",
        editMerchantDesc = "Reemplazar el nombre de comercio/fuente en todas las transacciones seleccionadas.",
        deleteDesc = "Eliminar todas las transacciones seleccionadas.",
        closeDesc = "Salir del modo de selecci\u00f3n sin cambios.",
        addEditTitle = "Di\u00e1logo de Agregar / Editar transacci\u00f3n",
        addEditBody = "Al agregar o editar una transacci\u00f3n, aparece un di\u00e1logo a pantalla completa con estos campos:",
        fieldDate = "Fecha",
        fieldDateDesc = "Toca el icono de calendario para elegir una fecha.",
        fieldMerchant = "Comercio / Fuente",
        fieldMerchantDesc = "Escribe el nombre del beneficiario (gastos) o la fuente de ingreso.",
        fieldCategory = "Categor\u00eda",
        fieldCategoryDesc = "Toca para abrir el selector de categor\u00edas. Puedes seleccionar una o varias.",
        fieldAmount = "Monto",
        fieldAmountDesc = "Ingresa el monto de la transacci\u00f3n.",
        singleCatTitle = "Una sola categor\u00eda",
        singleCatBody = "Con una categor\u00eda seleccionada, simplemente ingresa el monto total en el campo Monto.",
        multiCatTitle = "M\u00faltiples categor\u00edas",
        multiCatBody = "Al seleccionar dos o m\u00e1s categor\u00edas, se desbloquean tres modos de ingreso. " +
            "Primero ingresa el monto Total, luego elige un modo:",
        pieChartDesc = "Arrastra las secciones en un gr\u00e1fico circular interactivo para distribuir el total entre categor\u00edas.",
        calculatorDesc = "Ingresa un monto espec\u00edfico para cada categor\u00eda. El \u00faltimo campo vac\u00edo se completa autom\u00e1ticamente.",
        percentageDesc = "Ingresa un porcentaje para cada categor\u00eda. Los porcentajes se ajustan autom\u00e1ticamente para sumar 100%.",
        pieChartModeTitle = "Modo de gr\u00e1fico circular",
        pieChartModeBody = "El gr\u00e1fico circular interactivo te permite distribuir visualmente una transacci\u00f3n entre categor\u00edas arrastrando las l\u00edneas divisorias entre secciones.",
        pieChartDragNote = "Arrastra el l\u00edmite entre dos secciones para redistribuir. " +
            "Las etiquetas de categor\u00eda y los montos se actualizan en tiempo real debajo del gr\u00e1fico.",
        autoFillTitle = "Autocompletar",
        autoFillBody = "Tanto en el modo Calculadora como en Porcentaje, cuando todos los campos menos uno est\u00e1n llenos, " +
            "el campo restante se completa autom\u00e1ticamente con el saldo. " +
            "Por ejemplo, con un total de \$100 y dos categor\u00edas, al ingresar \$60 para Comida " +
            "se asignar\u00e1 autom\u00e1ticamente \$40 a Compras.",
        duplicateTitle = "Detecci\u00f3n de duplicados",
        duplicateBody = "Al guardar una nueva transacci\u00f3n o importar desde un archivo, la app verifica posibles duplicados. " +
            "Una transacci\u00f3n se marca si coincide con una existente en los tres criterios:",
        dupAmountBullet = "Monto con diferencia menor al 1%",
        dupDateBullet = "Fecha con diferencia menor a 7 d\u00edas",
        dupMerchantBullet = "Nombre del comercio comparte una subcadena en com\u00fan",
        dupDialogBody = "Cuando se detecta un duplicado, ver\u00e1s un di\u00e1logo con cuatro opciones:",
        dupIgnore = "Ignorar \u2014 conservar ambas transacciones",
        dupKeepNew = "Conservar nuevo \u2014 reemplazar la existente con la nueva",
        dupKeepExisting = "Conservar existente \u2014 descartar la nueva transacci\u00f3n",
        dupIgnoreAll = "Ignorar todos \u2014 conservar todos los duplicados restantes (solo al importar)",
        savingTitle = "Guardar transacciones",
        savingBody = "Toca el icono de Guardar en la barra superior para exportar todas las transacciones a un archivo. Dos formatos est\u00e1n disponibles:",
        csvFormatTitle = "Formato CSV",
        csvFormatBody = "Guarda tus transacciones como un archivo CSV de texto plano (syncbudget_transactions.csv). " +
            "Este archivo conserva todos los datos incluyendo categor\u00edas y puede cargarse de nuevo en la app. " +
            "Tambi\u00e9n se puede abrir en hojas de c\u00e1lculo como Excel o Google Sheets para su revisi\u00f3n.",
        encryptedFormatTitle = "Formato cifrado",
        encryptedFormatBody = "Guarda tus transacciones en un archivo cifrado (syncbudget_transactions.enc) " +
            "protegido con una contrase\u00f1a que t\u00fa eliges. Este es el formato recomendado para respaldos " +
            "y transferencia de datos entre dispositivos, ya que mantiene tu informaci\u00f3n financiera privada.",
        encryptionDetailsTitle = "Detalles del cifrado",
        encryptionDetailsBody = "Tu archivo est\u00e1 protegido con cifrado autenticado ChaCha20-Poly1305 \u2014 " +
            "la misma familia de cifrados utilizada por apps de mensajer\u00eda modernas y VPNs. " +
            "Tu contrase\u00f1a nunca se almacena; en su lugar, se transforma en una clave de cifrado " +
            "usando PBKDF2 con 100,000 iteraciones, lo que hace los ataques de fuerza bruta extremadamente lentos.",
        passwordImportanceTitle = "Por qu\u00e9 importa tu contrase\u00f1a",
        passwordImportanceBody = "El cifrado es tan fuerte como tu contrase\u00f1a. Una contrase\u00f1a corta o com\u00fan " +
            "puede adivinarse r\u00e1pidamente, incluso con cifrado fuerte. Esto es lo que una tarjeta " +
            "gr\u00e1fica moderna de alta gama (capaz de probar miles de millones de hashes simples por segundo) podr\u00eda lograr:",
        passwordTableHeader = "Contrase\u00f1a",
        passwordTableExample = "Ejemplo",
        passwordTableTime = "Tiempo para descifrar",
        pw8Lower = "8 caract., min\u00fasculas",
        pw8LowerEx = "password",
        pw8LowerTime = "minutos",
        pw8Mixed = "8 caract., mixtos",
        pw8MixedEx = "Pa\$sw0rd",
        pw8MixedTime = "horas",
        pw10Mixed = "10 caract., mixtos",
        pw10MixedEx = "K9#mP2x!qL",
        pw10MixedTime = "meses",
        pw12Mixed = "12 caract., mixtos",
        pw12MixedEx = "7hR!q2Lp#9Zk",
        pw12MixedTime = "milenios",
        pw16Mixed = "16+ caract., mixtos",
        pw16MixedEx = "cT8!nQ#2mK@5rW9j",
        pw16MixedTime = "billones de a\u00f1os",
        pw4Word = "Frase de 4 palabras",
        pw4WordEx = "maple cloud river fox",
        pw4WordTime = "billones de a\u00f1os",
        pbkdfNote = "Debido a que tu contrase\u00f1a pasa por 100,000 rondas de PBKDF2 antes de usarse como " +
            "clave de cifrado, cada intento se hace deliberadamente muy costoso. Una sola GPU de alta gama " +
            "solo puede intentar aproximadamente 100,000\u2013500,000 contrase\u00f1as por segundo contra este archivo \u2014 " +
            "millones de veces m\u00e1s lento que atacar un hash simple.",
        recommendedTitle = "Estrategia de contrase\u00f1a recomendada",
        recommendedBody = "Usa 12 o m\u00e1s caracteres combinando may\u00fasculas, min\u00fasculas, " +
            "n\u00fameros y s\u00edmbolos. Una frase de 4\u20135 palabras aleatorias (ej. \"correct horse battery staple\") " +
            "tambi\u00e9n es excelente. Con una contrase\u00f1a fuerte de este tipo, " +
            "incluso un adversario a nivel estatal con miles de GPUs " +
            "necesitar\u00eda billones de a\u00f1os para descifrar tu archivo.",
        passwordMinNote = "La longitud m\u00ednima requerida es de 8 caracteres, pero m\u00e1s largo siempre es mejor. " +
            "Debes ingresar tu contrase\u00f1a dos veces para confirmarla antes de guardar. " +
            "No hay recuperaci\u00f3n de contrase\u00f1a \u2014 si olvidas tu contrase\u00f1a, " +
            "el archivo no se podr\u00e1 abrir.",
        loadingTitle = "Cargar e importar",
        loadingBody = "Toca el icono de Cargar en la barra superior para importar transacciones desde un archivo. Se admiten tres formatos:",
        loadUsBank = "US Bank",
        loadUsBankDesc = "Importa transacciones desde un archivo CSV de US Bank. " +
            "Las transacciones se categorizan autom\u00e1ticamente seg\u00fan tu historial de comercios.",
        loadCsv = "Archivo CSV de SecureSync",
        loadCsvDesc = "Carga un archivo CSV guardado previamente desde esta app. " +
            "Todas las categor\u00edas y datos se conservan exactamente como estaban.",
        loadEncrypted = "Archivo cifrado de SecureSync",
        loadEncryptedDesc = "Carga un archivo cifrado guardado previamente. " +
            "Debes ingresar la contrase\u00f1a usada al guardar el archivo.",
        loadPasswordNote = "Para archivos cifrados, el campo de contrase\u00f1a aparece autom\u00e1ticamente al seleccionar el " +
            "formato cifrado. El bot\u00f3n \"Seleccionar archivo\" se desactiva hasta que ingreses al menos 8 caracteres.",
        loadDuplicateNote = "Despu\u00e9s de cargar, cada transacci\u00f3n importada se verifica contra tus " +
            "transacciones existentes. Si se encuentran duplicados, se te pedir\u00e1 que los resuelvas " +
            "uno por uno (consulta Detecci\u00f3n de duplicados arriba).",
        autoCatTitle = "Categorizaci\u00f3n autom\u00e1tica (importaciones bancarias)",
        autoCatBody = "Al importar desde un CSV bancario, la app revisa tus transacciones de los " +
            "\u00faltimos 6 meses para encontrar comercios coincidentes. Si encuentra una coincidencia, se asigna " +
            "autom\u00e1ticamente la categor\u00eda m\u00e1s usada. Las transacciones sin coincidencia se asignan a \"Otros\". " +
            "Las transacciones categorizadas autom\u00e1ticamente muestran un icono en color predeterminado hasta que " +
            "confirmes o cambies la categor\u00eda manualmente.",
        tipsTitle = "Consejos",
        tip1 = "Usa respaldos CSV para copias de seguridad compatibles con hojas de c\u00e1lculo que puedas revisar en una computadora.",
        tip2 = "Usa respaldos cifrados para copias de seguridad protegidas y transferencia de datos entre dispositivos.",
        tip3 = "El mismo archivo se puede cargar tantas veces como sea necesario \u2014 la detecci\u00f3n de duplicados evita entradas dobles accidentales.",
        tip4 = "Usa el filtro de categor\u00eda (toca cualquier icono de categor\u00eda) combinado con el modo de selecci\u00f3n para ediciones masivas eficientes.",
        tip5 = "Despu\u00e9s de una importaci\u00f3n bancaria, revisa las transacciones categorizadas autom\u00e1ticamente y usa Cambiar categor\u00eda en lote para corregir asignaciones incorrectas."
    )

    override val budgetConfigHelp = BudgetConfigHelpStrings(
        title = "Ayuda de Configuraci\u00f3n del presupuesto",
        overviewTitle = "Descripci\u00f3n general",
        overviewBody = "La Configuraci\u00f3n del presupuesto es la pantalla principal donde defines tus fuentes " +
            "de ingreso, eliges tu per\u00edodo de presupuesto y calculas tu monto seguro de gasto. " +
            "El motor de presupuesto usa esta informaci\u00f3n para determinar cu\u00e1nto puedes gastar " +
            "con seguridad en cada per\u00edodo.",
        periodTitle = "Per\u00edodo del presupuesto",
        periodBody = "El per\u00edodo del presupuesto determina con qu\u00e9 frecuencia se renueva tu efectivo disponible. " +
            "Elige el per\u00edodo que mejor se ajuste a c\u00f3mo piensas en tus gastos:",
        periodDaily = "Diario",
        periodDailyDesc = "Tu presupuesto se calcula por d\u00eda. Ideal para presupuestos ajustados o personas que quieren m\u00e1xima conciencia diaria.",
        periodWeekly = "Semanal",
        periodWeeklyDesc = "Tu presupuesto se calcula por semana. Bueno para personas que planifican gastos semanalmente.",
        periodMonthly = "Mensual",
        periodMonthlyDesc = "Tu presupuesto se calcula por mes. Adecuado para personas con pago mensual que prefieren planificar por mes.",
        periodNote = "El per\u00edodo tambi\u00e9n afecta c\u00f3mo se calculan las deducciones de Metas de Ahorro y Amortizaci\u00f3n, " +
            "ya que deducen un monto fijo por per\u00edodo.",
        resetSettingsTitle = "Ajustes de hora de refresco",
        resetSettingsBody = "Toca el bot\u00f3n \"Hora de refresco\" junto al selector de Per\u00edodo del presupuesto para configurar " +
            "cu\u00e1ndo cambia tu per\u00edodo:",
        resetHourTitle = "Hora de reinicio",
        resetHourBody = "La hora del d\u00eda en que comienza un nuevo per\u00edodo y tu monto de presupuesto se " +
            "agrega al efectivo disponible. El valor predeterminado es 12 AM (medianoche). Configura esto " +
            "a la hora en que normalmente empiezas tu d\u00eda \u2014 por ejemplo, 6 AM si madrugas.",
        dayOfWeekTitle = "D\u00eda de la semana (Semanal)",
        dayOfWeekBody = "Para presupuestos semanales, elige qu\u00e9 d\u00eda comienza la nueva semana. Por ejemplo, " +
            "si configuras Lunes, tu presupuesto se reinicia cada lunes a la hora de reinicio configurada.",
        dayOfMonthTitle = "D\u00eda del mes (Mensual)",
        dayOfMonthBody = "Para presupuestos mensuales, elige qu\u00e9 d\u00eda del mes comienza el nuevo per\u00edodo " +
            "(1\u201328). Si te pagan el 1, configura 1. Si te pagan el 15, " +
            "configura 15. El m\u00e1ximo es 28 para asegurar que la fecha exista en todos los meses.",
        safeBudgetTitle = "Presupuesto seguro",
        safeBudgetBody = "El Presupuesto Seguro es el m\u00e1ximo calculado que puedes gastar por per\u00edodo " +
            "cubriendo todos tus gastos recurrentes. Se muestra en la parte superior " +
            "de la pantalla de configuraci\u00f3n.",
        howCalculatedTitle = "C\u00f3mo se calcula",
        howCalculatedBody = "El motor proyecta tus ingresos y gastos un a\u00f1o hacia adelante:",
        calcStep1 = "Suma de ingresos",
        calcStep1Desc = "Todas las ocurrencias de fuentes de ingreso se generan para los pr\u00f3ximos 12 meses seg\u00fan sus calendarios de repetici\u00f3n. Se calcula el ingreso anual total.",
        calcStep2 = "Monto base",
        calcStep2Desc = "El presupuesto base es el ingreso anual dividido por el n\u00famero de per\u00edodos en un a\u00f1o (ej. 365 para diario, 52 para semanal, 12 para mensual).",
        calcStep3 = "Protecci\u00f3n de timing",
        calcStep3Desc = "El motor simula cada per\u00edodo y verifica que los gastos acumulados nunca superen el presupuesto. Si las facturas se acumulan en ciertos per\u00edodos, el monto se aumenta para cubrir el peor caso.",
        importantTitle = "Importante",
        importantBody = "El Presupuesto Seguro solo considera fuentes de ingreso y gastos recurrentes " +
            "que tengan configuraci\u00f3n completa de calendario de repetici\u00f3n. Si una fuente no tiene " +
            "ajustes de repetici\u00f3n, se excluir\u00e1 del c\u00e1lculo. Aseg\u00farate de configurar " +
            "los calendarios de repetici\u00f3n de todas tus fuentes de ingreso.",
        autoRecalcTitle = "Rec\u00e1lculo autom\u00e1tico",
        autoRecalcBody = "El Presupuesto Seguro se actualiza autom\u00e1ticamente cuando cambias fuentes de ingreso, " +
            "gastos recurrentes o el per\u00edodo del presupuesto. No se necesita rec\u00e1lculo manual.",
        startResetTitle = "Iniciar/Restablecer presupuesto",
        startResetBody = "Toca \"Iniciar/Restablecer\" cuando configures por primera vez o necesites empezar de cero. Esto:",
        resetBullet1 = "Recalcula el presupuesto seguro",
        resetBullet2 = "Restablece la fecha de inicio del presupuesto a hoy",
        resetBullet3 = "Fija el efectivo disponible en el monto de un per\u00edodo",
        resetBullet4 = "NO elimina tus transacciones",
        whenToResetTitle = "Cu\u00e1ndo restablecer",
        whenToResetBody = "Usa Iniciar/Restablecer cuando tu efectivo disponible se haya desviado de la realidad " +
            "(ej. despu\u00e9s de un cambio importante como un nuevo empleo o mudanza), o cuando " +
            "hayas hecho cambios significativos en tus fuentes de ingreso o gastos " +
            "y quieras empezar de cero. Restablecer perder\u00e1 tu excedente o d\u00e9ficit acumulado, " +
            "as\u00ed que \u00fasalo deliberadamente.",
        manualTitle = "Presupuesto manual",
        manualBody = "Activa \"Presupuesto manual\" para establecer tu propio monto por per\u00edodo " +
            "en lugar de usar el valor calculado. Al activarlo:",
        manualBullet1 = "Aparece un campo de texto donde ingresas el monto deseado por per\u00edodo",
        manualBullet2 = "El c\u00e1lculo del presupuesto seguro se ignora",
        manualBullet3 = "Las deducciones de Amortizaci\u00f3n y Metas de Ahorro se desactivan",
        warningTitle = "Advertencia",
        warningBody = "El modo manual desactiva la red de seguridad del calculador de presupuesto. " +
            "Las deducciones de Amortizaci\u00f3n y Metas de Ahorro no se restar\u00e1n " +
            "de tu presupuesto. Aparecer\u00e1n banners de advertencia rojos en las pantallas de Amortizaci\u00f3n " +
            "y Metas de Ahorro cuando el modo manual est\u00e9 activo.",
        incomeSourcesTitle = "Fuentes de ingreso",
        incomeSourcesBody = "Las fuentes de ingreso representan tus ingresos confiables y recurrentes \u2014 el dinero con " +
            "el que puedes contar para presupuestar. Agrega todos los flujos de ingreso constantes: " +
            "salario, honorarios fijos de freelance, pensi\u00f3n, ingresos extra regulares, etc.",
        addingIncomeTitle = "Agregar una fuente de ingreso",
        addingIncomeBody = "Toca \"Agregar fuente de ingreso\" y completa:",
        incomeNameBullet = "Nombre de la fuente \u2014 un nombre descriptivo (ej. \"N\u00f3mina trabajo principal\"). Tambi\u00e9n se usa para la detecci\u00f3n de ingresos presupuestados al agregar transacciones.",
        incomeAmountBullet = "Monto \u2014 la cantidad que recibes por ocurrencia",
        variablePayTitle = "Pago variable",
        variablePayBody = "Si tu pago var\u00eda (por ejemplo, un cheque grande y uno peque\u00f1o " +
            "cada mes), crea entradas separadas para cada monto. La calculadora de presupuesto " +
            "manejar\u00e1 correctamente los diferentes montos.",
        managingTitle = "Gestionar fuentes de ingreso",
        manageTapBullet = "Toca una fuente para editar su nombre y monto",
        manageRepeatDesc = "Configurar el calendario de ingresos (cu\u00e1ndo te pagan)",
        manageDeleteDesc = "Eliminar permanentemente la fuente de ingreso",
        repeatTitle = "Calendarios de repetici\u00f3n",
        repeatBody = "Cada fuente de ingreso necesita un calendario de repetici\u00f3n para que la calculadora de presupuesto " +
            "sepa cu\u00e1ndo esperar los pagos. Los mismos tipos de repetici\u00f3n est\u00e1n disponibles para fuentes " +
            "de ingreso y gastos recurrentes:",
        everyXDaysTitle = "D\u00eda",
        everyXDaysBody = "El ingreso llega cada N d\u00edas (1\u2013365). Requiere una Fecha de Inicio \u2014 la fecha " +
            "de cualquier ocurrencia pasada o futura. El motor calcula todas las fechas futuras a partir " +
            "de este punto de referencia.",
        everyXWeeksTitle = "Semana",
        everyXWeeksBody = "El ingreso llega cada N semanas (1\u201352). Requiere una Fecha de Inicio. El d\u00eda de " +
            "la semana lo determina tu fecha de inicio (ej. si tu fecha de inicio cae en viernes, " +
            "el ingreso se repite cada N viernes).",
        biWeeklyTitle = "",
        biWeeklyBody = "",
        everyXMonthsTitle = "Mes",
        everyXMonthsBody = "El ingreso llega en un d\u00eda espec\u00edfico del mes, cada N meses (1\u201312). " +
            "Elige una fecha de inicio para establecer el d\u00eda y la fase. Los d\u00edas 29\u201331 se permiten " +
            "cuando el intervalo es 12 (anual); de lo contrario se limita a 1\u201328.",
        biMonthlyTitle = "Dos veces al mes (Bimensual)",
        biMonthlyBody = "El ingreso llega en dos d\u00edas espec\u00edficos cada mes. Ingresa tanto el Primer D\u00eda como el " +
            "Segundo D\u00eda (1\u201328 cada uno). Por ejemplo, si te pagan el 1 y el 15, " +
            "ingresa 1 y 15. Esto da exactamente 24 ocurrencias al a\u00f1o.",
        annualTitle = "Anual",
        annualBody = "El ingreso llega una vez al a\u00f1o en una fecha espec\u00edfica. Elige una fecha de inicio \u2014 " +
            "cualquier d\u00eda de cualquier mes es v\u00e1lido, incluyendo del 29 al 31. El motor maneja a\u00f1os " +
            "bisiestos y meses cortos autom\u00e1ticamente.",
        dayLimitTitle = "L\u00edmite de d\u00eda: 1\u201328",
        dayLimitBody = "Los valores de d\u00eda del mes est\u00e1n limitados a 28 para la mayor\u00eda de tipos de repetici\u00f3n " +
            "para asegurar que la fecha exista en todos los meses, incluyendo febrero. Los tipos Anual " +
            "e intervalos de 12 meses permiten d\u00edas 29\u201331 ya que apuntan a un mes espec\u00edfico.",
        budgetIncomeTitle = "Detecci\u00f3n de ingresos presupuestados",
        budgetIncomeBody = "Cuando agregas una transacci\u00f3n de ingreso en la pantalla de Transacciones, la app " +
            "verifica si coincide con alguna de tus fuentes de ingreso configuradas (por nombre " +
            "y monto). Si encuentra coincidencia, se te pregunta si es:",
        budgetIncomeBullet = "Ingreso presupuestado \u2014 ya contabilizado en tu presupuesto (NO aumenta el efectivo disponible)",
        extraIncomeBullet = "Ingreso extra \u2014 ingreso inesperado o adicional (S\u00cd aumenta el efectivo disponible)",
        budgetIncomeNote = "Esto evita que tu n\u00f3mina se cuente dos veces \u2014 una en el c\u00e1lculo del presupuesto " +
            "y otra como entrada manual de ingreso.",
        tipsTitle = "Consejos",
        tip1 = "Configura todas las fuentes de ingreso y gastos recurrentes antes de tocar Iniciar/Restablecer para obtener el mejor resultado.",
        tip2 = "El presupuesto seguro se recalcula autom\u00e1ticamente cuando cambias ingresos o gastos.",
        tip3 = "Usa Iniciar/Restablecer con moderaci\u00f3n \u2014 elimina tu excedente/d\u00e9ficit acumulado.",
        tip4 = "Para ingresos variables, crea entradas separadas para cada monto de pago para mejorar la precisi\u00f3n.",
        tip5 = "Usa nombres descriptivos como \"N\u00f3mina Empresa ABC\" \u2014 el nombre se usa para la detecci\u00f3n autom\u00e1tica de ingresos presupuestados.",
        tip6 = "Solo incluye ingresos confiables y recurrentes. No agregues ingresos puntuales \u2014 reg\u00edstralos como ingreso extra en Transacciones."
    )

    override val futureExpendituresHelp = FutureExpendituresHelpStrings(
        title = "Ayuda de Metas de Ahorro",
        whatTitle = "\u00bfQu\u00e9 son las Metas de Ahorro?",
        whatBody = "Las Metas de Ahorro te permiten planificar y ahorrar para gastos futuros u objetivos " +
            "financieros sin arruinar tu presupuesto. En vez de que un gasto grande golpee " +
            "tu efectivo disponible de una sola vez, la app reduce autom\u00e1ticamente tu presupuesto " +
            "un poco en cada per\u00edodo para ir ahorrando gradualmente.",
        exampleTitle = "Ejemplo",
        exampleBody = "Quieres crear un fondo de emergencia de \$3,000. Crea una meta de contribuci\u00f3n fija " +
            "de \$5/d\u00eda. Con un presupuesto diario, apenas notas la deducci\u00f3n, " +
            "pero despu\u00e9s de 20 meses tu fondo de emergencia est\u00e1 completo. O fija " +
            "una fecha objetivo de 6 meses para ahorrar \$600 para llantas nuevas \u2014 la app " +
            "deduce aproximadamente \$3.29/d\u00eda autom\u00e1ticamente. Sin sorpresas, sin estr\u00e9s.",
        twoTypesTitle = "Dos tipos de metas",
        twoTypesBody = "Las Metas de Ahorro admiten dos enfoques diferentes para ahorrar:",
        targetDateTitle = "Fecha objetivo",
        targetDateBody = "Establece una fecha para cuando necesites el dinero. La app calcula autom\u00e1ticamente " +
            "cu\u00e1nto deducir en cada per\u00edodo seg\u00fan el monto restante y el tiempo " +
            "restante. A medida que te acercas a la fecha, la deducci\u00f3n se ajusta din\u00e1micamente.",
        fixedContribTitle = "Contribuci\u00f3n fija",
        fixedContribBody = "Establece un monto fijo a contribuir en cada per\u00edodo. No hay fecha objetivo \u2014 " +
            "la app simplemente deduce el monto elegido cada per\u00edodo hasta alcanzar la meta. " +
            "Ideal para ahorros sin fecha l\u00edmite, como un fondo de emergencia.",
        headerTitle = "Barra superior",
        headerBody = "La barra superior ofrece navegaci\u00f3n y acciones masivas:",
        backDesc = "Volver al panel principal.",
        pauseAllDesc = "Pausar todas las metas activas a la vez. Cambia a Reanudar cuando todas est\u00e1n pausadas.",
        helpDesc = "Abre esta p\u00e1gina de ayuda.",
        pauseAllNote = "El bot\u00f3n Pausar todas solo aparece cuando tienes al menos una meta.",
        addingTitle = "Agregar una meta de ahorro",
        addingBody = "Toca \"Agregar meta de ahorro\" y completa:",
        addStep1 = "Nombre",
        addStep1Desc = "Para qu\u00e9 est\u00e1s ahorrando (ej. \"Llantas nuevas\", \"Vacaciones en la playa\", \"Fondo de emergencia\").",
        addStep2 = "Monto objetivo",
        addStep2Desc = "El costo total que necesitas ahorrar.",
        addStep3 = "Monto ya ahorrado",
        addStep3Desc = "Opcional. Si ya tienes algo ahorrado para esta meta, ingr\u00e9salo aqu\u00ed para prellenar la barra de progreso.",
        addStep4 = "Tipo de meta",
        addStep4Desc = "Elige \"Fecha objetivo\" para establecer un plazo, o \"Contribuci\u00f3n fija\" para un monto regular por per\u00edodo.",
        addStep5 = "Fecha objetivo / Contribuci\u00f3n",
        addStep5Desc = "Seg\u00fan el tipo de meta, selecciona una fecha objetivo o ingresa una contribuci\u00f3n por per\u00edodo.",
        deductionsTitle = "C\u00f3mo funcionan las deducciones",
        deductionsBody = "Para cada meta activa (no pausada), la app calcula una deducci\u00f3n por per\u00edodo:",
        targetDateDeductionTitle = "Metas con fecha objetivo",
        targetDateDeductionFormula = "Deducci\u00f3n = (Monto Objetivo \u2212 Ahorrado Hasta Ahora) \u00f7 Per\u00edodos Restantes hasta la Fecha Objetivo",
        fixedDeductionTitle = "Metas con contribuci\u00f3n fija",
        fixedDeductionBody = "La deducci\u00f3n es igual a la contribuci\u00f3n por per\u00edodo que estableciste al crear la meta. " +
            "Se mantiene constante hasta alcanzar la meta.",
        deductionNote = "Estas deducciones se restan de tu Presupuesto Seguro para obtener " +
            "tu Monto de Presupuesto real. El \"Ahorrado Hasta Ahora\" aumenta autom\u00e1ticamente en cada " +
            "per\u00edodo seg\u00fan el monto de la deducci\u00f3n.",
        progressTitle = "Seguimiento del progreso",
        progressBody = "Cada meta en la lista muestra:",
        progressName = "Nombre \u2014 para qu\u00e9 est\u00e1s ahorrando",
        progressTarget = "Monto objetivo (y fecha objetivo para metas basadas en fecha)",
        progressDeduction = "Deducci\u00f3n del presupuesto o contribuci\u00f3n por per\u00edodo",
        progressBar = "Barra de progreso \u2014 indicador visual de qu\u00e9 tan cerca est\u00e1s de la meta",
        progressSaved = "Monto ahorrado \u2014 texto verde mostrando el ahorro acumulado vs. objetivo",
        progressGoalReached = "Etiqueta \"\u00a1Meta alcanzada!\" cuando se completa",
        actionsTitle = "Acciones",
        pauseDesc = "Detener temporalmente las deducciones de esta meta. El presupuesto vuelve a la normalidad mientras est\u00e1 pausada.",
        resumeDesc = "Reanudar las deducciones. El monto por per\u00edodo se recalcula seg\u00fan el tiempo y ahorro restantes.",
        deleteDesc = "Eliminar permanentemente la meta de ahorro.",
        editNote = "Toca cualquier meta para editar su nombre, monto objetivo, tipo de meta u otros ajustes.",
        statusTitle = "Indicadores de estado",
        activeTitle = "Activa",
        activeBody = "Estado normal \u2014 la deducci\u00f3n se aplica en cada per\u00edodo y el ahorro se acumula.",
        pausedTitle = "Pausada",
        pausedBody = "Las deducciones est\u00e1n temporalmente detenidas. La meta aparece atenuada. Pausar es \u00fatil " +
            "cuando tienes un mes ajustado y necesitas el presupuesto completo temporalmente. El progreso del ahorro " +
            "se conserva. Al reanudar, la deducci\u00f3n se recalcula con el tiempo restante reducido " +
            "(para metas con fecha objetivo), por lo que ser\u00e1 un poco mayor.",
        goalReachedTitle = "Meta alcanzada",
        goalReachedBody = "Muestra \"\u00a1Meta alcanzada!\" en verde cuando el Ahorrado Hasta Ahora alcanza o supera el objetivo. " +
            "No se hacen m\u00e1s deducciones. Puedes eliminar la meta o conservarla como registro.",
        manualOverrideTitle = "Presupuesto manual",
        manualOverrideBody = "Si el Presupuesto manual est\u00e1 activado en Configuraci\u00f3n del presupuesto, aparece un banner " +
            "de advertencia rojo en la parte superior de esta pantalla. Con el modo manual activo, " +
            "las deducciones de Metas de Ahorro NO se restan de tu presupuesto \u2014 debes contabilizar " +
            "estos gastos por tu cuenta.",
        tipsTitle = "Consejos",
        tip1 = "Crea metas con fecha objetivo lo antes posible \u2014 cuanto m\u00e1s tiempo tengas, menor ser\u00e1 la deducci\u00f3n de cada per\u00edodo.",
        tip2 = "Usa metas de contribuci\u00f3n fija para ahorros sin fecha l\u00edmite, como fondos de emergencia o ahorro general.",
        tip3 = "Usa Pausar estrat\u00e9gicamente en meses ajustados, pero reanuda pronto para evitar un aumento brusco en las deducciones al acercarse la fecha objetivo.",
        tip4 = "Ingresa un monto ya ahorrado al crear una meta si ya tienes dinero apartado.",
        tip5 = "Usos comunes: mantenimiento del auto, procedimientos m\u00e9dicos, regalos navide\u00f1os, vacaciones, electr\u00f3nica, muebles, suscripciones anuales, fondo de emergencia.",
        tip6 = "Combin\u00e1lo con Amortizaci\u00f3n: usa Metas de Ahorro para ahorrar antes de una compra, y Amortizaci\u00f3n para distribuir costos despu\u00e9s de una compra inesperada."
    )

    override val amortizationHelp = AmortizationHelpStrings(
        title = "Ayuda de Amortizaci\u00f3n",
        whatTitle = "\u00bfQu\u00e9 es la Amortizaci\u00f3n?",
        whatBody = "La Amortizaci\u00f3n te permite distribuir el impacto de un gasto grande a lo largo " +
            "de m\u00faltiples per\u00edodos. En lugar de que el costo total destruya tu presupuesto " +
            "en un solo d\u00eda/semana/mes, el costo se divide de forma equitativa y se deduce " +
            "de tu presupuesto gradualmente.",
        exampleTitle = "Ejemplo",
        exampleBody = "Tu auto necesita inesperadamente una reparaci\u00f3n de \$900. Con un presupuesto diario de \$40/d\u00eda, " +
            "eso acabar\u00eda con m\u00e1s de 22 d\u00edas de presupuesto. En su lugar, creas una " +
            "entrada de amortizaci\u00f3n por \$900 en 90 d\u00edas. Tu presupuesto se reduce solo " +
            "\$10/d\u00eda durante 90 d\u00edas, manteni\u00e9ndote a flote mientras el costo se absorbe gradualmente.",
        vsGoalsTitle = "Amortizaci\u00f3n vs. Metas de Ahorro",
        vsGoalsBody = "Estas dos funciones son complementarias:",
        goalsBullet = "Metas de Ahorro \u2014 ahorra ANTES de un gasto planeado (proactivo)",
        amortBullet = "Amortizaci\u00f3n \u2014 distribuye DESPU\u00c9S de un gasto imprevisto o pasado (reactivo)",
        headerTitle = "Barra superior",
        backDesc = "Volver al panel principal.",
        helpDesc = "Abre esta p\u00e1gina de ayuda.",
        addingTitle = "Agregar una entrada de amortizaci\u00f3n",
        addingBody = "Toca \"Agregar entrada de amortizaci\u00f3n\" y completa:",
        addStep1 = "Nombre de la fuente",
        addStep1Desc = "Un nombre descriptivo para el gasto (ej. \"Reparaci\u00f3n del auto\", \"Visita a urgencias\", \"Laptop nueva\"). Importante: este nombre se compara con los nombres de comercio en transacciones bancarias para el reconocimiento autom\u00e1tico.",
        addStep2 = "Monto total",
        addStep2Desc = "El costo completo del gasto.",
        addStep3 = "Per\u00edodos",
        addStep3Desc = "En cu\u00e1ntos per\u00edodos distribuir el costo. La etiqueta muestra tu tipo de per\u00edodo actual (d\u00edas, semanas o meses).",
        addStep4 = "Fecha de inicio",
        addStep4Desc = "Cu\u00e1ndo comienza la amortizaci\u00f3n (generalmente la fecha del gasto).",
        deductionsTitle = "C\u00f3mo funcionan las deducciones",
        deductionsBody = "La deducci\u00f3n por per\u00edodo es sencilla:",
        deductionFormula = "Deducci\u00f3n = Monto Total \u00f7 N\u00famero de Per\u00edodos",
        deductionNote = "Esta deducci\u00f3n se resta de tu Presupuesto Seguro (junto con las deducciones de " +
            "Metas de Ahorro) para obtener tu Monto de Presupuesto real. La deducci\u00f3n se mantiene " +
            "constante durante todo el per\u00edodo de amortizaci\u00f3n y luego se detiene autom\u00e1ticamente.",
        entryListTitle = "Lista de entradas",
        entryListBody = "Cada entrada de amortizaci\u00f3n muestra:",
        entrySource = "Nombre de la fuente",
        entryTotal = "Monto total y deducci\u00f3n por per\u00edodo",
        entryProgress = "Progreso \u2014 \"X de Y [per\u00edodos] completados\" o \"Completada\" en verde",
        actionsTitle = "Acciones",
        editNote = "Toca una entrada para editar sus detalles (nombre de fuente, monto, per\u00edodos, fecha de inicio).",
        deleteDesc = "Eliminar permanentemente la entrada de amortizaci\u00f3n.",
        matchingTitle = "Coincidencia autom\u00e1tica de transacciones",
        matchingBody = "Cuando agregas una transacci\u00f3n (manualmente o mediante importaci\u00f3n bancaria), la app verifica " +
            "si el nombre del comercio y el monto coinciden con alguna de tus entradas de amortizaci\u00f3n. " +
            "Si encuentra coincidencia, se te muestra un di\u00e1logo de confirmaci\u00f3n:",
        yesAmortBullet = "\"S\u00ed, es amortizaci\u00f3n\" \u2014 la transacci\u00f3n se marca como amortizada y NO reduce tu efectivo disponible (ya que el costo ya se est\u00e1 deduciendo de tu presupuesto gradualmente)",
        noRegularBullet = "\"No, es regular\" \u2014 la transacci\u00f3n se trata como un gasto normal",
        sourceMatchingTitle = "Coincidencia por nombre de fuente",
        sourceMatchingBody = "Usa nombres descriptivos para tus fuentes de amortizaci\u00f3n. El algoritmo de coincidencia " +
            "busca subcadenas en com\u00fan entre el nombre de la fuente y " +
            "el nombre del comercio de la transacci\u00f3n. Por ejemplo, una fuente llamada \"Servicio Toyota\" " +
            "coincidir\u00eda con una transacci\u00f3n bancaria de \"SERVICIO TOYOTA CENTER\".",
        manualOverrideTitle = "Presupuesto manual",
        manualOverrideBody = "Si el Presupuesto manual est\u00e1 activado en Configuraci\u00f3n del presupuesto, aparece un banner " +
            "de advertencia rojo en la parte superior de esta pantalla. Con el modo manual activo, " +
            "las deducciones de amortizaci\u00f3n NO se restan de tu presupuesto \u2014 debes " +
            "contabilizar estos costos por tu cuenta.",
        tipsTitle = "Consejos",
        tip1 = "Elige un n\u00famero de per\u00edodos que resulte en una deducci\u00f3n c\u00f3moda. Si \$10/d\u00eda es demasiado, distr\u00edbuyelo en m\u00e1s d\u00edas.",
        tip2 = "Usa la amortizaci\u00f3n para cualquier gasto que de otro modo devastar\u00eda tu presupuesto: facturas m\u00e9dicas, reparaciones del auto, reemplazo de electrodom\u00e9sticos, viajes de emergencia.",
        tip3 = "Las entradas completadas (todos los per\u00edodos transcurridos) se pueden eliminar para mantener la lista limpia.",
        tip4 = "Recuerda tambi\u00e9n registrar la transacci\u00f3n real \u2014 la amortizaci\u00f3n solo ajusta la tasa de tu presupuesto, no registra el gasto en s\u00ed.",
        tip5 = "Si sab\u00edas del gasto de antemano, las Metas de Ahorro habr\u00edan sido la mejor herramienta. Usa la Amortizaci\u00f3n para sorpresas."
    )

    override val recurringExpensesHelp = RecurringExpensesHelpStrings(
        title = "Ayuda de Gastos Recurrentes",
        whatTitle = "\u00bfQu\u00e9 son los Gastos Recurrentes?",
        whatBody = "Los gastos recurrentes son facturas y pagos que se repiten en un calendario regular: " +
            "alquiler, hipoteca, servicios p\u00fablicos, seguros, suscripciones, pagos de pr\u00e9stamos y obligaciones " +
            "similares. Al registrarlos aqu\u00ed, la calculadora de presupuesto los tiene en cuenta " +
            "autom\u00e1ticamente, de modo que tu presupuesto diario/semanal/mensual refleje solo lo que " +
            "realmente tienes disponible para gastos discrecionales.",
        whyTitle = "Por qu\u00e9 es importante",
        whyBody = "Sin los gastos recurrentes en la calculadora, tu monto de presupuesto " +
            "se basar\u00eda solo en los ingresos. Ver\u00edas un presupuesto diario alto, gastar\u00edas libremente " +
            "y luego andar\u00edas corto cuando llegue el alquiler. Registrar los gastos asegura que el presupuesto " +
            "reserve lo suficiente para las facturas, incluso en meses donde varias se acumulan.",
        headerTitle = "Barra superior",
        backDesc = "Volver al panel principal.",
        helpDesc = "Abre esta p\u00e1gina de ayuda.",
        addingTitle = "Agregar un gasto recurrente",
        addingBody = "Toca \"Agregar gasto recurrente\" y completa:",
        addStep1 = "Descripci\u00f3n",
        addStep1Desc = "Un nombre descriptivo para el gasto (ej. \"Alquiler\", \"Netflix\", \"Seguro del auto\"). Importante: este nombre se compara con los nombres de comercio en transacciones bancarias para el reconocimiento autom\u00e1tico.",
        addStep2 = "Monto",
        addStep2Desc = "El monto por ocurrencia.",
        repeatTitle = "Ajustes de repetici\u00f3n",
        repeatBody = "Cada gasto recurrente necesita un calendario de repetici\u00f3n para que la calculadora de presupuesto " +
            "sepa cu\u00e1ndo esperar el cargo. Toca el icono de sincronizaci\u00f3n en cualquier gasto para configurar:",
        everyXDaysTitle = "D\u00eda",
        everyXDaysBody = "El gasto ocurre cada N d\u00edas (1\u2013365). Requiere una Fecha de Inicio. " +
            "\u00datil para gastos con intervalos irregulares como recarga de medicamentos.",
        everyXWeeksTitle = "Semana",
        everyXWeeksBody = "El gasto ocurre cada N semanas (1\u201352). Requiere una Fecha de Inicio. " +
            "El d\u00eda de la semana lo determina la fecha de inicio.",
        biWeeklyTitle = "",
        biWeeklyBody = "",
        everyXMonthsTitle = "Mes",
        everyXMonthsBody = "Ocurre en un d\u00eda espec\u00edfico del mes, cada N meses (1\u201312). " +
            "Elige una fecha de inicio para establecer el d\u00eda y la fase. La mayor\u00eda de facturas usan este tipo: " +
            "alquiler el 1, tel\u00e9fono el 15, etc. Los d\u00edas 29\u201331 se permiten cuando el intervalo es 12.",
        biMonthlyTitle = "Dos veces al mes (Bimensual)",
        biMonthlyBody = "Ocurre en dos d\u00edas espec\u00edficos cada mes. Ingresa ambos d\u00edas (1\u201328 cada uno). " +
            "\u00datil para gastos que se cobran dos veces al mes.",
        annualTitle = "Anual",
        annualBody = "Ocurre una vez al a\u00f1o en una fecha espec\u00edfica. Elige una fecha de inicio \u2014 cualquier " +
            "d\u00eda es v\u00e1lido, incluyendo del 29 al 31. \u00datil para primas de seguros anuales, membres\u00edas o suscripciones.",
        dayLimitNote = "Los valores de d\u00eda del mes est\u00e1n limitados a 28 para la mayor\u00eda de tipos de repetici\u00f3n " +
            "para asegurar que la fecha exista en todos los meses. Los tipos Anual e intervalos de 12 meses permiten d\u00edas 29\u201331.",
        expenseListTitle = "Lista de gastos",
        expenseListBody = "Cada gasto recurrente en la lista muestra:",
        expenseSource = "Descripci\u00f3n",
        expenseAmount = "Monto por ocurrencia",
        actionsTitle = "Acciones",
        editNote = "Toca un gasto para editar su nombre y monto.",
        repeatSettingsDesc = "Configurar o cambiar el calendario de repetici\u00f3n.",
        deleteDesc = "Eliminar permanentemente el gasto recurrente.",
        budgetEffectTitle = "C\u00f3mo afectan los gastos recurrentes a tu presupuesto",
        budgetEffectBody = "Los gastos recurrentes cumplen dos funciones en el sistema de presupuesto:",
        timingSafetyTitle = "1. C\u00e1lculo del presupuesto (Protecci\u00f3n de timing)",
        timingSafetyBody = "La calculadora proyecta todos los gastos recurrentes un a\u00f1o hacia adelante y " +
            "simula cada per\u00edodo. Se asegura de que tu monto de presupuesto sea suficiente " +
            "para cubrir las facturas incluso en meses donde se acumulan varios gastos. " +
            "Sin esto, podr\u00edas tener suficiente dinero en total pero no en una " +
            "semana o mes particular.",
        autoMatchTitle = "2. Coincidencia autom\u00e1tica de transacciones",
        autoMatchBody = "Cuando agregas una transacci\u00f3n (manualmente o mediante importaci\u00f3n bancaria), la app verifica " +
            "si el nombre del comercio y el monto coinciden con alg\u00fan gasto recurrente. Si encuentra " +
            "coincidencia, se te muestra un di\u00e1logo de confirmaci\u00f3n:",
        yesRecurringBullet = "\"S\u00ed, es recurrente\" \u2014 la transacci\u00f3n se marca como gasto recurrente y NO reduce tu efectivo disponible (ya que est\u00e1 contabilizado en el presupuesto)",
        noRegularBullet = "\"No, es regular\" \u2014 la transacci\u00f3n se trata como un gasto normal",
        whyMatchingTitle = "Por qu\u00e9 importa la coincidencia",
        whyMatchingBody = "Tu monto de presupuesto ya tiene los gastos recurrentes \"incorporados\" \u2014 la " +
            "calculadora reserv\u00f3 dinero para ellos. Si un gasto recurrente tambi\u00e9n se restara " +
            "del efectivo disponible, se contar\u00eda doble. El sistema de coincidencia evita " +
            "esto al reconocer las transacciones recurrentes e impedir que afecten " +
            "tu dinero para gastar.",
        sourceMatchTitle = "Coincidencia por nombre de fuente",
        sourceMatchBody = "El sistema de reconocimiento autom\u00e1tico compara los nombres de comercio de las transacciones " +
            "con los nombres de tus gastos recurrentes. Para mejores resultados:",
        matchBullet1 = "Usa nombres descriptivos que coincidan con c\u00f3mo aparece el gasto en los extractos bancarios",
        matchBullet2 = "Por ejemplo, \"State Farm\" coincidir\u00e1 con \"STATE FARM INSURANCE\" de tu banco",
        matchBullet3 = "La coincidencia busca subcadenas en com\u00fan, as\u00ed que coincidencias parciales funcionan",
        matchBullet4 = "El monto tambi\u00e9n debe estar dentro del 1% para que se active la coincidencia",
        tipsTitle = "Consejos",
        tip1 = "Agrega TODOS los gastos recurrentes, incluso los peque\u00f1os como suscripciones de streaming. Se acumulan y la calculadora necesita el panorama completo.",
        tip2 = "Si el monto de un gasto var\u00eda ligeramente (como una factura de servicios), usa el monto promedio.",
        tip3 = "Tu presupuesto se recalcula autom\u00e1ticamente cuando agregas o eliminas gastos recurrentes.",
        tip4 = "Gastos comunes para agregar: alquiler/hipoteca, servicios (electricidad, gas, agua), seguros (auto, salud, hogar), suscripciones (streaming, gimnasio, software), pagos de pr\u00e9stamos, tel\u00e9fono.",
        tip5 = "Si un gasto es realmente puntual, no lo agregues aqu\u00ed. Usa la Amortizaci\u00f3n para distribuirlo en el tiempo.",
        tip6 = "Revisa tus extractos bancarios para asegurarte de no haber olvidado ning\u00fan cargo recurrente."
    )

    override val familySyncHelp = FamilySyncHelpStrings(
        title = "Ayuda de Sincronizaci\u00f3n Familiar",
        whatTitle = "\u00bfQu\u00e9 es la Sincronizaci\u00f3n Familiar?",
        whatBody = "La Sincronizaci\u00f3n Familiar te permite compartir un presupuesto familiar en hasta 5 dispositivos. " +
            "Todas las transacciones, fuentes de ingreso, gastos recurrentes, metas de ahorro y configuraciones " +
            "se mantienen sincronizados autom\u00e1ticamente usando cifrado de extremo a extremo. " +
            "Nadie \u2014 ni siquiera el servidor \u2014 puede leer tus datos financieros.",
        adminRoleTitle = "El Rol de Administrador",
        adminRoleBody = "La persona que crea el grupo familiar se convierte en administrador. El admin puede " +
            "cambiar la configuraci\u00f3n compartida del presupuesto (moneda, per\u00edodo, horario de reinicio), " +
            "generar c\u00f3digos de emparejamiento, configurar la zona horaria familiar, " +
            "habilitar la atribuci\u00f3n de transacciones y disolver el grupo. Los miembros no admin " +
            "pueden ver la configuraci\u00f3n pero no pueden cambiarla.",
        gettingStartedTitle = "Primeros Pasos",
        gettingStartedBody = "Para configurar la sincronizaci\u00f3n: Abre Configuraci\u00f3n, toca Sincronizaci\u00f3n Familiar y " +
            "toca \"Crear Grupo Familiar\". Se crear\u00e1 un grupo contigo como admin. " +
            "Luego toca \"Generar C\u00f3digo\" y comparte el c\u00f3digo de 6 caracteres con " +
            "tus familiares. Ellos ingresan el c\u00f3digo en su dispositivo para unirse. " +
            "Los c\u00f3digos expiran despu\u00e9s de 10 minutos por seguridad.",
        joiningTitle = "Unirse a un Grupo",
        joiningBody = "Para unirte a un grupo existente, toca \"Unirse a Grupo Familiar\" e ingresa el " +
            "c\u00f3digo de 6 caracteres. Importante: unirte reemplazar\u00e1 tus datos locales " +
            "con los datos compartidos del grupo. Tus transacciones, metas y configuraciones " +
            "actuales ser\u00e1n sobrescritas. Aseg\u00farate de hacer una copia de seguridad primero.",
        syncStatusTitle = "Estado de Sincronizaci\u00f3n",
        syncStatusBody = "El punto de color en el panel principal y la tarjeta de estado en esta pantalla " +
            "muestran el estado actual: Verde (Sincronizado) significa que todo est\u00e1 actualizado. " +
            "Amarillo (Sincronizando) significa que hay una sincronizaci\u00f3n en progreso. Naranja (Desactualizado) " +
            "significa que ha pasado tiempo desde la \u00faltima sincronizaci\u00f3n. Rojo (Error) " +
            "significa que el \u00faltimo intento fall\u00f3.",
        staleWarningsTitle = "Advertencias de Desactualizaci\u00f3n",
        staleWarningsBody = "Si tu dispositivo no se ha sincronizado por un per\u00edodo extendido, " +
            "aparecer\u00e1n advertencias escalonadas en el panel: a los 60 d\u00edas un recordatorio suave, " +
            "a los 75 d\u00edas una cuenta regresiva, a los 85 d\u00edas una advertencia urgente, y a los 90 d\u00edas " +
            "la sincronizaci\u00f3n se bloquea y se requiere una actualizaci\u00f3n completa. " +
            "Sincroniza regularmente para evitar esto.",
        attributionTitle = "Atribuci\u00f3n de Transacciones",
        attributionBody = "Cuando el admin lo habilita, cada transacci\u00f3n muestra qu\u00e9 dispositivo la cre\u00f3. " +
            "Tus propias transacciones muestran \"T\u00fa\" y las de otros dispositivos muestran " +
            "el nombre del dispositivo. Esto ayuda a las familias a ver qui\u00e9n registr\u00f3 cada gasto.",
        adminClaimsTitle = "Reclamos de Admin",
        adminClaimsBody = "Si el dispositivo admin se pierde o no est\u00e1 disponible, cualquier miembro puede " +
            "reclamar el rol de admin. Toca \"Reclamar Rol de Admin\" en la pantalla de Sincronizaci\u00f3n Familiar. " +
            "Los dem\u00e1s miembros tienen 24 horas para objetar. Si nadie objeta, el reclamo se aprueba. " +
            "Si alguien objeta, el reclamo se rechaza.",
        leavingTitle = "Salir o Disolver",
        leavingBody = "Los miembros no admin pueden salir del grupo en cualquier momento. Tus datos locales se conservan " +
            "pero dejan de sincronizarse. El admin puede disolver el grupo completamente, " +
            "desconectando a todos los miembros y eliminando los datos de sincronizaci\u00f3n del servidor. " +
            "Los datos locales en cada dispositivo se conservan.",
        privacyTitle = "Privacidad y Seguridad",
        privacyBody = "Todos los datos de sincronizaci\u00f3n est\u00e1n cifrados de extremo a extremo con una clave de 256 bits " +
            "generada al crear el grupo. La clave se comparte solo a trav\u00e9s del mecanismo de c\u00f3digo de emparejamiento. " +
            "El servidor almacena solo datos cifrados \u2014 no puede leer tus transacciones, montos, " +
            "nombres de comercios ni ning\u00fan dato financiero. Tu dinero, tus datos, tu control."
    )
}
