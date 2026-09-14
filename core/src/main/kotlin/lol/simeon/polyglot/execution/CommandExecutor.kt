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

import lol.simeon.polyglot.argument.ArgumentQueue
import lol.simeon.polyglot.argument.ParseContext
import lol.simeon.polyglot.argument.ParserRegistry
import lol.simeon.polyglot.context.CommandContext
import lol.simeon.polyglot.exception.ArgumentParseException
import lol.simeon.polyglot.exception.CommandExecutionException
import lol.simeon.polyglot.exception.MissingArgumentException
import lol.simeon.polyglot.exception.NoPermissionException
import lol.simeon.polyglot.exception.PolyglotException
import lol.simeon.polyglot.exception.UnknownCommandException
import lol.simeon.polyglot.model.CommandArgument
import lol.simeon.polyglot.model.CommandHandler
import lol.simeon.polyglot.model.CommandNode
import lol.simeon.polyglot.permission.PermissionResolver

/**
 * Resolves a command path, enforces permissions, parses arguments and invokes the matched handler.
 * Stateless and reusable across dispatches.
 */
public class CommandExecutor<S>(
    private val parsers: ParserRegistry<S>,
    private val permissions: PermissionResolver<S>,
) {
    /**
     * @param root the already-resolved root command node
     * @param args the tokens following the root command name
     */
    public suspend fun execute(root: CommandNode<S>, sender: S, args: List<String>) {
        val queue = ArgumentQueue(args)
        val path = mutableListOf(root)
        authorize(root, sender)

        while (queue.hasNext()) {
            val child = path.last().child(queue.peek()!!) ?: break
            queue.next()
            path += child
            authorize(child, sender)
        }

        val node = path.last()
        val handler = node.handler ?: throw UnknownCommandException(pathOf(path))
        val parsed = parseArguments(node.arguments, sender, queue)
        invoke(handler, CommandContext(sender, pathOf(path), parsed, args))
    }

    /**
     * Executes the leaf at the end of [path] (root first) when the platform has already walked the
     * command tree and parsed some or all positional arguments natively. Each node on [path] is
     * authorized in order. [inputs] maps positional argument names to how they were supplied;
     * arguments missing from the map are treated as [ArgumentInput.Absent]. Named options and
     * flags, if the leaf declares any, are parsed from [optionTokens] using the usual
     * `--name value` / `-x value` / `--flag` syntax. When the leaf ends with a greedy argument
     * supplied as [ArgumentInput.Raw] tokens, options are also extracted from those tokens (so a
     * platform may hand over one undivided tail).
     */
    public suspend fun executeResolved(
        path: List<CommandNode<S>>,
        sender: S,
        inputs: Map<String, ArgumentInput>,
        optionTokens: List<String> = emptyList(),
        rawInput: List<String> = emptyList(),
    ) {
        require(path.isNotEmpty()) { "path must contain at least the root node" }
        for (node in path) authorize(node, sender)

        val node = path.last()
        val handler = node.handler ?: throw UnknownCommandException(pathOf(path))
        val positionals = node.arguments.filter { it.positional }
        val options = node.arguments.filter { it.named || it.flag }

        val effectiveInputs = inputs.toMutableMap()
        val optionValues: Map<String, Any?>
        val greedy = positionals.lastOrNull()?.takeIf { it.greedy }
        val greedyTokens = greedy?.let { (inputs[it.name] as? ArgumentInput.Raw)?.tokens }
        if (options.isNotEmpty() && greedyTokens != null) {
            val rest = mutableListOf<String>()
            optionValues = parseOptions(options, sender, ArgumentQueue(greedyTokens + optionTokens), strict = false, rest)
            effectiveInputs[greedy.name] = if (rest.isEmpty()) ArgumentInput.Absent else ArgumentInput.Raw(rest)
        } else if (options.isNotEmpty()) {
            optionValues = parseOptions(options, sender, ArgumentQueue(optionTokens), strict = true)
        } else {
            if (optionTokens.isNotEmpty()) {
                throw ArgumentParseException("options", "option", optionTokens.first(), "unexpected input")
            }
            optionValues = emptyMap()
        }

        val result = LinkedHashMap<String, Any?>()
        for (arg in positionals) {
            result[arg.name] = bindInput(arg, sender, effectiveInputs[arg.name] ?: ArgumentInput.Absent)
        }
        result += optionValues
        invoke(handler, CommandContext(sender, pathOf(path), result, rawInput))
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun invoke(handler: CommandHandler<S>, context: CommandContext<S>) {
        try {
            handler.handle(context)
        } catch (ex: PolyglotException) {
            throw ex
        } catch (ex: Throwable) {
            throw CommandExecutionException(context.commandPath, ex)
        }
    }

    private fun pathOf(path: List<CommandNode<S>>): String = path.joinToString(" ") { it.name }

    private suspend fun bindInput(arg: CommandArgument, sender: S, input: ArgumentInput): Any? = when (input) {
        is ArgumentInput.Absent -> if (arg.optional) null else throw MissingArgumentException(arg.name)
        is ArgumentInput.Raw -> bindArgument(arg, sender, ArgumentQueue(input.tokens))
        is ArgumentInput.Resolved -> {
            val value = input.value
            if (value != null) checkChoice(arg, value.toString())
            validateRange(arg, value)
            validateCustom(arg, value)
            value
        }
    }

    /** Enforces the node's string permission, then its runtime guards, before it may run. */
    private suspend fun authorize(node: CommandNode<S>, sender: S) {
        checkPermission(node, sender)
        for (guard in node.guards) guard.check(sender)
    }

    private fun checkPermission(node: CommandNode<S>, sender: S) {
        val node0 = node.permission ?: return
        if (!permissions.hasPermission(sender, node0)) throw NoPermissionException(node0)
    }

    private suspend fun parseArguments(
        arguments: List<CommandArgument>,
        sender: S,
        queue: ArgumentQueue,
    ): Map<String, Any?> {
        if (arguments.none { it.named || it.flag }) {
            val result = LinkedHashMap<String, Any?>()
            for (arg in arguments) {
                result[arg.name] = bindArgument(arg, sender, queue)
            }
            return result
        }
        return parseMixed(arguments, sender, queue)
    }

    private suspend fun parseMixed(
        arguments: List<CommandArgument>,
        sender: S,
        queue: ArgumentQueue,
    ): Map<String, Any?> {
        val options = arguments.filter { it.named || it.flag }
        val positionalTokens = mutableListOf<String>()
        val optionValues = parseOptions(options, sender, queue, strict = false, positionalSink = positionalTokens)

        val result = LinkedHashMap<String, Any?>()
        val positionalQueue = ArgumentQueue(positionalTokens)
        for (arg in arguments.filter { it.positional }) {
            result[arg.name] = bindArgument(arg, sender, positionalQueue)
        }
        result += optionValues
        return result
    }

    /**
     * Consumes `--name value`, `-x value` and `--flag` tokens for [options]. Tokens that match no
     * option are appended to [positionalSink] when [strict] is false, or rejected when true.
     */
    private suspend fun parseOptions(
        options: List<CommandArgument>,
        sender: S,
        queue: ArgumentQueue,
        strict: Boolean,
        positionalSink: MutableList<String> = mutableListOf(),
    ): Map<String, Any?> {
        val byLong = options.associateBy { it.name.lowercase() }
        val byShort = options.mapNotNull { arg -> arg.shorthand?.let { it to arg } }.toMap()
        val namedValues = HashMap<String, String>()
        val setFlags = HashSet<String>()

        while (queue.hasNext()) {
            val token = queue.next()
            val option = matchOption(token, byLong, byShort)
            when {
                option == null && strict -> throw ArgumentParseException("options", "option", token, "unknown option")
                option == null -> positionalSink += token
                option.flag -> setFlags += option.name
                !queue.hasNext() -> throw MissingArgumentException(option.name)
                else -> namedValues[option.name] = queue.next()
            }
        }

        val result = LinkedHashMap<String, Any?>()
        for (arg in options.filter { it.named }) {
            result[arg.name] = bindNamed(arg, sender, namedValues[arg.name])
        }
        for (arg in options.filter { it.flag }) {
            result[arg.name] = arg.name in setFlags
        }
        return result
    }

    private fun matchOption(
        token: String,
        byLong: Map<String, CommandArgument>,
        byShort: Map<Char, CommandArgument>,
    ): CommandArgument? = when {
        token.startsWith("--") && token.length > 2 -> byLong[token.substring(2).lowercase()]
        token.length == 2 && token[0] == '-' && token[1].isLetter() -> byShort[token[1]]
        else -> null
    }

    private suspend fun bindNamed(arg: CommandArgument, sender: S, raw: String?): Any? {
        if (raw == null) {
            if (arg.optional) return null
            throw MissingArgumentException(arg.name)
        }
        return parseSingle(arg, sender, ArgumentQueue(listOf(raw)))
    }

    private suspend fun bindArgument(arg: CommandArgument, sender: S, queue: ArgumentQueue): Any? {
        if (!queue.hasNext()) {
            if (arg.optional) return null
            throw MissingArgumentException(arg.name)
        }
        if (arg.greedy) return queue.consumeRemaining()
        return parseSingle(arg, sender, queue)
    }

    private suspend fun parseSingle(arg: CommandArgument, sender: S, queue: ArgumentQueue): Any? {
        val token = queue.peek()!!
        checkChoice(arg, token)
        val parser = parsers.parserFor(arg.type)
            ?: throw ArgumentParseException(arg.name, arg.type.simpleName ?: "?", token, "no parser registered")
        val value = parser.parse(ParseContext(sender, arg.name, queue))
        validateRange(arg, value)
        validateCustom(arg, value)
        return value
    }

    private fun checkChoice(arg: CommandArgument, token: String) {
        if (arg.choices.isNotEmpty() && arg.choices.none { it.equals(token, ignoreCase = true) }) {
            throw ArgumentParseException(arg.name, "choice", token, "must be one of ${arg.choices}")
        }
    }

    private fun validateRange(arg: CommandArgument, value: Any?) {
        val range = arg.range ?: return
        val number = (value as? Number)?.toDouble() ?: return
        if (number !in range) {
            throw ArgumentParseException(arg.name, "range", value.toString(), "must be within $range")
        }
    }

    private fun validateCustom(arg: CommandArgument, value: Any?) {
        for (validator in arg.validators) {
            val reason = validator.validate(value)
            if (reason != null) {
                throw ArgumentParseException(arg.name, "validation", value.toString(), reason)
            }
        }
    }
}
