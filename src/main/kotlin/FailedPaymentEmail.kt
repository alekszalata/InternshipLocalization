import kotlinx.html.*
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

enum class Language {
    EN, RU, DE
}

interface LanguageLocale {
    val greeting: String
    val unfortunately: String
    val personalCustomer: String
    val organizationCustomer: String
    val toEnsure: String
    val hRefSentence: String
    val doubleCheck: String
    val till: String

    val creditCardReasons: ArrayList<String>
    val paypalCardReasons: ArrayList<String>
}

class FailedPaymentEmail(private val data: FailedPaymentData, language: Language) {

    private val currentLocale = chooseLocale(language, data)

    fun buildContent(body: HTML) = with(body) {
        body {
            greeting(currentLocale)

            problemDescription(currentLocale, data)

            if (data.cardProvider == CardProvider.PAY_PAL)
                paypalFailedPaymentReasons(currentLocale)
            else
                creditCardFailedPaymentReasons(currentLocale)

            subRenew(currentLocale)

            checkAndTry(currentLocale)
        }
    }
}

private fun chooseLocale(language: Language, data: FailedPaymentData): LanguageLocale {
    return when (language) {
        Language.EN -> EnLocale(data)
        Language.RU -> RuLocale(data)
        Language.DE -> DeLocale(data)
    }
}

private fun FlowContent.greeting(locale: LanguageLocale) {
    return p { +locale.greeting }
}

private fun FlowContent.problemDescription(locale: LanguageLocale, data: FailedPaymentData) {
    return p {
        +locale.unfortunately
        if (data.customerType == CustomerType.PERSONAL) {
            +locale.personalCustomer
        } else {
            +locale.organizationCustomer
            br()
            data.items.forEach { +"- ${it.quantity} x ${it.description}";br() }
        }
    }
}

private fun FlowContent.creditCardFailedPaymentReasons(locale: LanguageLocale) {
    return p {
        for (i in 0 until locale.creditCardReasons.size) {
            +locale.creditCardReasons[i]; br()
        }
    }
}

private fun FlowContent.paypalFailedPaymentReasons(locale: LanguageLocale) {
    return p {
        for (i in 0 until locale.paypalCardReasons.size - 1) {
            +locale.paypalCardReasons[i]; br()
        }
        +locale.paypalCardReasons.last()
    }
}

private fun FlowContent.subRenew(locale: LanguageLocale) {
    return p {
        +locale.toEnsure
        a(href = "https://foo.bar/ex") { +locale.hRefSentence }
        +locale.till
    }
}

private fun FlowContent.checkAndTry(locale: LanguageLocale) {
    return p { +locale.doubleCheck }
}

private fun String.simplyPluralize(amount: Int, language: Language): String {
    return when (amount) {
        1 -> this
        else -> {
            return when (language) {
                Language.EN, Language.DE -> "${this}s"
                Language.RU -> {
                    return when (this) {
                        "вашу" -> "ваши"
                        "вашей" -> "вашим"
                        "подписке" -> "подпискам"
                        else -> "${this.substring(0, this.length - 1)}и"
                    }
                }
            }
        }
    }
}


class DeLocale(data: FailedPaymentData) : LanguageLocale {
    override val greeting: String = "Vielen Dank für Ihren Aufenthalt bei JetBrains."

    override val unfortunately: String =
        "Leider konnten wir keine Mittel abschreiben${data.cardDetails ?: "deine Karte"} für " +
                "Ihre".simplyPluralize(data.items.sumBy { it.quantity }, Language.DE)

    override val personalCustomer: String =
        " ${data.subscriptionPack.billingPeriod.name.toLowerCase()} abonnement " +
                "für ${data.items.joinToString(", ") { it.productName }}."

