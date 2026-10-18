import AppIntents
import ComposeApp

/// #206: Siri, the Shortcuts app, Spotlight and the Action button. Each intent hands its words to
/// Kotlin, which reads them (`AgentIntents.kt`). The two that add something only open a filled sheet;
/// the sheet's Save is what stores it. Siri has no Burmese, so Burmese titles reach Shortcuts only.

struct AddExpenseIntent: AppIntent {
    static var title: LocalizedStringResource = "Add expense"
    static var description = IntentDescription("Opens the Add sheet with the amount filled in. Nothing is saved until you tap Save.")
    static var openAppWhenRun = true

    @Parameter(title: "Amount", requestValueDialog: IntentDialog("How much?"))
    var amount: Double

    @Parameter(title: "Category")
    var category: String?

    @Parameter(title: "Note")
    var note: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Add \(\.$amount) for \(\.$category)") {
            \.$note
        }
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        AgentIntentsKt.receiveAddExpense(amount: baht(amount), category: category, note: note)
        return .result()
    }
}

struct AddSharedExpenseIntent: AppIntent {
    static var title: LocalizedStringResource = "Add shared expense"
    static var description = IntentDescription("Opens the group's Add expense with the amount filled in. Nothing is saved until you tap Save.")
    static var openAppWhenRun = true

    @Parameter(title: "Group", requestValueDialog: IntentDialog("Which group?"))
    var group: String

    @Parameter(title: "Amount", requestValueDialog: IntentDialog("How much?"))
    var amount: Double

    @Parameter(title: "Description")
    var details: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Add \(\.$amount) to \(\.$group)") {
            \.$details
        }
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        AgentIntentsKt.receiveAddSharedExpense(group: group, amount: baht(amount), description: details)
        return .result()
    }
}

/// Answered where it was asked: the app does not open, and nothing changes.
struct SpendSummaryIntent: AppIntent {
    static var title: LocalizedStringResource = "How much did I spend"
    static var description = IntentDescription("Says what you spent today, this week or this month. Nothing changes.")
    /// It answers without opening the app, so a locked phone must not read a total aloud.
    static var authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication

    @Parameter(title: "Period", default: .today)
    var period: SpendPeriod

    static var parameterSummary: some ParameterSummary {
        Summary("How much did I spend \(\.$period)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<String> & ProvidesDialog {
        let amount = await withCheckedContinuation { done in
            AgentIntentsKt.spendSummary(period: period.rawValue) { done.resume(returning: $0) }
        }
        let sentence: String
        switch period {
        case .today: sentence = String(localized: "You spent \(amount) today.")
        case .week: sentence = String(localized: "You spent \(amount) this week.")
        case .month: sentence = String(localized: "You spent \(amount) this month.")
        }
        return .result(value: sentence, dialog: IntentDialog(stringLiteral: sentence))
    }
}

/// Kotlin's `SpendWindow`, by name.
enum SpendPeriod: String, AppEnum {
    case today
    case week
    case month

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Period"
    static var caseDisplayRepresentations: [SpendPeriod: DisplayRepresentation] = [
        .today: "today",
        .week: "this week",
        .month: "this month",
    ]
}

/// English and Thai phrases; the Thai ones live in th.lproj/AppShortcuts.strings.
struct PaiSenShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AddExpenseIntent(),
            phrases: [
                "Add expense in \(.applicationName)",
                "Add an expense in \(.applicationName)",
                "New expense in \(.applicationName)",
            ]
        )
        AppShortcut(
            intent: SpendSummaryIntent(),
            phrases: [
                "How much did I spend in \(.applicationName)",
                "How much did I spend \(\.$period) in \(.applicationName)",
            ]
        )
        AppShortcut(
            intent: AddSharedExpenseIntent(),
            phrases: [
                "Add shared expense in \(.applicationName)",
            ]
        )
    }
}

/// Siri hands over a Double. Kotlin reads money as digits, so it crosses as text with two decimals.
private func baht(_ amount: Double) -> String {
    String(format: "%.2f", amount)
}
