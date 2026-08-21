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

package lol.simeon.polyglot.jda

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import lol.simeon.polyglot.annotation.Command
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private object Recorder {
    val calls: MutableList<String> = mutableListOf()
}

@Command(name = "weather")
private class WeatherCommand {
    @Command(name = "today")
    fun today(sender: JdaSender, city: String) {
        Recorder.calls += "today:$city"
    }

    @Command(name = "admin")
    class Admin {
        @Command(name = "cache")
        class Cache {
            @Command(name = "clear")
            fun clear(sender: JdaSender) {
                Recorder.calls += "clear"
            }
        }
    }
}

class JdaDispatchTest {
    private lateinit var manager: JdaCommandManager

    @BeforeEach
    fun setUp() {
        Recorder.calls.clear()
        // Unconfined so launchDispatch completes synchronously within the event callback
        manager = JdaCommandManager(
            prefixProvider = { listOf("!") },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
        )
        manager.register(WeatherCommand())
    }

    private fun stringOption(value: String): OptionMapping = mockk {
        every { type } returns OptionType.STRING
        every { asString } returns value
    }

    @Test
    fun `routes a slash command with an option`() {
        val event = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { event.name } returns "weather"
        every { event.subcommandGroup } returns null
        every { event.subcommandName } returns "today"
        every { event.getOption("city") } returns stringOption("Paris")

        manager.eventListener.onSlashCommandInteraction(event)

        assertEquals(listOf("today:Paris"), Recorder.calls)
    }

    @Test
    fun `reverses flattened slash path back to the nested command`() {
        val event = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { event.name } returns "weather"
        every { event.subcommandGroup } returns "admin"
        every { event.subcommandName } returns "cache-clear"

        manager.eventListener.onSlashCommandInteraction(event)

        assertEquals(listOf("clear"), Recorder.calls)
    }

    @Test
    fun `routes a prefix message command`() {
        val event = mockk<MessageReceivedEvent>(relaxed = true)
        every { event.author.isBot } returns false
        every { event.message.contentRaw } returns "!weather today Berlin"

        manager.eventListener.onMessageReceived(event)

        assertEquals(listOf("today:Berlin"), Recorder.calls)
    }
}
