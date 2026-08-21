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

package lol.simeon.polyglot.execution

import lol.simeon.polyglot.exception.PolyglotException
import lol.simeon.polyglot.model.CommandArgument
import lol.simeon.polyglot.model.CommandNode
import lol.simeon.polyglot.permission.PermissionResolver
import lol.simeon.polyglot.suggestion.Suggestion
import lol.simeon.polyglot.suggestion.SuggestionContext
import lol.simeon.polyglot.suggestion.SuggestionRegistry

/**
 * Computes completion candidates for a partially-typed command: subcommand names the sender may
 * use, plus suggestions for the argument currently being typed.
 */
public class CommandCompleter<S>(
    private val suggestions: SuggestionRegistry<S>,
    private val permissions: PermissionResolver<S>,
) {
    /** @param args the tokens following the root command name; the last is the partial token. */
    public suspend fun complete(root: CommandNode<S>, sender: S, args: List<String>): List<Suggestion> {
        var node = root
        var index = 0
        while (index < args.size - 1) {
            val child = node.child(args[index]) ?: break
            node = child
            index++
        }

        val partial = args.lastOrNull().orEmpty()
        val preceding = if (args.size - 1 > index) args.subList(index, args.size - 1) else emptyList()
        val result = mutableListOf<Suggestion>()

        // typing an option: suggest the node's unused --long names
        if (partial.startsWith("-")) {
            node.arguments
                .filter { it.named || it.flag }
                .map { "--${it.name}" to it }
                .filter { it.first.startsWith(partial, ignoreCase = true) }
                .forEach { (long, arg) -> result += Suggestion(long, arg.description.ifEmpty { null }) }
            return result.distinctBy { it.value }
        }

        // subcommand names at the first argument position
        if (preceding.isEmpty()) {
            for (child in node.childNodes) {
                if (child.name.startsWith(partial, ignoreCase = true) && permits(child, sender)) {
                    result += Suggestion(child.name, child.description.ifEmpty { null })
                }
            }
        }

        // positional argument suggestion (options and their values are skipped when counting)
        val positionalIndex = preceding.count { !it.startsWith("-") }
        node.arguments.filter { it.positional }.getOrNull(positionalIndex)?.let { arg ->
            result += suggestFor(arg, sender, partial)
        }

        return result.distinctBy { it.value }
    }

    private suspend fun suggestFor(arg: CommandArgument, sender: S, partial: String): List<Suggestion> {
        val provider = arg.suggestionId?.let { suggestions.forId(it) } ?: suggestions.forType(arg.type)
        if (provider != null) {
            return provider.suggest(SuggestionContext(sender, partial, arg.name))
                .filter { it.value.startsWith(partial, ignoreCase = true) }
        }
        return arg.choices
            .filter { it.startsWith(partial, ignoreCase = true) }
            .map { Suggestion(it) }
    }

    private suspend fun permits(node: CommandNode<S>, sender: S): Boolean {
        val permitted = node.permission?.let { permissions.hasPermission(sender, it) } ?: true
        if (!permitted) return false
        return try {
            node.guards.forEach { it.check(sender) }
            true
        } catch (_: PolyglotException) {
            false
        }
    }
}
