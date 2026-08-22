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

package lol.simeon.polyglot.jda.ktx

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.jda.JdaCommandManager
import lol.simeon.polyglot.jda.JdaSender
import lol.simeon.polyglot.jda.PushType
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PolyglotExtensionsTest {

    @Command(name = "ping")
    private class PingCommand {
        @Command(name = "now")
        fun now(sender: JdaSender) = Unit
    }

    @Test
    fun `builder withPolyglot NONE wires only the event listener`() {
        val builder = mockk<JDABuilder>(relaxed = true)
        val manager = JdaCommandManager()

        val result = builder.withPolyglot(manager)

        assertSame(builder, result)
        verify(exactly = 1) { builder.addEventListeners(any()) }
    }

    @Test
    fun `builder withPolyglot GLOBAL also wires the sync listener`() {
        val builder = mockk<JDABuilder>(relaxed = true)

        builder.withPolyglot(JdaCommandManager(), PushType.GLOBAL)

        verify(exactly = 2) { builder.addEventListeners(any()) }
    }

    @Test
    fun `builder withPolyglot configures the manager inline`() {
        val builder = mockk<JDABuilder>(relaxed = true)
        var registered = false

        val result = builder.withPolyglot(PushType.NONE) {
            registered = true
            register(PingCommand())
        }

        assertSame(builder, result)
        assertTrue(registered)
        verify(exactly = 1) { builder.addEventListeners(any()) }
    }

    @Test
    fun `jda withPolyglot GLOBAL pushes globally after build`() {
        val jda = mockk<JDA>(relaxed = true)
        val manager = JdaCommandManager()

        val result = jda.withPolyglot(manager, PushType.GLOBAL)

        assertSame(jda, result)
        verify { jda.addEventListener(manager.eventListener) }
        verify { jda.updateCommands() }
    }

    @Test
    fun `jda withPolyglot GUILD pushes to each connected guild`() {
        val guild = mockk<Guild>(relaxed = true)
        val jda = mockk<JDA>(relaxed = true) { every { guilds } returns listOf(guild) }

        jda.withPolyglot(JdaCommandManager(), PushType.GUILD)

        verify { guild.updateCommands() }
    }

    @Test
    fun `jda withPolyglot NONE only wires the listener`() {
        val jda = mockk<JDA>(relaxed = true)
        val manager = JdaCommandManager()

        jda.withPolyglot(manager)

        verify { jda.addEventListener(manager.eventListener) }
        verify(exactly = 0) { jda.updateCommands() }
    }
}
