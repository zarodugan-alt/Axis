package axis.kernel.search

/**
 * In-memory fuzzy matcher for app labels (drawer + home search, spec §S3).
 *
 * Ranking (lower is better): exact(0) < prefix(1) < contains(2) <
 * word-prefix(3) < levenshtein≤2(4+dist). Null = no match.
 * Allocation-light: lowercases once per call, early-exits the DP table.
 */
object FuzzySearch {

    fun rank(query: String, label: String): Int? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        val l = label.trim().lowercase()
        if (l.isEmpty()) return null
        if (l == q) return 0
        if (l.startsWith(q)) return 1
        if (l.contains(q)) return 2
        val words = l.split(' ')
        if (words.any { it.startsWith(q) }) return 3
        var best = Int.MAX_VALUE
        for (w in words) {
            // Skip words whose length delta alone exceeds the budget.
            if (kotlin.math.abs(w.length - q.length) > 2) continue
            val d = levenshtein(q, w, max = 2)
            if (d < best) best = d
            if (best == 0) break
        }
        if (kotlin.math.abs(l.length - q.length) <= 2) {
            best = minOf(best, levenshtein(q, l, max = 2))
        }
        return if (best <= 2) 4 + best else null
    }

    fun <T> filter(query: String, items: List<T>, labelOf: (T) -> String): List<T> {
        if (query.isBlank()) return items
        return items
            .mapNotNull { item ->
                val r = rank(query, labelOf(item)) ?: return@mapNotNull null
                r to item
            }
            .sortedWith(compareBy({ it.first }, { labelOf(it.second).lowercase() }))
            .map { it.second }
    }

    /**
     * Levenshtein distance capped at [max]+1. Early-exits rows whose minimum
     * already exceeds [max], so the common no-match case stays O(q × small).
     */
    fun levenshtein(a: String, b: String, max: Int = 2): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > max) return max + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
