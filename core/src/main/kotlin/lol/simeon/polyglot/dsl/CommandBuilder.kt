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

package lol.simeon.polyglot.dsl

import lol.simeon.polyglot.argument.ArgumentValidator
import lol.simeon.polyglot.context.CommandContext
import lol.simeon.polyglot.guard.CommandGuard
import lol.simeon.polyglot.model.CommandArgument
import lol.simeon.polyglot.model.CommandHandler
import lol.simeon.polyglot.model.CommandNode

/**
 * Type-safe builder producing the same [CommandNode] model as the annotation scanner, for
 * programmatic/dynamic command definitions. Supports arbitrarily nested [subcommand]s.
 */
@CommandDsl
public class CommandBuilder<S>(private val name: String) {
    private var aliases: List<String> = emptyList()
    private var description: String = ""
    private var permission: String? = null
    private var handler: CommandHandler<S>? = null
    private val arguments: MutableList<CommandArgument> = mutableListOf()
    private val guards: MutableList<CommandGuard<S>> = mutableListOf()
    private val children: LinkedHashMap<String, CommandNode<S>> = LinkedHashMap()

    public fun aliases(vararg values: String) {
        aliases = values.toList()
    }

    public fun description(value: String) {
        description = value
    }

    public fun permission(node: String) {
        permission = node
    }

    /** Attaches a runtime guard that runs before this node's handler (and its subcommands). */
    public fun guard(guard: CommandGuard<S>) {
        guards += guard
    }

    /** Adds a fully-specified argument. */
    public fun argument(argument: CommandArgument) {
        arguments += argument
    }

    /** Adds an argument of reified type [T]. */
    public inline fun <reified T : Any> argument(
        name: String,
        optional: Boolean = false,
        greedy: Boolean = false,
        suggestionId: String? = null,
        choices: List<String> = emptyList(),
        range: ClosedFloatingPointRange<Double>? = null,
        validators: List<ArgumentValidator> = emptyList(),
    ) {
        argument(
            CommandArgument(
                name = name,
                type = T::class,
                optional = optional,
                greedy = greedy,
                suggestionId = suggestionId,
                choices = choices,
                range = range,
                validators = validators,
            ),
        )
    }

    /** Adds a GNU-style named option (`--name value` / `-x value`). */
    public inline fun <reified T : Any> namedArgument(
        name: String,
        shorthand: Char? = null,
        optional: Boolean = false,
        suggestionId: String? = null,
        choices: List<String> = emptyList(),
        range: ClosedFloatingPointRange<Double>? = null,
        validators: List<ArgumentValidator> = emptyList(),
    ) {
        argument(
            CommandArgument(
                name = name,
                type = T::class,
                optional = optional,
                suggestionId = suggestionId,
                choices = choices,
                range = range,
                named = true,
                shorthand = shorthand,
                validators = validators,
            ),
        )
    }

    /** Adds a boolean presence flag (`--name` -> true). */
    public fun flag(name: String, shorthand: Char? = null) {
        argument(
            CommandArgument(
                name = name,
                type = Boolean::class,
                optional = true,
                flag = true,
                shorthand = shorthand,
            ),
        )
    }

    /** Sets the executable body. */
    public fun executes(block: suspend (CommandContext<S>) -> Unit) {
        handler = CommandHandler { block(it) }
    }

    /** Declares a nested subcommand (which may itself declare further subcommands). */
    public fun subcommand(name: String, block: CommandBuilder<S>.() -> Unit) {
        val child = CommandBuilder<S>(name).apply(block).build()
        children[child.name.lowercase()] = child
        child.aliases.forEach { children[it.lowercase()] = child }
    }

    public fun build(): CommandNode<S> = CommandNode(
        name = name,
        aliases = aliases,
        description = description,
        permission = permission,
        arguments = arguments.toList(),
        handler = handler,
        children = children,
        guards = guards.toList(),
    )
}

/** Entry point for the command DSL. */
public fun <S> command(name: String, block: CommandBuilder<S>.() -> Unit): CommandNode<S> =
    CommandBuilder<S>(name).apply(block).build()
