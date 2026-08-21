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

package lol.simeon.polyglot.scanner

import lol.simeon.polyglot.annotation.Argument
import lol.simeon.polyglot.annotation.Choice
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.annotation.Default
import lol.simeon.polyglot.annotation.Flag
import lol.simeon.polyglot.annotation.Greedy
import lol.simeon.polyglot.annotation.Named
import lol.simeon.polyglot.annotation.Optional
import lol.simeon.polyglot.annotation.Permission
import lol.simeon.polyglot.annotation.Range
import lol.simeon.polyglot.annotation.Suggestion
import lol.simeon.polyglot.context.CommandContext
import lol.simeon.polyglot.guard.CommandGuard
import lol.simeon.polyglot.model.CommandArgument
import lol.simeon.polyglot.model.CommandHandler
import lol.simeon.polyglot.model.CommandNode
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.valueParameters

/**
 * Builds a [CommandNode] tree from an annotated instance using runtime reflection. Member functions
 * annotated with [Command] become leaf subcommands, a [Default]-annotated function becomes the
 * group's own handler, and nested [Command] classes become subgroups (recursively, for
 * sub-sub-commands).
 *
 * Convention: the first value parameter of a handler function receives the sender (or the whole
 * [CommandContext] if typed as such); the remaining parameters are the command's arguments.
 */
public class AnnotationScanner<S>(
    private val guardContributors: List<GuardContributor<S>> = emptyList(),
) {

    public fun scan(handler: Any): CommandNode<S> {
        val kClass = handler::class
        val command = kClass.findAnnotation<Command>()
            ?: error("${kClass.simpleName} is not annotated with @Command")
        return buildNode(handler, kClass, command)
    }

    private fun buildNode(instance: Any, kClass: KClass<*>, command: Command): CommandNode<S> {
        val children = LinkedHashMap<String, CommandNode<S>>()
        var groupHandler: CommandHandler<S>? = null
        var groupArguments: List<CommandArgument> = emptyList()

        for (function in kClass.declaredMemberFunctions) {
            val leafCommand = function.findAnnotation<Command>()
            when {
                leafCommand != null -> putChild(children, buildLeaf(instance, function, leafCommand))
                function.hasAnnotation<Default>() -> {
                    val (arguments, handler) = buildFunction(instance, function)
                    groupArguments = arguments
                    groupHandler = handler
                }
            }
        }

        for (nested in kClass.nestedClasses) {
            val nestedCommand = nested.findAnnotation<Command>() ?: continue
            putChild(children, buildNode(instantiate(nested), nested, nestedCommand))
        }

        return CommandNode(
            name = command.name,
            aliases = command.aliases.toList(),
            description = command.description,
            permission = kClass.findAnnotation<Permission>()?.node,
            arguments = groupArguments,
            handler = groupHandler,
            children = children,
            guards = guardsFor(kClass),
        )
    }

    private fun buildLeaf(instance: Any, function: KFunction<*>, command: Command): CommandNode<S> {
        val (arguments, handler) = buildFunction(instance, function)
        return CommandNode(
            name = command.name,
            aliases = command.aliases.toList(),
            description = command.description,
            permission = function.findAnnotation<Permission>()?.node,
            arguments = arguments,
            handler = handler,
            children = emptyMap(),
            guards = guardsFor(function),
        )
    }

    private fun guardsFor(element: KAnnotatedElement): List<CommandGuard<S>> =
        guardContributors.flatMap { it.contribute(element) }

    private fun buildFunction(instance: Any, function: KFunction<*>): Pair<List<CommandArgument>, CommandHandler<S>> {
        val valueParameters = function.valueParameters
        val receiver = valueParameters.firstOrNull()
        val injectContext = receiver?.type?.classifier == CommandContext::class
        val argumentParams = if (receiver != null) valueParameters.drop(1) else emptyList()

        val arguments = argumentParams.map(::toArgument)
        val handler = ReflectiveCommandHandler<S>(
            instance = instance,
            function = function,
            receiverParameter = receiver,
            injectContext = injectContext,
            argumentParameters = argumentParams.associateBy(::argumentName),
        )
        return arguments to handler
    }

    private fun toArgument(parameter: KParameter): CommandArgument {
        val type = parameter.type.classifier as? KClass<*>
            ?: error("Unsupported argument type for '${parameter.name}'")
        val rangeAnnotation = parameter.findAnnotation<Range>()
        val range = rangeAnnotation
            ?.takeUnless { it.min == Double.NEGATIVE_INFINITY && it.max == Double.POSITIVE_INFINITY }
            ?.let { it.min..it.max }

        val named = parameter.findAnnotation<Named>()
        val flag = parameter.findAnnotation<Flag>()
        val optional = parameter.hasAnnotation<Optional>() ||
            parameter.isOptional ||
            parameter.type.isMarkedNullable ||
            flag != null

        return CommandArgument(
            name = flag?.name ?: named?.name ?: argumentName(parameter),
            type = type,
            description = parameter.findAnnotation<Argument>()?.description.orEmpty(),
            optional = optional,
            greedy = parameter.hasAnnotation<Greedy>(),
            suggestionId = parameter.findAnnotation<Suggestion>()?.id,
            choices = parameter.findAnnotation<Choice>()?.values?.toList().orEmpty(),
            range = range,
            named = named != null,
            shorthand = (named?.shorthand ?: flag?.shorthand)?.takeUnless { it == ' ' },
            flag = flag != null,
        )
    }

    private fun argumentName(parameter: KParameter): String =
        parameter.findAnnotation<Argument>()?.name
            ?: parameter.name
            ?: error("Argument name unavailable; compile with -java-parameters")

    private fun putChild(children: MutableMap<String, CommandNode<S>>, node: CommandNode<S>) {
        children[node.name.lowercase()] = node
        node.aliases.forEach { children[it.lowercase()] = node }
    }

    private fun instantiate(kClass: KClass<*>): Any =
        kClass.objectInstance ?: kClass.createInstance()
}
