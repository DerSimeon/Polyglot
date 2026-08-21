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
import lol.simeon.polyglot.exception.MissingArgumentException
import lol.simeon.polyglot.exception.NoPermissionException
import lol.simeon.polyglot.exception.UnknownCommandException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CommandManagerTest {
    private lateinit var manager: TestCommandManager

    @BeforeEach
    fun setUp() {
        CommandFixtures.calls.clear()
        manager = TestCommandManager()
        manager.register(CommandFixtures.Party())
    }

    @Test
    fun `registers root natively`() {
        assertEquals(1, manager.registeredRoots.size)
        assertTrue(manager.resolveRoot("party") != null)
    }

    @Test
    fun `invokes default group handler`() = runTest {
        manager.dispatch(TestSender("bob"), listOf("party"))
        assertEquals(listOf("root:bob"), CommandFixtures.calls)
    }

    @Test
    fun `binds required and optional arguments`() = runTest {
        manager.dispatch(TestSender(), listOf("party", "create", "Alpha"))
        manager.dispatch(TestSender(), listOf("party", "create", "Beta", "5"))
        assertEquals(listOf("create:Alpha:null", "create:Beta:5"), CommandFixtures.calls)
    }

    @Test
    fun `resolves subcommand aliases`() = runTest {
        manager.dispatch(TestSender(), listOf("party", "new", "Gamma"))
        assertEquals(listOf("create:Gamma:null"), CommandFixtures.calls)
    }

    @Test
    fun `parses enums and enforces permission`() = runTest {
        val allowed = TestSender(permissions = mutableSetOf("party.color"))
        manager.dispatch(allowed, listOf("party", "color", "green"))
        assertEquals(listOf("color:GREEN"), CommandFixtures.calls)
    }

    @Test
    fun `denies command without permission`() = runTest {
        assertThrows<NoPermissionException> {
            manager.dispatch(TestSender(), listOf("party", "color", "RED"))
        }
    }

    @Test
    fun `routes sub-sub-command with greedy argument`() = runTest {
        manager.dispatch(TestSender(), listOf("party", "admin", "kick", "too", "many", "trees"))
        assertEquals(listOf("kick:too many trees"), CommandFixtures.calls)
    }

    @Test
    fun `throws on unknown root`() = runTest {
        assertThrows<UnknownCommandException> {
            manager.dispatch(TestSender(), listOf("nope"))
        }
    }

    @Test
    fun `throws on missing required argument`() = runTest {
        assertThrows<MissingArgumentException> {
            manager.dispatch(TestSender(), listOf("party", "create"))
        }
    }

    @Test
    fun `dispatchCatching wraps failures`() = runTest {
        val result = manager.dispatchCatching(TestSender(), listOf("party", "create"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MissingArgumentException)
    }
}
