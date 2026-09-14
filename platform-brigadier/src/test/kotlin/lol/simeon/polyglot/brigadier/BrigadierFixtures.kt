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

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.suggestion.Suggestions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import lol.simeon.polyglot.CommandManager
import lol.simeon.polyglot.exception.PolyglotException
import lol.simeon.polyglot.model.CommandNode
import lol.simeon.polyglot.permission.PermissionResolver

/** Brigadier source used by the tests. */
class TestSource(val name: String = "tester", val permissions: MutableSet<String> = mutableSetOf(), val player: Boolean = true)

/** Sender wrapper (kept distinct from the source to exercise the sender factory). */
class TestSender(val source: TestSource)

/** Concrete manager for the tests, wiring the Brigadier tree builder like a platform would. */
class TestBrigadierManager : CommandManager<TestSender>() {
    val calls: MutableList<String> = mutableListOf()
    val errors: MutableList<PolyglotException> = mutableListOf()
    val dispatcher: CommandDispatcher<TestSource> = CommandDispatcher()
    val types: BrigadierTypeRegistry<TestSource> = BrigadierTypeRegistry<TestSource>().also { BuiltInBrigadierTypes.install(it) }

    override val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)

    override val permissionResolver: PermissionResolver<TestSender> =
        PermissionResolver { sender, node -> node in sender.source.permissions }

    private val builder = BrigadierTreeBuilder(
        manager = this,
        types = types,
        senderFactory = ::TestSender,
        permissionResolver = permissionResolver,
        onError = { _, error -> errors += error },
    )

    override fun registerNative(node: CommandNode<TestSender>) {
        builder.build(node).forEach { dispatcher.register(it) }
    }

    fun execute(input: String, source: TestSource = TestSource()): Int = dispatcher.execute(input, source)

    fun suggest(input: String, source: TestSource = TestSource()): List<String> {
        val parse = dispatcher.parse(input, source)
        val suggestions: Suggestions = dispatcher.getCompletionSuggestions(parse).join()
        return suggestions.list.map { it.text }
    }
}
