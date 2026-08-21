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

package lol.simeon.polyglot.cli

import kotlinx.coroutines.test.runTest
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.annotation.Greedy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class CapturingSender : CommandSender {
    val lines: MutableList<String> = mutableListOf()
    override fun sendMessage(message: String) {
        lines += message
    }
}

class CliCommandManagerTest {

    @Command(name = "say")
    class SayCommand(private val sink: MutableList<String>) {
        @Command(name = "hello")
        fun hello(sender: CommandSender, name: String) {
            sink += "hello $name"
            sender.sendMessage("hi $name")
        }

        @Command(name = "echo")
        fun echo(sender: CommandSender, @Greedy message: String) {
            sink += "echo $message"
        }
    }

    @Test
    fun `executes quoted arguments and reports feedback`() = runTest {
        val sink = mutableListOf<String>()
        val manager = CliCommandManager()
        manager.register(SayCommand(sink))
        val sender = CapturingSender()

        manager.execute(sender, """say hello "Ada Lovelace"""")
        manager.execute(sender, "say echo the rest is greedy")

        assertEquals(listOf("hello Ada Lovelace", "echo the rest is greedy"), sink)
        assertEquals(listOf("hi Ada Lovelace"), sender.lines)
    }

    @Test
    fun `reports errors instead of throwing`() = runTest {
        val manager = CliCommandManager()
        manager.register(SayCommand(mutableListOf()))
        val sender = CapturingSender()

        manager.execute(sender, "say hello") // missing required arg

        assertTrue(sender.lines.single().contains("Missing required argument"))
    }

    @Test
    fun `completes subcommands`() = runTest {
        val manager = CliCommandManager()
        manager.register(SayCommand(mutableListOf()))

        val values = manager.completeLine(CapturingSender(), "say ").map { it.value }

        assertTrue("hello" in values)
        assertTrue("echo" in values)
    }
}
