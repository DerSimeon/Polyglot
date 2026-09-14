/*
 * 2026 Simeon L.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package lol.simeon.polyglot.brigadier

import com.mojang.brigadier.LiteralMessage
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.coroutines.runBlocking
import lol.simeon.polyglot.argument.CommandLineTokenizer
import lol.simeon.polyglot.suggestion.Suggestion
import lol.simeon.polyglot.suggestion.SuggestionContext
import java.util.concurrent.CompletableFuture

/** Adapts Polyglot suggestion sources to Brigadier [SuggestionProvider]s. */
public object BrigadierSuggestions {

    /** Suggests from a core provider for the single-token argument [argumentName]. */
    public fun <S, Src> ofProvider(
        provider: lol.simeon.polyglot.suggestion.SuggestionProvider<S>,
        argumentName: String,
        senderFactory: (Src) -> S,
    ): SuggestionProvider<Src> = SuggestionProvider { ctx, builder ->
        val sender = senderFactory(ctx.source)
        val partial = builder.remaining
        val suggestions = runBlocking { provider.suggest(SuggestionContext(sender, partial, argumentName)) }
        offer(builder, partial, suggestions)
    }

    /** Suggests fixed [choices]. */
    public fun <Src> ofChoices(choices: List<String>): SuggestionProvider<Src> = SuggestionProvider { _, builder ->
        offer(builder, builder.remaining, choices.map { Suggestion(it) })
    }

    /**
     * Suggests for a multi-token tail (a greedy argument or the trailing options node) by asking
     * [complete] with the tokens typed so far; only the last token is completed.
     */
    public fun <Src> ofTail(
        complete: (source: Src, tokens: List<String>) -> List<Suggestion>,
    ): SuggestionProvider<Src> = SuggestionProvider { ctx, builder ->
        val text = builder.remaining
        val partial = if (text.isEmpty() || text.last().isWhitespace()) "" else text.substringAfterLast(' ')
        val tokens = CommandLineTokenizer.tokenize(text) + if (partial.isEmpty()) listOf("") else emptyList()
        val offset = builder.createOffset(builder.start + text.length - partial.length)
        offer(offset, partial, complete(ctx.source, tokens))
    }

    private fun offer(builder: SuggestionsBuilder, partial: String, suggestions: List<Suggestion>): CompletableFuture<Suggestions> {
        suggestions
            .filter { it.value.startsWith(partial, ignoreCase = true) }
            .forEach { suggestion ->
                val tooltip = suggestion.tooltip
                if (tooltip == null) builder.suggest(suggestion.value) else builder.suggest(suggestion.value, LiteralMessage(tooltip))
            }
        return builder.buildFuture()
    }
}
