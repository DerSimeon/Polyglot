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

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.suggestion.SuggestionProvider
import kotlinx.coroutines.runBlocking
import lol.simeon.polyglot.CommandManager
import lol.simeon.polyglot.exception.PolyglotException
import lol.simeon.polyglot.model.CommandArgument
import lol.simeon.polyglot.model.CommandNode
import lol.simeon.polyglot.permission.PermissionResolver
import java.util.function.Predicate

/**
 * Translates a Polyglot [CommandNode] tree into native Brigadier nodes.
 *
 * - Every command / subcommand (and each alias) becomes a literal; nesting is unlimited.
 * - Positional arguments become typed argument nodes when the [types] registry has a mapping
 *   (primitives, plus whatever the platform registers). Everything else becomes a string node
 *   parsed by the core [lol.simeon.polyglot.argument.ArgumentParser], with suggestions taken
 *   from the core [lol.simeon.polyglot.suggestion.SuggestionRegistry] (enums, `@Choice`, `@Suggestion`).
 * - Optional arguments make the preceding node executable, so `/cmd a` and `/cmd a b` both run.
 * - `@Named` options and `@Flag`s, being order-independent, are collected in one trailing greedy
 *   `options` node (`--name value`, `-x value`, `--flag`); a trailing `@Greedy` argument absorbs
 *   them instead.
 * - The string `@Permission` and every guard implementing [BrigadierRequirement] are mirrored into
 *   the node's `requires`, hiding it from unauthorized sources.
 *
 * @param S the Polyglot sender type
 * @param Src the Brigadier source type
 */
public class BrigadierTreeBuilder<S, Src>(
    private val manager: CommandManager<S>,
    private val types: BrigadierTypeRegistry<Src>,
    private val senderFactory: (Src) -> S,
    private val permissionResolver: PermissionResolver<S>,
    private val onError: (S, PolyglotException) -> Unit,
) {
    /** Name of the trailing node holding named options and flags. */
    public val optionsNodeName: String = OPTIONS_NODE

    /** Builds one literal per name and alias of [root]. */
    public fun build(root: CommandNode<S>): List<LiteralArgumentBuilder<Src>> =
        (listOf(root.name) + root.aliases).map { literal(root, it, listOf(root)) }

    /** Builds every registered root of the manager. */
    public fun buildAll(): List<LiteralArgumentBuilder<Src>> =
        manager.commands.values.distinct().flatMap { build(it) }

    private fun literal(node: CommandNode<S>, name: String, path: List<CommandNode<S>>): LiteralArgumentBuilder<Src> {
        val literal = LiteralArgumentBuilder.literal<Src>(name)
        literal.requires(requirement(node))
        node.children.forEach { (childName, child) -> literal.then(literal(child, childName, path + child)) }
        if (node.executable) attachArguments(literal, node, path)
        return literal
    }

    private fun requirement(node: CommandNode<S>): Predicate<Src> {
        val permission = node.permission
        val requirements = node.guards.filterIsInstance<BrigadierRequirement<Src>>()
        return Predicate { source ->
            val permitted = permission == null || permissionResolver.hasPermission(senderFactory(source), permission)
            permitted && requirements.all { it.test(source) }
        }
    }

    private fun attachArguments(literal: LiteralArgumentBuilder<Src>, node: CommandNode<S>, path: List<CommandNode<S>>) {
        val positionals = node.arguments.filter { it.positional }
        val hasOptions = node.arguments.any { it.named || it.flag }
        val greedyTail = hasOptions && positionals.lastOrNull()?.greedy == true
        val optionsNode = if (hasOptions && !greedyTail) OPTIONS_NODE else null

        // a greedy tail that must also carry options is always handed to the core as raw tokens
        val bindings = positionals.map { arg ->
            val absorbsOptions = greedyTail && arg.greedy
            val mapping = if (absorbsOptions) null else types.mappingFor(arg.type)
            PositionalBinding(arg, mapping, tokenizeTail = absorbsOptions)
        }
        val command = PolyglotBrigadierCommand(manager, path, bindings, optionsNode, senderFactory, onError)

        var tail: ArgumentBuilder<Src, *>? = optionsNode?.let { name ->
            RequiredArgumentBuilder.argument<Src, String>(name, StringArgumentType.greedyString())
                .suggests(tailSuggestions(path, positionals.size))
                .executes(command)
        }
        var allFollowingOptional = true
        for ((index, binding) in bindings.withIndex().reversed()) {
            val builder = argumentNode(binding, path, index, greedyTail)
            if (allFollowingOptional) builder.executes(command)
            tail?.let { builder.then(it) }
            tail = builder
            allFollowingOptional = allFollowingOptional && binding.argument.optional
        }
        tail?.let { literal.then(it) }
        if (allFollowingOptional) literal.executes(command)
    }

    private fun argumentNode(
        binding: PositionalBinding<Src>,
        path: List<CommandNode<S>>,
        index: Int,
        greedyTail: Boolean,
    ): RequiredArgumentBuilder<Src, Any> {
        val arg = binding.argument
        val builder = RequiredArgumentBuilder.argument<Src, Any>(arg.name, argumentType(arg, binding.native))
        suggestionsFor(arg, binding.native, path, index, greedyTail)?.let { builder.suggests(it) }
        return builder
    }

    @Suppress("UNCHECKED_CAST")
    private fun argumentType(arg: CommandArgument, native: Boolean): ArgumentType<Any> {
        val mapped = if (native) types.mappingFor(arg.type)?.type?.invoke(arg) else null
        return (mapped ?: BuiltInBrigadierTypes.stringType(arg)) as ArgumentType<Any>
    }

    /**
     * Core suggestions apply to string-fallback nodes, explicit `@Suggestion` ids and `@Choice`
     * lists. Natively mapped types keep their own Brigadier suggestions unless an id is given.
     */
    private fun suggestionsFor(
        arg: CommandArgument,
        native: Boolean,
        path: List<CommandNode<S>>,
        index: Int,
        greedyTail: Boolean,
    ): SuggestionProvider<Src>? {
        if (greedyTail && arg.greedy) return tailSuggestions(path, index)
        val byId = arg.suggestionId?.let { manager.suggestions.forId(it) }
        val provider = byId ?: if (native) null else manager.suggestions.forType(arg.type)
        return when {
            provider != null -> BrigadierSuggestions.ofProvider(provider, arg.name, senderFactory)
            arg.choices.isNotEmpty() -> BrigadierSuggestions.ofChoices(arg.choices)
            else -> null
        }
    }

    /** Completes the last token of a multi-token tail through the core completer. */
    private fun tailSuggestions(path: List<CommandNode<S>>, precedingPositionals: Int): SuggestionProvider<Src> {
        val prefix = path.map { it.name } + List(precedingPositionals) { PLACEHOLDER }
        return BrigadierSuggestions.ofTail { source, tokens ->
            runBlocking { manager.complete(senderFactory(source), prefix + tokens) }
        }
    }

    private companion object {
        const val OPTIONS_NODE = "options"
        const val PLACEHOLDER = "_"
    }
}
