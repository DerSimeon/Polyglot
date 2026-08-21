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
import lol.simeon.polyglot.dsl.command
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DslAndCompletionTest {
    private lateinit var manager: TestCommandManager

    @BeforeEach
    fun setUp() {
        CommandFixtures.calls.clear()
        manager = TestCommandManager()
        manager.register(CommandFixtures.Party())
    }

    @Test
    fun `dsl produces an executable command tree`() = runTest {
        manager.register(
            command<TestSender>("math") {
                description("Arithmetic")
                subcommand("add") {
                    argument<Int>("a")
                    argument<Int>("b")
                    executes { ctx -> CommandFixtures.calls += "sum:${ctx.get<Int>("a") + ctx.get<Int>("b")}" }
                }
            },
        )
        manager.dispatch(TestSender(), listOf("math", "add", "2", "3"))
        assertEquals(listOf("sum:5"), CommandFixtures.calls)
    }

    @Test
    fun `completes subcommand names filtered by permission`() = runTest {
        val names = manager.complete(TestSender(), listOf("party", "")).map { it.value }
        assertTrue("create" in names)
        assertTrue("admin" in names)
        // color requires a permission the sender lacks -> excluded from completions
        assertFalse("color" in names)
    }

    @Test
    fun `completes enum argument values`() = runTest {
        val allowed = TestSender(permissions = mutableSetOf("party.color"))
        val values = manager.complete(allowed, listOf("party", "color", "g")).map { it.value }
        assertEquals(listOf("GREEN"), values)
    }
}
