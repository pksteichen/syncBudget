package com.syncbudget.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class QuickStartStep {
    WELCOME,
    BUDGET_PERIOD,
    INCOME,
    EXPENSES,
    FIRST_TRANSACTION,
    DONE
}

data class StepContent(
    val title: String,
    val body: String,
    val targetScreen: String,  // which screen the user should be on
    val buttonLabel: String = "Next"
)

/**
 * Overlay guide that draws over the app with semi-transparent backdrop.
 * Positions an instruction card at the bottom, leaving the top of the
 * screen accessible for the user to interact with the underlying UI.
 */
@Composable
fun QuickStartOverlay(
    step: QuickStartStep,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onNavigate: (String) -> Unit,
    isEnglish: Boolean = true,
    onLanguageChange: (String) -> Unit = {},
    isPaidUser: Boolean = false
) {
    val steps = if (isEnglish) englishSteps() else spanishSteps()
    val content = steps[step] ?: return

    // Navigate to the correct screen for this step
    when (step) {
        QuickStartStep.WELCOME -> { /* stay on current screen */ }
        QuickStartStep.BUDGET_PERIOD -> onNavigate("budget_config")
        QuickStartStep.INCOME -> onNavigate("budget_config")  // income is on budget config page
        QuickStartStep.EXPENSES -> onNavigate("recurring_expenses")
        QuickStartStep.FIRST_TRANSACTION -> onNavigate("main")
        QuickStartStep.DONE -> { /* stay on current screen */ }
    }

    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
        Box(modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
        ) {
            // Semi-transparent backdrop — clickable to dismiss for WELCOME/DONE,
            // pass-through for other steps so user can interact with the app
            if (step == QuickStartStep.WELCOME || step == QuickStartStep.DONE) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* consume clicks on backdrop */ }
                )
            }

            // Position card based on step: top for steps that reference
            // bottom-of-page content, center for steps referencing top+bottom,
            // bottom for everything else
            val cardAlignment = when (step) {
                QuickStartStep.INCOME -> Alignment.TopCenter
                QuickStartStep.FIRST_TRANSACTION, QuickStartStep.DONE -> Alignment.Center
                else -> Alignment.BottomCenter
            }
            // Extra top padding to clear ad banner for non-paid users
            val adBannerPad = if (!isPaidUser && cardAlignment == Alignment.TopCenter) 50.dp else 0.dp
            // Offset center cards down 30dp
            val centerOffset = if (cardAlignment == Alignment.Center) 100.dp else 0.dp
            Surface(
                modifier = Modifier
                    .align(cardAlignment)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp,
                        top = 16.dp + adBannerPad + centerOffset),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        if (step == QuickStartStep.DONE) 28.dp else 20.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Step indicator dots
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        QuickStartStep.entries.forEach { s ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (s == step) 10.dp else 7.dp)
                                    .background(
                                        if (s == step) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = content.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = content.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    // Language selector on Welcome step
                    if (step == QuickStartStep.WELCOME) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { onLanguageChange("en") },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isEnglish) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.width(100.dp)
                            ) {
                                Text(
                                    "English",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    textAlign = TextAlign.Center,
                                    color = if (isEnglish) Color.White
                                        else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isEnglish) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                onClick = { onLanguageChange("es") },
                                shape = RoundedCornerShape(8.dp),
                                color = if (!isEnglish) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.width(100.dp)
                            ) {
                                Text(
                                    "Espa\u00f1ol",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    textAlign = TextAlign.Center,
                                    color = if (!isEnglish) Color.White
                                        else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (!isEnglish) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onSkip) {
                            Text(
                                if (isEnglish) "Skip" else "Saltar",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        Button(
                            onClick = onNext,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(content.buttonLabel)
                        }
                    }
                }
            }
        }
    }
}

