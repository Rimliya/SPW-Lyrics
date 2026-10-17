package dev.gaboron.spwlyrics.domain

import kotlin.math.abs

/**
 * Compares two lyric documents by their text instead of their metadata, so a cover,
 * a live version or a same-named track cannot replace the lyrics being displayed.
 */
object LyricsSimilarity {
    /** Below this many usable lines the local text is too short to judge another document. */
    const val MIN_REFERENCE_LINES = 5
    const val MIN_LINE_CHARACTERS = 4
    const val LINE_MATCH = 0.62
    const val REJECT_BELOW = 0.25

    data class Result(val coverage: Double, val comparable: Boolean)

    fun compare(reference: LyricsDocument, candidate: LyricsDocument): Result {
        val referenceLines = usableLines(reference)
        val candidateLines = usableLines(candidate)
        if (referenceLines.size < MIN_REFERENCE_LINES || candidateLines.size < MIN_REFERENCE_LINES) {
            return Result(1.0, comparable = false)
        }
        if (!sameScript(referenceLines, candidateLines)) return Result(1.0, comparable = false)
        val referencePrepared = referenceLines.map(TextNormalizer::prepare)
        val candidatePrepared = candidateLines.map(TextNormalizer::prepare)
        return Result(
            maxOf(coverage(referencePrepared, candidatePrepared), coverage(candidatePrepared, referencePrepared)),
            comparable = true,
        )
    }

    /** A null reference means the track has no local lyrics to compare against. */
    fun accepts(reference: LyricsDocument?, candidate: LyricsDocument): Boolean =
        reference == null || compare(reference, candidate).let { !it.comparable || it.coverage >= REJECT_BELOW }

    private fun usableLines(document: LyricsDocument): List<String> = document.lines.asSequence()
        .filterNot { it.background }
        .map { it.text.trim() }
        .filter { line -> line.count(Char::isLetterOrDigit) >= MIN_LINE_CHARACTERS }
        .toList()

    private fun coverage(
        source: List<TextNormalizer.PreparedText>,
        target: List<TextNormalizer.PreparedText>,
    ): Double = source.count { prepared ->
        target.any { other -> TextNormalizer.similarity(prepared, other) >= LINE_MATCH }
    }.toDouble() / source.size

    /** Translations, romaji and the original script cannot be compared line by line. */
    private fun sameScript(left: List<String>, right: List<String>): Boolean =
        abs(cjkShare(left) - cjkShare(right)) <= 0.45

    private fun cjkShare(lines: List<String>): Double {
        var cjk = 0
        var letters = 0
        lines.forEach { line ->
            line.forEach { character ->
                if (character.isLetter()) {
                    letters++
                    if (character.isCjk()) cjk++
                }
            }
        }
        return if (letters == 0) 0.0 else cjk.toDouble() / letters
    }

    private fun Char.isCjk(): Boolean =
        this in '\u3040'..'\u30ff' || this in '\u3400'..'\u4dbf' ||
            this in '\u4e00'..'\u9fff' || this in '\uac00'..'\ud7af' || this in '\uff66'..'\uff9f'
}