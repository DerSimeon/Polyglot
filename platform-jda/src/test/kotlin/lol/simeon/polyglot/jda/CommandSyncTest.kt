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
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.jda.annotation.RequirePermissions
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandSyncTest {

    @Command(name = "ping")
    private class PingCommand {
        @Command(name = "now")
        fun now(sender: JdaSender) = Unit
    }

    @Command(name = "ban")
    @RequirePermissions(Permission.BAN_MEMBERS)
    private class BanCommand {
        @Command(name = "user")
        fun user(sender: JdaSender) = Unit
    }

    private fun manager() = JdaCommandManager().apply { register(PingCommand()) }

    @Test
    fun `updateGlobalCommands pushes via jda`() {
        val jda = mockk<JDA>(relaxed = true)
        manager().updateGlobalCommands(jda)
        verify { jda.updateCommands() }
    }

    @Test
    fun `global sync listener pushes globally on ready`() {
        val jda = mockk<JDA>(relaxed = true)
        val event = mockk<ReadyEvent>(relaxed = true) { every { this@mockk.jda } returns jda }
        CommandSyncListener(manager(), PushType.GLOBAL).onReady(event)
        verify { jda.updateCommands() }
    }

    @Test
    fun `guild sync listener pushes per guild on guild ready`() {
        val guild = mockk<Guild>(relaxed = true)
        val event = mockk<GuildReadyEvent>(relaxed = true) { every { this@mockk.guild } returns guild }
        CommandSyncListener(manager(), PushType.GUILD).onGuildReady(event)
        verify { guild.updateCommands() }
    }

    @Test
    fun `global listener ignores guild ready`() {
        val guild = mockk<Guild>(relaxed = true)
        val event = mockk<GuildReadyEvent>(relaxed = true) { every { this@mockk.guild } returns guild }
        CommandSyncListener(manager(), PushType.GLOBAL).onGuildReady(event)
        verify(exactly = 0) { guild.updateCommands() }
    }

    @Test
    fun `guild listener ignores session ready`() {
        val jda = mockk<JDA>(relaxed = true)
        val event = mockk<ReadyEvent>(relaxed = true) { every { this@mockk.jda } returns jda }
        CommandSyncListener(manager(), PushType.GUILD).onReady(event)
        verify(exactly = 0) { jda.updateCommands() }
    }

    @Test
    fun `none listener never pushes`() {
        val jda = mockk<JDA>(relaxed = true)
        val guild = mockk<Guild>(relaxed = true)
        val ready = mockk<ReadyEvent>(relaxed = true) { every { this@mockk.jda } returns jda }
        val guildReady = mockk<GuildReadyEvent>(relaxed = true) { every { this@mockk.guild } returns guild }
        val listener = manager().commandSyncListener(PushType.NONE)
        listener.onReady(ready)
        listener.onGuildReady(guildReady)
        verify(exactly = 0) { jda.updateCommands() }
        verify(exactly = 0) { guild.updateCommands() }
    }

    @Test
    fun `invite url aggregates command permissions plus extras`() {
        val captured = mutableListOf<Collection<Permission>>()
        val jda = mockk<JDA> {
            every { getInviteUrl(capture(captured)) } returns "https://invite"
        }
        val manager = JdaCommandManager().apply { register(BanCommand()) }

        val url = manager.inviteUrl(jda, Permission.MESSAGE_HISTORY)

        assertEquals("https://invite", url)
        val permissions = captured.single()
        assertTrue(permissions.containsAll(
            listOf(Permission.BAN_MEMBERS, Permission.MESSAGE_HISTORY, Permission.USE_APPLICATION_COMMANDS),
        ))
    }
}
