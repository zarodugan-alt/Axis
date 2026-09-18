package axis.kernel.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzySearchTest {

    @Test
    fun blankQuery_matchesEverythingWithRankZero() {
        assertEquals(0, FuzzySearch.rank("", "WhatsApp"))
        assertEquals(0, FuzzySearch.rank("   ", "WhatsApp"))
    }

    @Test
    fun exactAndPrefix_ranks() {
        assertEquals(0, FuzzySearch.rank("whatsapp", "WhatsApp"))
        assertEquals(1, FuzzySearch.rank("whats", "WhatsApp"))
    }

    @Test
    fun containsAndWordPrefix_ranks() {
        assertEquals(2, FuzzySearch.rank("sapp", "WhatsApp"))
        assertEquals(3, FuzzySearch.rank("mes", "Facebook Messenger"))
    }

    @Test
    fun typoWithinTwoEdits_matches() {
        assertEquals(5, FuzzySearch.rank("whatsap", "WhatsApp")) // 1 deletion
        assertEquals(6, FuzzySearch.rank("whatspp", "WhatsApp")) // 2 edits
    }

    @Test
    fun farMismatch_returnsNull() {
        assertNull(FuzzySearch.rank("zzz", "WhatsApp"))
        assertNull(FuzzySearch.rank("whatsapppro", "FM"))
    }

    @Test
    fun filter_sortsByRankThenLabel() {
        val items = listOf("Messenger", "WhatsApp", "Whatnot", "Settings")
        val out = FuzzySearch.filter("what", items) { it }
        assertEquals(listOf("WhatsApp", "Whatnot"), out)
    }

    @Test
    fun levenshtein_matchesKnownDistances() {
        assertEquals(0, FuzzySearch.levenshtein("abc", "abc", 5))
        assertEquals(1, FuzzySearch.levenshtein("kitten", "sitten", 5))
        assertEquals(3, FuzzySearch.levenshtein("kitten", "sitting", 5))
        // Capped: anything beyond max reports max+1.
        assertTrue(FuzzySearch.levenshtein("abc", "xyz", 2) > 2)
    }

    @Test
    fun filter_hundredsOfApps_completesFast() {
        val items = List(500) { "App number $it with a fairly long label" }
        val start = System.nanoTime()
        val out = FuzzySearch.filter("nubmer 42", items) { it } // typo on purpose
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("first result should be the typo target", out.first().contains("42"))
        assertTrue("500-item fuzzy filter took ${ms}ms, budget is 100ms", ms < 100)
    }
}