private fun englishSteps(): Map<QuickStartStep, StepContent> = mapOf(
    QuickStartStep.WELCOME to StepContent(
        title = "Welcome to BudgeTrak!",
        body = "This guide will walk you through setting up your budget in a few simple steps.\n\n" +
            "BudgeTrak calculates how much you can spend each day by tracking your income, " +
            "recurring expenses, and daily spending.",
        targetScreen = "main",
        buttonLabel = "Let's go!"
    ),
    QuickStartStep.BUDGET_PERIOD to StepContent(
        title = "Step 1: Budget Period",
        body = "Choose how often your budget resets.\n\n" +
            "Most users choose Daily — your budget refreshes each morning.\n\n" +
            "Set your refresh time (e.g. 5:00 AM) so the budget resets while you sleep.\n\n" +
            "Use the controls above to configure, then tap Next.",
        targetScreen = "budget_config",
        buttonLabel = "Next"
    ),
    QuickStartStep.INCOME to StepContent(
        title = "Step 2: Income Sources",
        body = "Scroll down to the Income Sources section on this page.\n\n" +
            "Add your regular income — paychecks, side jobs, etc. " +
            "Tap the + button to add each income source with its amount and frequency.\n\n" +
            "BudgeTrak uses this to calculate your daily spending allowance.\n\n" +
            "Tap Next when you've added your income.",
        targetScreen = "budget_config",
        buttonLabel = "Next"
    ),
    QuickStartStep.EXPENSES to StepContent(
        title = "Step 3: Recurring Expenses",
        body = "Add your regular bills — rent, utilities, subscriptions, etc.\n\n" +
            "These are automatically deducted from your daily budget so you always know " +
            "what's safe to spend. Tap + above to add each expense.\n\n" +
            "Tap Next when you've added your recurring expenses.",
        targetScreen = "recurring_expenses",
        buttonLabel = "Next"
    ),
    QuickStartStep.FIRST_TRANSACTION to StepContent(
        title = "Step 4: Your First Transaction",
        body = "Your daily budget is now calculated and shown on the Solari display!\n\n" +
            "When you spend money, tap the \u2212 button at the bottom to record it. " +
            "Your available cash updates instantly.\n\n" +
            "Try adding a transaction now, or tap Finish to complete setup.",
        targetScreen = "main",
        buttonLabel = "Finish"
    ),
    QuickStartStep.DONE to StepContent(
        title = "You're all set!",
        body = "Your budget is ready to go. The Solari display shows your available cash " +
            "and updates automatically each period.\n\n" +
            "Explore the app to discover more features like savings goals, " +
            "amortization tracking, and SYNC across your devices.\n\n" +
            "Tap the ? icon on any page for detailed help and tips.",
        targetScreen = "main",
        buttonLabel = "Done"
    )
)

private fun spanishSteps(): Map<QuickStartStep, StepContent> = mapOf(
    QuickStartStep.WELCOME to StepContent(
        title = "\u00a1Bienvenido a BudgeTrak!",
        body = "Esta gu\u00eda te ayudar\u00e1 a configurar tu presupuesto en unos simples pasos.\n\n" +
            "BudgeTrak calcula cu\u00e1nto puedes gastar cada d\u00eda rastreando tus ingresos, " +
            "gastos recurrentes y gastos diarios.",
        targetScreen = "main",
        buttonLabel = "\u00a1Vamos!"
    ),
    QuickStartStep.BUDGET_PERIOD to StepContent(
        title = "Paso 1: Per\u00edodo del Presupuesto",
        body = "Elige con qu\u00e9 frecuencia se reinicia tu presupuesto.\n\n" +
            "La mayor\u00eda elige Diario \u2014 tu presupuesto se renueva cada ma\u00f1ana.\n\n" +
            "Configura tu hora de reinicio (ej. 5:00 AM) para que se reinicie mientras duermes.\n\n" +
            "Usa los controles de arriba para configurar, luego toca Siguiente.",
        targetScreen = "budget_config",
        buttonLabel = "Siguiente"
    ),
    QuickStartStep.INCOME to StepContent(
        title = "Paso 2: Fuentes de Ingreso",
        body = "Despl\u00e1zate hacia abajo a la secci\u00f3n de Fuentes de Ingreso en esta p\u00e1gina.\n\n" +
            "Agrega tus ingresos regulares \u2014 salarios, trabajos extras, etc. " +
            "Toca el bot\u00f3n + para agregar cada fuente de ingreso.\n\n" +
            "BudgeTrak usa esto para calcular tu asignaci\u00f3n diaria.\n\n" +
            "Toca Siguiente cuando hayas agregado tus ingresos.",
        targetScreen = "budget_config",
        buttonLabel = "Siguiente"
    ),
    QuickStartStep.EXPENSES to StepContent(
        title = "Paso 3: Gastos Recurrentes",
        body = "Agrega tus facturas regulares \u2014 alquiler, servicios, suscripciones, etc.\n\n" +
            "Estos se deducen autom\u00e1ticamente de tu presupuesto diario. " +
            "Toca + arriba para agregar cada gasto.\n\n" +
            "Toca Siguiente cuando hayas agregado tus gastos recurrentes.",
        targetScreen = "recurring_expenses",
        buttonLabel = "Siguiente"
    ),
    QuickStartStep.FIRST_TRANSACTION to StepContent(
        title = "Paso 4: Tu Primera Transacci\u00f3n",
        body = "\u00a1Tu presupuesto diario ahora est\u00e1 calculado y se muestra en el Solari!\n\n" +
            "Cuando gastes dinero, toca el bot\u00f3n \u2212 en la parte inferior para registrarlo. " +
            "Tu efectivo disponible se actualiza instant\u00e1neamente.\n\n" +
            "Intenta agregar una transacci\u00f3n ahora, o toca Finalizar.",
        targetScreen = "main",
        buttonLabel = "Finalizar"
    ),
    QuickStartStep.DONE to StepContent(
        title = "\u00a1Listo!",
        body = "Tu presupuesto est\u00e1 listo. El Solari muestra tu efectivo disponible " +
            "y se actualiza autom\u00e1ticamente cada per\u00edodo.\n\n" +
            "Explora la app para descubrir m\u00e1s funciones como metas de ahorro, " +
            "amortizaci\u00f3n y sincronizaci\u00f3n familiar.\n\n" +
            "Toca el icono ? en cualquier p\u00e1gina para ayuda detallada y consejos.",
        targetScreen = "main",
        buttonLabel = "Hecho"
    )
)
