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

package lol.simeon.polyglot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import lol.simeon.polyglot.argument.BuiltInParsers
import lol.simeon.polyglot.argument.ParserRegistry
import lol.simeon.polyglot.dsl.CommandBuilder
import lol.simeon.polyglot.dsl.command
import lol.simeon.polyglot.exception.PolyglotException
import lol.simeon.polyglot.exception.UnknownCommandException
import lol.simeon.polyglot.execution.CommandCompleter
import lol.simeon.polyglot.execution.CommandExecutor
import lol.simeon.polyglot.message.DefaultMessageProvider
import lol.simeon.polyglot.message.MessageProvider
import lol.simeon.polyglot.model.CommandNode
import lol.simeon.polyglot.permission.PermissionResolver
import lol.simeon.polyglot.scanner.AnnotationScanner
import lol.simeon.polyglot.scanner.GuardContributor
import lol.simeon.polyglot.suggestion.Suggestion
import lol.simeon.polyglot.suggestion.SuggestionRegistry

/**
 * Platform-agnostic command engine. Each platform subclasses this, wires a [coroutineScope],
 * [permissionResolver] and [messageProvider], and implements [registerNative] to publish commands
 * to the underlying platform (Brigadier, JDA, CLI, …).
 *
 * @param S the platform sender type.
 */
public abstract class CommandManager<S> {

    /** Registry of argument parsers; pre-populated with primitives + enum support. Consumer-extensible. */
    public val parsers: ParserRegistry<S> = ParserRegistry<S>().also { BuiltInParsers.install(it) }

    /** Registry of suggestion providers (by id and by type). Consumer-extensible. */
    public val suggestions: SuggestionRegistry<S> = SuggestionRegistry()

    /** Permission check; defaults to allow-all. Override per platform. */
    protected open val permissionResolver: PermissionResolver<S> = PermissionResolver { _, _ -> true }

    /** Error formatter; defaults to the exception message. Override to localize. */
    protected open val messageProvider: MessageProvider<S> = DefaultMessageProvider()

    /** Contributors translating platform annotations into runtime guards. Override per platform. */
    protected open val guardContributors: List<GuardContributor<S>> = emptyList()

    /** Scope commands are dispatched in when using [launchDispatch]. */
    protected abstract val coroutineScope: CoroutineScope

    private val scanner: AnnotationScanner<S> by lazy { AnnotationScanner(guardContributors) }
    private val roots = LinkedHashMap<String, CommandNode<S>>()
    private val executor: CommandExecutor<S> by lazy { CommandExecutor(parsers, permissionResolver) }
    private val completer: CommandCompleter<S> by lazy { CommandCompleter(suggestions, permissionResolver) }

    /** All registered root commands, keyed by lower-cased name and alias. */
    public val commands: Map<String, CommandNode<S>> get() = roots

    /** Registers an annotated command instance. */
    public fun register(handler: Any) {
        registerNode(scanner.scan(handler))
    }

    /** Registers a pre-built command node (e.g. from the DSL). */
    public fun register(node: CommandNode<S>) {
        registerNode(node)
    }

    /** Registers a command defined inline via the DSL. */
    public fun register(name: String, block: CommandBuilder<S>.() -> Unit) {
        registerNode(command(name, block))
    }

    /** Resolves a registered root command by name or alias. */
    public fun resolveRoot(name: String): CommandNode<S>? = roots[name.lowercase()]

    /** Parses and executes [input] (command name followed by its arguments). Throws [PolyglotException] on failure. */
    public suspend fun dispatch(sender: S, input: List<String>) {
        if (input.isEmpty()) return
        val root = resolveRoot(input.first()) ?: throw UnknownCommandException(input.first())
        executor.execute(root, sender, input.drop(1))
    }

    /** Like [dispatch] but returns the failure instead of throwing. */
    public suspend fun dispatchCatching(sender: S, input: List<String>): Result<Unit> =
        runCatching { dispatch(sender, input) }

    /** Computes completions for a partially-typed [input]. */
    public suspend fun complete(sender: S, input: List<String>): List<Suggestion> {
        if (input.isEmpty()) return emptyList()
        val root = resolveRoot(input.first()) ?: return emptyList()
        return completer.complete(root, sender, input.drop(1))
    }

    /** Dispatches on [coroutineScope], routing any [PolyglotException] to [onError]. */
    public fun launchDispatch(sender: S, input: List<String>): Job = coroutineScope.launch {
        try {
            dispatch(sender, input)
        } catch (error: PolyglotException) {
            onError(sender, error)
        }
    }

    /** Formats [error] into a sender-facing string. */
    protected fun formatError(sender: S, error: PolyglotException): String =
        messageProvider.format(sender, error)

    /** Hook invoked when [launchDispatch] catches a command error. Default: no-op. */
    protected open fun onError(sender: S, error: PolyglotException) {
        // platforms override to reply to the sender
    }

    private fun registerNode(node: CommandNode<S>) {
        roots[node.name.lowercase()] = node
        node.aliases.forEach { roots[it.lowercase()] = node }
        registerNative(node)
    }

    /** Publishes [node] to the underlying platform. */
    protected abstract fun registerNative(node: CommandNode<S>)
}
