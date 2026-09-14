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

import kotlinx.coroutines.test.runTest
import lol.simeon.polyglot.annotation.Choice
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.annotation.Flag
import lol.simeon.polyglot.annotation.Greedy
import lol.simeon.polyglot.annotation.Named
import lol.simeon.polyglot.annotation.Optional
import lol.simeon.polyglot.annotation.Permission
import lol.simeon.polyglot.annotation.Range
import lol.simeon.polyglot.exception.ArgumentParseException
import lol.simeon.polyglot.exception.CommandExecutionException
import lol.simeon.polyglot.exception.MissingArgumentException
import lol.simeon.polyglot.exception.NoPermissionException
import lol.simeon.polyglot.exception.UnknownCommandException
import lol.simeon.polyglot.execution.ArgumentInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResolvedExecutionTest {

    @Command(name = "res")
    class ResolvedCommand {
        @Command(name = "typed")
        fun typed(sender: TestSender, @Range(min = 1.0, max = 10.0) amount: Int, @Optional label: String?) {
            CommandFixtures.calls += "typed:$amount:$label"
        }

        @Command(name = "pick")
        fun pick(sender: TestSender, @Choice(["red", "blue"]) colour: String) {
            CommandFixtures.calls += "pick:$colour"
        }

        @Command(name = "run")
        fun run(
            sender: TestSender,
            service: String,
            @Named("count", shorthand = 'c') count: Int,
            @Flag("dry") dry: Boolean,
        ) {
            CommandFixtures.calls += "run:$service:$count:$dry"
        }

        @Command(name = "say")
        fun say(sender: TestSender, @Greedy text: String?, @Flag("loud") loud: Boolean) {
            CommandFixtures.calls += "say:$text:$loud"
        }

        @Command(name = "secret")
        @Permission("res.secret")
        fun secret(sender: TestSender) {
            CommandFixtures.calls += "secret"
        }

        @Command(name = "boom")
        fun boom(sender: TestSender) {
            error("kaboom")
        }

        @Command(name = "group")
        class Group
    }

    private lateinit var manager: TestCommandManager

    @BeforeEach
    fun setUp() {
        CommandFixtures.calls.clear()
        manager = TestCommandManager()
        manager.register(ResolvedCommand())
    }

    private fun path(vararg names: String) = names.fold(emptyList<lol.simeon.polyglot.model.CommandNode<TestSender>>()) { acc, n ->
        acc + (if (acc.isEmpty()) manager.resolveRoot(n)!! else acc.last().child(n)!!)
    }

    @Test
    fun `binds resolved, raw and absent inputs`() = runTest {
        manager.dispatchResolved(TestSender(), path("res", "typed"), mapOf("amount" to ArgumentInput.Resolved(5)))
        manager.dispatchResolved(
            TestSender(),
            path("res", "typed"),
            mapOf("amount" to ArgumentInput.Raw("7"), "label" to ArgumentInput.Resolved("x")),
        )
        assertEquals(listOf("typed:5:null", "typed:7:x"), CommandFixtures.calls)
    }

    @Test
    fun `validates range and choices on resolved values`() = runTest {
        assertThrows<ArgumentParseException> {
            manager.dispatchResolved(TestSender(), path("res", "typed"), mapOf("amount" to ArgumentInput.Resolved(42)))
        }
        assertThrows<ArgumentParseException> {
            manager.dispatchResolved(TestSender(), path("res", "pick"), mapOf("colour" to ArgumentInput.Resolved("green")))
        }
        manager.dispatchResolved(TestSender(), path("res", "pick"), mapOf("colour" to ArgumentInput.Resolved("RED")))
        assertEquals(listOf("pick:RED"), CommandFixtures.calls)
    }

    @Test
    fun `missing required positional throws`() = runTest {
        assertThrows<MissingArgumentException> {
            manager.dispatchResolved(TestSender(), path("res", "typed"), emptyMap())
        }
    }

    @Test
    fun `parses named options and flags from the option tail`() = runTest {
        manager.dispatchResolved(
            TestSender(),
            path("res", "run"),
            mapOf("service" to ArgumentInput.Resolved("api")),
            optionTokens = listOf("--dry", "-c", "3"),
        )
        assertEquals(listOf("run:api:3:true"), CommandFixtures.calls)
    }

    @Test
    fun `extracts options from a raw greedy tail`() = runTest {
        manager.dispatchResolved(
            TestSender(),
            path("res", "say"),
            mapOf("text" to ArgumentInput.Raw(listOf("hello", "--loud", "world"))),
        )
        manager.dispatchResolved(TestSender(), path("res", "say"), mapOf("text" to ArgumentInput.Raw(listOf("--loud"))))
        manager.dispatchResolved(TestSender(), path("res", "say"), mapOf("text" to ArgumentInput.Resolved("plain")))
        assertEquals(listOf("say:hello world:true", "say:null:true", "say:plain:false"), CommandFixtures.calls)
    }

    @Test
    fun `rejects unknown tokens in the option tail`() = runTest {
        assertThrows<ArgumentParseException> {
            manager.dispatchResolved(
                TestSender(),
                path("res", "run"),
                mapOf("service" to ArgumentInput.Resolved("api")),
                optionTokens = listOf("stray", "-c", "3"),
            )
        }
        assertThrows<ArgumentParseException> {
            manager.dispatchResolved(
                TestSender(),
                path("res", "pick"),
                mapOf("colour" to ArgumentInput.Resolved("red")),
                optionTokens = listOf("--nope"),
            )
        }
    }

    @Test
    fun `authorizes every node on the path`() = runTest {
        assertThrows<NoPermissionException> {
            manager.dispatchResolved(TestSender(), path("res", "secret"), emptyMap())
        }
        manager.dispatchResolved(TestSender(permissions = mutableSetOf("res.secret")), path("res", "secret"), emptyMap())
        assertEquals(listOf("secret"), CommandFixtures.calls)
    }

    @Test
    fun `non executable leaf and handler failures map to core exceptions`() = runTest {
        assertThrows<UnknownCommandException> {
            manager.dispatchResolved(TestSender(), path("res", "group"), emptyMap())
        }
        assertThrows<CommandExecutionException> {
            manager.dispatchResolved(TestSender(), path("res", "boom"), emptyMap())
        }
        assertThrows<IllegalArgumentException> {
            manager.dispatchResolved(TestSender(), emptyList(), emptyMap())
        }
    }
}