    override val organizationCustomer: String =
        " abonnement".simplyPluralize(data.items.sumBy { it.quantity }, Language.DE) +
                " als Teil eines Abonnement-Pakets ${data.subscriptionPack.subPackRef?.let { "#$it" }.orEmpty()} " +
                "für die nächsten " +
                (when (data.subscriptionPack.billingPeriod) {
                    BillingPeriod.MONTHLY -> "monate"
                    BillingPeriod.ANNUAL -> "оahr"
                    else -> "zeitraum"
                } + ": ")


    override val toEnsure: String =
        ("Um einen ununterbrochenen Zugriff auf zu gewährleisten Ihre " +
                "abonnement".simplyPluralize(data.subscriptionPack.totalLicenses, Language.DE) +
                ", bitte folgen Sie dem Link und aktualisiere " +
                "deine ${"abonnement".simplyPluralize(data.subscriptionPack.totalLicenses, Language.DE)}")

    override val hRefSentence: String = "manuell"


    override val till: String =
        " Vor ${DateTimeFormatter.ofPattern("MMM dd, YYYY", Locale.GERMANY).format(data.paymentDeadline)}"

    override val doubleCheck: String =
        "Sie können Ihre Zahlungskarte überprüfen und es erneut versuchen. Verwenden Sie eine andere Karte " +
                "oder wählen Sie eine andere Zahlungsmethode."

    override val creditCardReasons: ArrayList<String> = arrayListOf(
        "Häufige Ursachen für fehlgeschlagene Kreditkartenzahlungen:",
        "- Die Karte ist abgelaufen oder das Ablaufdatum wurde falsch eingegeben.",
        "- Unzureichendes Guthaben oder Zahlungslimit auf der Karte; oder",
        "- Die Karte ist nicht für internationale / ausländische Transaktionen vorgesehen, oder die " +
                "ausstellende Bank hat die Transaktion abgelehnt."
    )
    override val paypalCardReasons: ArrayList<String> = arrayListOf(
        ("Stellen Sie sicher, dass Ihr PayPal-Konto nicht geschlossen oder gelöscht wird. " +
                "Die mit Ihrem PayPal-Konto verbundene Kreditkarte muss aktiv sein. " +
                "Häufige Gründe für erfolglose Kartenzahlungen: "),
        "- Die Karte ist in Ihrem PayPal-Konto nicht verifiziert.",
        "- Die Kartendaten (Nummer, Gültigkeitsdauer, CVC, Rechnungsadresse) sind unvollständig oder wurden falsch eingegeben.",
        "- Die Karte ist abgelaufen; oder",
        "- Unzureichendes Guthaben oder Zahlungslimit auf der Karte."
    )
}


class RuLocale(data: FailedPaymentData) : LanguageLocale {
    override val greeting: String = "Спасибо, что остаетесь с JetBrains."

    override val unfortunately: String =
        "К сожалению, нам не удалось списать средства с ${data.cardDetails ?: "your card"} за " +
                "вашу".simplyPluralize(data.items.sumBy { it.quantity }, Language.RU)

    override val personalCustomer: String =
        " ${data.subscriptionPack.billingPeriod.name.toLowerCase()} подписку " +
                "на ${data.items.joinToString(", ") { it.productName }}."

    override val organizationCustomer: String =
        " подписку".simplyPluralize(data.items.sumBy { it.quantity }, Language.RU) +
                " как часть пакета подписки ${data.subscriptionPack.subPackRef?.let { "#$it" }.orEmpty()} " +
                "для следующего " +
                (when (data.subscriptionPack.billingPeriod) {
                    BillingPeriod.MONTHLY -> "месяца"
                    BillingPeriod.ANNUAL -> "года"
                    else -> "периода"
                } + ": ")


    override val toEnsure: String =
        ("Чтобы обеспечить бесперебойный " +
                "доступ к ${"вашей".simplyPluralize(data.subscriptionPack.totalLicenses, Language.RU)}" +
                " ${"подписке".simplyPluralize(data.subscriptionPack.totalLicenses, Language.RU)}, пожалуйста, " +
                "перейдите по ссылке " +
                "и обновите ${"свою".simplyPluralize(data.subscriptionPack.totalLicenses, Language.RU)} " +
                "${"подписку".simplyPluralize(data.subscriptionPack.totalLicenses, Language.RU)} ")

