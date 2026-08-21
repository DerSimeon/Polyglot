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

package lol.simeon.polyglot.cli

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import lol.simeon.polyglot.CommandManager
import lol.simeon.polyglot.exception.PolyglotException
import lol.simeon.polyglot.model.CommandNode
import lol.simeon.polyglot.suggestion.Suggestion

/**
 * CLI adapter. Commands are dispatched directly from tokenized input; there is no external registry
 * to publish to, so [registerNative] is a no-op. Provides raw-line execution and completion helpers
 * (the latter suitable for driving shell completion).
 */
public open class CliCommandManager(
    override val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : CommandManager<CommandSender>() {

    override fun registerNative(node: CommandNode<CommandSender>) {
        // CLI resolves commands on demand via dispatch; nothing to publish.
    }

    /** Tokenizes and executes [line], reporting any command error back to [sender]. */
    public suspend fun execute(sender: CommandSender, line: String) {
        try {
            dispatch(sender, CommandLineTokenizer.tokenize(line))
        } catch (error: PolyglotException) {
            sender.sendMessage(formatError(sender, error))
        }
    }

    /** Returns completions for a partially-typed [line]. */
    public suspend fun completeLine(sender: CommandSender, line: String): List<Suggestion> =
        complete(sender, CommandLineTokenizer.tokenize(line))
}
