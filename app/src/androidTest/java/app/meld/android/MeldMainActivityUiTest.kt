package app.meld.android

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeldMainActivityUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun navTabMatcher(context: Context, labelRes: Int): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            .and(hasText(context.getString(labelRes)))

    @Test
    fun mainActivity_navigatesAcrossStudioToolsAndPrefsTabs() {
        val context = composeRule.activity
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.change_file))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.quick_tools_heading))
            .assertIsDisplayed()

        composeRule.onNode(navTabMatcher(context, R.string.nav_tools))
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.all_tools_heading))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.search_tools_placeholder))
            .performTextInput("PPTX")
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.tool_pptx_to_pdf_title))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_clear_search))
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNode(navTabMatcher(context, R.string.nav_prefs))
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.prefs_heading))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.privacy_promise_title))
            .assertIsDisplayed()

        composeRule.onNode(navTabMatcher(context, R.string.nav_studio))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.quick_tools_heading))
            .assertIsDisplayed()
    }
}
