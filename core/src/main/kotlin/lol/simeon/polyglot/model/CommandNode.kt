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

/**
 * A node in the resolved command tree. A node may be a group (has [children]), a leaf (has a
 * [handler] and [arguments]), or both (a group with a default handler). Children are keyed by
 * lower-cased name and by every alias.
 */
public class CommandNode<S>(
    public val name: String,
    public val aliases: List<String>,
    public val description: String,
    public val permission: String?,
    public val arguments: List<CommandArgument>,
    public val handler: CommandHandler<S>?,
    children: Map<String, CommandNode<S>>,
) {
    public val children: Map<String, CommandNode<S>> = children

    /** Resolves an immediate child by name or alias, case-insensitively. */
    public fun child(name: String): CommandNode<S>? = children[name.lowercase()]

    /** Distinct child nodes (alias keys collapsed). */
    public val childNodes: List<CommandNode<S>> get() = children.values.distinct()

    /** Whether this node can be executed directly. */
    public val executable: Boolean get() = handler != null
}
