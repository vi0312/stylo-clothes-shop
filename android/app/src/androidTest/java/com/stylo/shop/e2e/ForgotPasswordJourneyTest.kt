package com.stylo.shop.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaspersky.components.composesupport.config.withComposeSupport
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.stylo.shop.MainActivity
import com.stylo.shop.core.ui.UiTags
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The code only exists inside the mail, so this journey needs a Mailtrap Email Testing sandbox
 * behind the server's SMTP settings:
 *
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.mailtrapToken=<token> \
 *     -Pandroid.testInstrumentationRunnerArguments.mailtrapSandboxId=<id>
 *
 * Without both arguments the test is skipped; the server integration suite covers the contract.
 */
@RunWith(AndroidJUnit4::class)
class ForgotPasswordJourneyTest : TestCase(kaspressoBuilder = Kaspresso.Builder.withComposeSupport()) {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val arguments = InstrumentationRegistry.getArguments()
    private val token: String? = arguments.getString("mailtrapToken")
    private val sandboxId: String? = arguments.getString("mailtrapSandboxId")

    private val email = "e2e+reset${System.currentTimeMillis()}@stylo.local"
    private val password = "E2ePass123!"
    private val newPassword = "E2ePass456!"
    private val fullName = "E2E Reset"

    @Test
    fun resetsThePasswordWithTheEmailedCode() = run {
        assumeTrue(
            "Pass -Pandroid.testInstrumentationRunnerArguments.mailtrapToken and mailtrapSandboxId",
            token != null && sandboxId != null,
        )

        step("Register a customer, sign out and ask for a code") {
            composeRule.startLoggedOut()
            composeRule.registerCustomer(E2eAuthScreen(composeRule), fullName, "0901234567", email, password)
            composeRule.startLoggedOut()
            composeRule.onNodeWithTag(UiTags.AuthForgotLink).performScrollTo().performClick()
            composeRule.waitForTag(UiTags.AuthResetEmail)
            composeRule.onNodeWithTag(UiTags.AuthResetEmail).performTextInput(email)
            composeRule.onNodeWithTag(UiTags.AuthResetSendCode).performScrollTo().performClick()
            composeRule.waitForTag(UiTags.AuthResetCode)
        }

        step("Enter the emailed code and the new password") {
            composeRule.onNodeWithTag(UiTags.AuthResetCode).performTextInput(waitForEmailedCode())
            composeRule.onNodeWithTag(UiTags.AuthResetPassword).performTextInput(newPassword)
            composeRule.onNodeWithTag(UiTags.AuthResetSubmit).performScrollTo().performClick()
            composeRule.waitForTag(UiTags.AuthResetDone)
            composeRule.onNodeWithTag(UiTags.AuthResetDone).performClick()
            composeRule.waitForTag(UiTags.AuthEmail)
        }

        step("Sign in with the new password") {
            composeRule.loginCustomer(E2eAuthScreen(composeRule), email, newPassword, fullName)
        }
    }

    private fun waitForEmailedCode(): String {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            latestEmailedCode()?.let { return it }
            Thread.sleep(1_000)
        }
        error("Mailtrap never captured a reset mail for $email")
    }

    private fun latestEmailedCode(): String? {
        val search = URLEncoder.encode(email, "UTF-8")
        val listing = httpGet(
            "https://mailtrap.io/api/sandboxes/${requireNotNull(sandboxId)}/messages?search=$search",
        )
        val txtPath = Regex("\"txt_path\":\"([^\"]+)\"").find(listing)?.groupValues?.get(1)
            ?: return null
        val body = httpGet("https://mailtrap.io$txtPath")
        return Regex("\\b(\\d{6})\\b").find(body)?.groupValues?.get(1)
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Api-Token", requireNotNull(token))
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) "" else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            connection.disconnect()
        }
    }
}
