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

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import kotlinx.coroutines.runBlocking
import lol.simeon.polyglot.CommandManager
import lol.simeon.polyglot.argument.CommandLineTokenizer
import lol.simeon.polyglot.exception.PolyglotException
import lol.simeon.polyglot.execution.ArgumentInput
import lol.simeon.polyglot.model.CommandNode

/**
 * The Brigadier [Command] attached to every executable node of a translated tree. Collects the
 * natively parsed arguments, hands them to [CommandManager.dispatchResolved] on the calling
 * thread and routes command failures to [onError].
 *
 * Returns [Command.SINGLE_SUCCESS] on success and `0` when a [PolyglotException] was reported.
 * Brigadier's own exceptions (for example a failed entity selector) propagate unchanged.
 */
public class PolyglotBrigadierCommand<S, Src>(
    private val manager: CommandManager<S>,
    private val path: List<CommandNode<S>>,
    private val bindings: List<PositionalBinding<Src>>,
    private val optionsNode: String?,
    private val senderFactory: (Src) -> S,
    private val onError: (S, PolyglotException) -> Unit,
) : Command<Src> {

    override fun run(ctx: CommandContext<Src>): Int {
        val sender = senderFactory(ctx.source)
        val inputs = bindings.associate { it.argument.name to it.read(ctx) }
        val optionTokens = optionsNode
            ?.takeIf { name -> ctx.nodes.any { it.node.name == name } }
            ?.let { CommandLineTokenizer.tokenize(StringArgumentType.getString(ctx, it)) }
            .orEmpty()
        val rawInput = CommandLineTokenizer.tokenize(ctx.input)
        return try {
            runBlocking { manager.dispatchResolved(sender, path, inputs, optionTokens, rawInput) }
            Command.SINGLE_SUCCESS
        } catch (error: PolyglotException) {
            onError(sender, error)
            0
        }
    }
}
