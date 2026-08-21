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
    @Suppress("ThrowsCount", "TooGenericExceptionCaught")
    public suspend fun execute(root: CommandNode<S>, sender: S, args: List<String>) {
        val queue = ArgumentQueue(args)
        val path = mutableListOf(root.name)
        var node = root
        checkPermission(node, sender)

        while (queue.hasNext()) {
            val child = node.child(queue.peek()!!) ?: break
            queue.next()
            node = child
            path += node.name
            checkPermission(node, sender)
        }

        val handler = node.handler ?: throw UnknownCommandException(path.joinToString(" "))
        val parsed = parseArguments(node.arguments, sender, queue)
        val context = CommandContext(sender, path.joinToString(" "), parsed, args)

        try {
            handler.handle(context)
        } catch (ex: PolyglotException) {
            throw ex
        } catch (ex: Throwable) {
            throw CommandExecutionException(path.joinToString(" "), ex)
        }
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
        val byLong = options.associateBy { it.name.lowercase() }
        val byShort = options.mapNotNull { arg -> arg.shorthand?.let { it to arg } }.toMap()

        val positionalTokens = mutableListOf<String>()
        val namedValues = HashMap<String, String>()
        val setFlags = HashSet<String>()

        while (queue.hasNext()) {
            val token = queue.next()
            val option = matchOption(token, byLong, byShort)
            when {
                option == null -> positionalTokens += token
                option.flag -> setFlags += option.name
                !queue.hasNext() -> throw MissingArgumentException(option.name)
                else -> namedValues[option.name] = queue.next()
            }
        }

        val result = LinkedHashMap<String, Any?>()
        val positionalQueue = ArgumentQueue(positionalTokens)
        for (arg in arguments.filter { it.positional }) {
            result[arg.name] = bindArgument(arg, sender, positionalQueue)
        }
        for (arg in arguments.filter { it.named }) {
            result[arg.name] = bindNamed(arg, sender, namedValues[arg.name])
        }
        for (arg in arguments.filter { it.flag }) {
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
        if (arg.choices.isNotEmpty() && arg.choices.none { it.equals(token, ignoreCase = true) }) {
            throw ArgumentParseException(arg.name, "choice", token, "must be one of ${arg.choices}")
        }
        val parser = parsers.parserFor(arg.type)
            ?: throw ArgumentParseException(arg.name, arg.type.simpleName ?: "?", token, "no parser registered")
        val value = parser.parse(ParseContext(sender, arg.name, queue))
        validateRange(arg, value)
        validateCustom(arg, value)
        return value
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