    override val hRefSentence: String = "вручную"


    override val till: String =
        " до ${DateTimeFormatter.ofPattern("MMM dd, YYYY", Locale("ru")).format(data.paymentDeadline)}"

    override val doubleCheck: String =
        "Вы можете перепроверить свою платежную карту и попробовать еще раз, использовать другую карту " +
                "или выбрать другой способ оплаты."

    override val creditCardReasons: ArrayList<String> = arrayListOf(
        "Распространенные причины неудачных платежей по кредитной карте:",
        "- Срок действия карты истек, либо срок годности введен неверно;",
        "- Недостаточно средств или платежного лимита на карте; или же",
        "- Карта не предназначена для международных / зарубежных транзакций, или банк-эмитент отклонил транзакцию."
    )
    override val paypalCardReasons: ArrayList<String> = arrayListOf(
        ("Убедитесь, что ваша учетная запись PayPal не закрыта и не удалена. " +
                "Кредитная карта, подключенная к вашей учетной записи PayPal, должна быть активной. " +
                "Распространенные причины неудачных платежей по карте:"),
        "- Карта не подтверждена в вашем аккаунте PayPal;",
        "- Данные карты (Номер, Срок действия, CVC, Платежный адрес) являются неполными или были введены неверно;",
        "- Срок действия карты истек; или же",
        "- Недостаточно средств или платежного лимита на карте."
    )
}

class EnLocale(data: FailedPaymentData) : LanguageLocale {
    override val greeting: String = "Thank you for staying with JetBrains."

    override val unfortunately: String =
        "Unfortunately, we were not able to charge ${data.cardDetails ?: "your card"} for your "

    override val personalCustomer: String =
        "${data.subscriptionPack.billingPeriod.name.toLowerCase()} subscription " +
                "to ${data.items.joinToString(", ") { it.productName }}."

    override val organizationCustomer: String =
        "subscription".simplyPluralize(data.items.sumBy { it.quantity }, Language.EN) +
                " as part of Subscription Pack ${data.subscriptionPack.subPackRef?.let { "#$it" }.orEmpty()} " +
                "for the next " +
                (when (data.subscriptionPack.billingPeriod) {
                    BillingPeriod.MONTHLY -> "month"
                    BillingPeriod.ANNUAL -> "year"
                    else -> "period"
                } + ": ")


    override val toEnsure: String =
        ("To ensure uninterrupted access to " +
                "your ${"subscription".simplyPluralize(data.subscriptionPack.totalLicenses, Language.EN)}, please " +
                "follow the link and renew " +
                "your ${"subscription".simplyPluralize(data.subscriptionPack.totalLicenses, Language.EN)} ")

    override val hRefSentence: String = "manually"


    override val till: String =
        " till ${DateTimeFormatter.ofPattern("MMM dd, YYYY", Locale.US).format(data.paymentDeadline)}"

    override val doubleCheck: String =
        "You can double-check and try your existing payment card again, use another card, or choose a different payment method."

    override val creditCardReasons: ArrayList<String> = arrayListOf(
        "Common reasons for failed credit card payments include:",
        "- The card is expired, or the expiration date was entered incorrectly;",
        "- Insufficient funds or payment limit on the card; or",
        "- The card is not set up for international/overseas transactions, or the issuing bank has rejected the transaction."
    )
    override val paypalCardReasons: ArrayList<String> = arrayListOf(
        ("Please make sure that your PayPal account is not closed or deleted. " +
                "The credit card connected to your PayPal account should be active. " +
                "Common reasons for failed card payments include:"),
        "- The card is not confirmed in your PayPal account;",
        "- The card details (Number, Expiration date, CVC, Billing address) are incomplete or were entered incorrectly;",
        "- The card is expired; or",
        "- Insufficient funds or payment limit on the card."
    )

}

