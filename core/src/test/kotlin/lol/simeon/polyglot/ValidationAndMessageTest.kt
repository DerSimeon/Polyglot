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
import lol.simeon.polyglot.argument.ArgumentValidator
import lol.simeon.polyglot.dsl.command
import lol.simeon.polyglot.exception.ArgumentParseException
import lol.simeon.polyglot.exception.UnknownCommandException
import lol.simeon.polyglot.message.ResourceBundleMessageProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.ListResourceBundle

class ValidationAndMessageTest {

    private class TestBundle : ListResourceBundle() {
        override fun getContents(): Array<Array<Any>> = arrayOf(
            arrayOf("polyglot.unknownCommand", "No such command: {0}"),
            arrayOf("polyglot.invalidArgument", "Bad {1}: '{0}' is not a {2}"),
        )
    }

    @Test
    fun `dsl range rejects out-of-bounds values`() = runTest {
        val manager = TestCommandManager()
        manager.register(
            command<TestSender>("volume") {
                argument<Int>("level", range = 0.0..100.0)
                executes { }
            },
        )
        assertThrows<ArgumentParseException> {
            manager.dispatch(TestSender(), listOf("volume", "250"))
        }
    }

    @Test
    fun `custom validator rejects with its message`() = runTest {
        val evenOnly = ArgumentValidator { value ->
            if ((value as Int) % 2 == 0) null else "must be even"
        }
        val manager = TestCommandManager()
        manager.register(
            command<TestSender>("pick") {
                argument<Int>("n", validators = listOf(evenOnly))
                executes { }
            },
        )
        val error = assertThrows<ArgumentParseException> {
            manager.dispatch(TestSender(), listOf("pick", "3"))
        }
        assertEquals("must be even", error.reason)
    }

    @Test
    fun `resource bundle provider formats localized templates`() {
        val provider = ResourceBundleMessageProvider<TestSender>(TestBundle())
        val message = provider.format(TestSender(), UnknownCommandException("teleport"))
        assertEquals("No such command: teleport", message)
    }

    @Test
    fun `resource bundle provider falls back to exception message`() {
        val provider = ResourceBundleMessageProvider<TestSender>(TestBundle())
        val message = provider.format(TestSender(), ArgumentParseException("count", "Int", "abc"))
        assertEquals("Bad count: 'abc' is not a Int", message)
    }
}
