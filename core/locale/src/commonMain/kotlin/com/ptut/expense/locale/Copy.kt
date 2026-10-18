package com.ptut.expense.locale

/**
 * Every word the app shows, one class per area. Each field defaults to English, so a string not yet
 * translated shows in English rather than as a gap. [BurmeseCopy] takes the wording of the
 * PaiSen Sky Screens MY design wherever it has one; `docs/l10n/` lists the rest for a native reader.
 */
data class Copy(
    val common: CommonCopy = CommonCopy(),
    val shell: ShellCopy = ShellCopy(),
    val calendar: CalendarCopy = CalendarCopy(),
    val language: LanguageCopy = LanguageCopy(),
    val home: HomeCopy = HomeCopy(),
    val add: AddCopy = AddCopy(),
    val receipt: ReceiptCopy = ReceiptCopy(),
    val slips: SlipsCopy = SlipsCopy(),
    val onboarding: OnboardingCopy = OnboardingCopy(),
    val folders: FoldersCopy = FoldersCopy(),
    val reports: ReportsCopy = ReportsCopy(),
    val history: HistoryCopy = HistoryCopy(),
    val record: RecordCopy = RecordCopy(),
    val phrases: PhrasesCopy = PhrasesCopy(),
    val categories: CategoriesCopy = CategoriesCopy(),
    val settings: SettingsCopy = SettingsCopy(),
    val shared: SharedCopy = SharedCopy(),
)

val EnglishCopy = Copy()

val BurmeseCopy = Copy(
    common = BurmeseCommon,
    shell = BurmeseShell,
    calendar = BurmeseCalendar,
    language = BurmeseLanguage,
    home = BurmeseHome,
    add = BurmeseAdd,
    receipt = BurmeseReceipt,
    slips = BurmeseSlips,
    onboarding = BurmeseOnboarding,
    folders = BurmeseFolders,
    reports = BurmeseReports,
    history = BurmeseHistory,
    record = BurmeseRecord,
    phrases = BurmesePhrases,
    categories = BurmeseCategories,
    settings = BurmeseSettings,
    shared = BurmeseShared,
)
