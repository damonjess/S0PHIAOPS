import com.sophia.ops.viewmodel.DashboardViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelVerificationTest {

    @Test
    fun testExpectedModelSha256IsSet() {
        val expectedSha = DashboardViewModel.EXPECTED_MODEL_SHA256
        assertTrue("EXPECTED_MODEL_SHA256 constant should not be blank", expectedSha.isNotBlank())
        assertEquals("SHA-256 hash length should be 64 hex characters", 64, expectedSha.length)
    }

    @Test
    fun testComputeSha256CalculatesCorrectHash() {
        val tempFile = File.createTempFile("test_model", ".task")
        try {
            tempFile.writeText("SOPHIA OPS TEST MODEL CONTENT")
            val computedHash = DashboardViewModel.computeSha256(tempFile)
            assertTrue("Hash should be non-blank 64-char hex string", computedHash.isNotBlank())
            assertEquals("SHA-256 hash length should be 64", 64, computedHash.length)

            // Verification against known SHA-256 for "SOPHIA OPS TEST MODEL CONTENT"
            val expectedKnownHash = "b4012fb027b0fe2416eec270ba37896d7414ff59f49163c027c4a99d72cca130"
            assertEquals(expectedKnownHash, computedHash)
        } finally {
            tempFile.delete()
        }
    }
}
