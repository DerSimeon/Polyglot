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
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.argument.ArgumentQueue
import lol.simeon.polyglot.argument.ParseContext
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JdaUnitsTest {

    @Command(name = "ping")
    private class PingCommand {
        @Command(name = "now")
        fun now(sender: JdaSender) = sender.reply("pong")
    }

    @Test
    fun `option type mapper maps entity types`() {
        assertEquals(OptionType.USER, OptionTypeMapper.map(Member::class))
        assertEquals(OptionType.USER, OptionTypeMapper.map(User::class))
        assertEquals(OptionType.ROLE, OptionTypeMapper.map(Role::class))
        assertEquals(OptionType.CHANNEL, OptionTypeMapper.map(TextChannel::class))
    }

    @Test
    fun `slash sender replies ephemerally when not acknowledged`() {
        val event = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { event.isAcknowledged } returns false
        SlashCommandSender(event).reply("hi")
        verify { event.reply("hi") }
    }

    @Test
    fun `message sender replies to the channel`() {
        val event = mockk<MessageReceivedEvent>(relaxed = true)
        MessageCommandSender(event).reply("hello")
        verify { event.channel.sendMessage("hello") }
    }

    @Test
    fun `slash sender sends a rich reply when not acknowledged`() {
        val event = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { event.isAcknowledged } returns false
        SlashCommandSender(event).reply {
            content = "here"
            embed { setDescription("body") }
        }
        verify { event.reply(any<MessageCreateData>()) }
    }

    @Test
    fun `slash sender routes a rich reply through the hook once acknowledged`() {
        val event = mockk<SlashCommandInteractionEvent>(relaxed = true)
        every { event.isAcknowledged } returns true
        SlashCommandSender(event).reply { embed { setDescription("body") } }
        verify { event.hook.sendMessage(any<MessageCreateData>()) }
    }

    @Test
    fun `message sender sends a rich reply with an attachment to the channel`() {
        val event = mockk<MessageReceivedEvent>(relaxed = true)
        MessageCommandSender(event).reply {
            content = "report"
            file("data.txt", "hi".toByteArray())
        }
        verify { event.channel.sendMessage(any<MessageCreateData>()) }
    }

    @Test
    fun `member parser resolves by id`() = runTest {
        val member = mockk<Member>()
        val guild = mockk<Guild> { every { getMemberById(123L) } returns member }
        val sender = mockk<JdaSender> { every { this@mockk.guild } returns guild }
        val result = MemberArgumentParser.parse(ParseContext(sender, "m", ArgumentQueue(listOf("123"))))
        assertEquals(member, result)
    }

    @Test
    fun `role parser resolves by id`() = runTest {
        val role = mockk<Role>()
        val guild = mockk<Guild> { every { getRoleById(9L) } returns role }
        val sender = mockk<JdaSender> { every { this@mockk.guild } returns guild }
        val result = RoleArgumentParser.parse(ParseContext(sender, "r", ArgumentQueue(listOf("9"))))
        assertEquals(role, result)
    }

    @Test
    fun `user parser resolves via jda cache`() = runTest {
        val user = mockk<User>()
        val jda = mockk<JDA> { every { getUserById(5L) } returns user }
        val sender = mockk<JdaSender> { every { this@mockk.jda } returns jda }
        val result = UserArgumentParser.parse(ParseContext(sender, "u", ArgumentQueue(listOf("5"))))
        assertEquals(user, result)
    }

    @Test
    fun `builds slash payloads for registered commands`() {
        val manager = JdaCommandManager()
        manager.register(PingCommand())
        val payloads = manager.buildSlashCommands()
        assertEquals(listOf("ping"), payloads.map { it.name })
        assertTrue(payloads.single().subcommands.any { it.name == "now" })
    }
}
