import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.alfleyla.zeituna.App
import com.alfleyla.zeituna.data.BookititApiService
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Setting the proxy to your Supabase Edge Function
    BookititApiService.setProxy("https://rdtxtuuqlqvluwuneyta.supabase.co/functions/v1/smart-worker")

    ComposeViewport(viewportContainerId = "compose-receiver") {
        App()
    }
}
