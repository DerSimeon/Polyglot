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
import lol.simeon.polyglot.argument.ArgumentQueue
import lol.simeon.polyglot.argument.BuiltInParsers
import lol.simeon.polyglot.argument.ParseContext
import lol.simeon.polyglot.argument.ParserRegistry
import lol.simeon.polyglot.exception.ArgumentParseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KClass

class ArgumentParsersTest {
    private val registry = ParserRegistry<Unit>().also { BuiltInParsers.install(it) }

    private suspend fun parse(type: KClass<*>, token: String): Any? =
        registry.parserFor(type)!!.parse(ParseContext(Unit, "x", ArgumentQueue(listOf(token))))

    @Test
    fun `parses primitives`() = runTest {
        assertEquals("hi", parse(String::class, "hi"))
        assertEquals('z', parse(Char::class, "z"))
        assertEquals(42.toByte(), parse(Byte::class, "42"))
        assertEquals(7.toShort(), parse(Short::class, "7"))
        assertEquals(1234L, parse(Long::class, "1234"))
        assertEquals(3.5f, parse(Float::class, "3.5"))
        assertEquals(2.5, parse(Double::class, "2.5"))
    }

    @Test
    fun `parses boolean synonyms`() = runTest {
        assertTrue(parse(Boolean::class, "yes") as Boolean)
        assertTrue(parse(Boolean::class, "on") as Boolean)
        assertFalse(parse(Boolean::class, "0") as Boolean)
    }

    @Test
    fun `resolves and parses enums generically`() = runTest {
        assertEquals(CommandFixtures.Color.BLUE, parse(CommandFixtures.Color::class, "blue"))
    }

    @Test
    fun `invalid input raises ArgumentParseException`() = runTest {
        assertThrows<ArgumentParseException> { parse(Int::class, "notanint") }
        assertThrows<ArgumentParseException> { parse(CommandFixtures.Color::class, "cyan") }
        assertThrows<ArgumentParseException> { parse(Char::class, "toolong") }
    }

    @Test
    fun `argument queue tracks position and remainder`() {
        val queue = ArgumentQueue(listOf("a", "b", "c"))
        assertEquals("a", queue.peek())
        assertEquals("a", queue.next())
        assertEquals(listOf("b", "c"), queue.remaining())
        assertEquals("b c", queue.consumeRemaining())
        assertFalse(queue.hasNext())
    }
}
