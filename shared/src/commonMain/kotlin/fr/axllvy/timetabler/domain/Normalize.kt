package fr.axllvy.timetabler.domain

private val FOLDS: Map<Char, String> = buildMap {
  fun fold(base: String, accented: String) = accented.forEach { put(it, base) }
  fold("a", "àáâãäåāăą")
  fold("c", "çćĉċč")
  fold("d", "ďđ")
  fold("e", "èéêëēĕėęě")
  fold("g", "ĝğġģ")
  fold("h", "ĥħ")
  fold("i", "ìíîïĩīĭįı")
  fold("j", "ĵ")
  fold("k", "ķ")
  fold("l", "ĺļľŀł")
  fold("n", "ñńņňŉ")
  fold("o", "òóôõöøōŏő")
  fold("r", "ŕŗř")
  fold("s", "śŝşšß")
  fold("t", "ţťŧ")
  fold("u", "ùúûüũūŭůűų")
  fold("w", "ŵ")
  fold("y", "ýÿŷ")
  fold("z", "źżž")
  put('æ', "ae")
  put('œ', "oe")
}

/** Lowercases [text], strips accents and punctuation, and collapses whitespace, for matching and search. */
fun normalizeText(text: String): String {
  val folded = buildString {
    for (c in text.lowercase()) {
      val replacement = FOLDS[c]
      when {
        replacement != null -> append(replacement)
        c.isLetterOrDigit() -> append(c)
        else -> append(' ')
      }
    }
  }
  return folded.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
}
