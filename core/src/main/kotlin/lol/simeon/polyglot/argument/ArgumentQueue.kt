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

package lol.simeon.polyglot.argument

/** A forward-only cursor over the raw input tokens supplied to a command. */
public class ArgumentQueue(tokens: List<String>) {
    private val tokens: List<String> = tokens.toList()
    private var cursor: Int = 0

    /** Whether at least one token remains. */
    public fun hasNext(): Boolean = cursor < tokens.size

    /** Returns the next token without consuming it, or `null` if exhausted. */
    public fun peek(): String? = tokens.getOrNull(cursor)

    /** Consumes and returns the next token. */
    public fun next(): String {
        check(hasNext()) { "No remaining tokens" }
        return tokens[cursor++]
    }

    /** Consumes every remaining token and returns them joined by a single space. */
    public fun consumeRemaining(): String {
        val rest = tokens.subList(cursor, tokens.size).joinToString(" ")
        cursor = tokens.size
        return rest
    }

    /** The remaining, not-yet-consumed tokens. */
    public fun remaining(): List<String> = tokens.subList(cursor, tokens.size).toList()

    /** Zero-based index of the next token. */
    public val position: Int get() = cursor
}
