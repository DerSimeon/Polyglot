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

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import lol.simeon.polyglot.argument.CommandLineTokenizer
import lol.simeon.polyglot.execution.ArgumentInput
import lol.simeon.polyglot.model.CommandArgument

/**
 * How one positional argument of a leaf is read back from a parsed Brigadier context: through a
 * native [ArgumentTypeMapping] (already typed), as a single raw string token, or as a raw greedy
 * tail that is tokenized so options may be extracted from it.
 */
public class PositionalBinding<Src>(
    public val argument: CommandArgument,
    private val mapping: ArgumentTypeMapping<Src, *>?,
    private val tokenizeTail: Boolean,
) {
    /** Whether the argument is parsed natively by Brigadier. */
    public val native: Boolean get() = mapping != null

    /** Reads the argument from [ctx], or [ArgumentInput.Absent] when it was not supplied. */
    public fun read(ctx: CommandContext<Src>): ArgumentInput {
        val present = ctx.nodes.any { it.node.name == argument.name }
        if (!present) return ArgumentInput.Absent
        if (mapping != null) return ArgumentInput.Resolved(mapping.extract(ctx, argument.name))
        val text = StringArgumentType.getString(ctx, argument.name)
        return if (tokenizeTail) ArgumentInput.Raw(CommandLineTokenizer.tokenize(text)) else ArgumentInput.Raw(text)
    }
}
