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

package lol.simeon.polyglot.model

import lol.simeon.polyglot.argument.ArgumentValidator
import kotlin.reflect.KClass

/** Platform-agnostic metadata describing one positional argument of a command. */
public class CommandArgument(
    public val name: String,
    public val type: KClass<*>,
    public val description: String = "",
    public val optional: Boolean = false,
    public val greedy: Boolean = false,
    public val suggestionId: String? = null,
    public val choices: List<String> = emptyList(),
    public val range: ClosedFloatingPointRange<Double>? = null,
    /** A named option (`--name value`) rather than positional. */
    public val named: Boolean = false,
    /** Optional single-character alias (`-x`) for a named option or flag. */
    public val shorthand: Char? = null,
    /** A boolean presence flag (`--name` -> true); never consumes a value. */
    public val flag: Boolean = false,
    /** Custom validators run after parsing; the first non-null message rejects the value. */
    public val validators: List<ArgumentValidator> = emptyList(),
) {
    /** True when this argument is bound positionally (not named and not a flag). */
    public val positional: Boolean get() = !named && !flag
}
