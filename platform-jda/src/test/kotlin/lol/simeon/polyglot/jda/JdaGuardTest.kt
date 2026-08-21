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
import kotlinx.coroutines.test.runTest
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.dsl.command
import lol.simeon.polyglot.exception.GuardRejectedException
import lol.simeon.polyglot.exception.NoPermissionException
import lol.simeon.polyglot.jda.annotation.GuildOnly
import lol.simeon.polyglot.jda.annotation.RequirePermissions
import lol.simeon.polyglot.jda.guard.GuildOnlyGuard
import lol.simeon.polyglot.jda.guard.JdaGuardContributor
import lol.simeon.polyglot.jda.guard.RequirePermissionsGuard
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.full.declaredMemberFunctions

@Command(name = "admin")
@GuildOnly
@RequirePermissions(Permission.BAN_MEMBERS)
private class AdminCommand {
    @Command(name = "kick")
    @RequirePermissions(Permission.KICK_MEMBERS)
    fun kick(sender: JdaSender) = Unit
}

class JdaGuardTest {

    private fun guildSender(
        member: Member?,
        guild: Guild?,
        channel: GuildChannel?,
    ): JdaSender = mockk {
        every { this@mockk.member } returns member
        every { this@mockk.guild } returns guild
        every { guildChannel } returns channel
    }

    @Test
    fun `require permissions passes when member holds all`() = runTest {
        val channel = mockk<GuildChannel>()
        val member = mockk<Member> { every { hasPermission(channel, Permission.BAN_MEMBERS) } returns true }
        val sender = guildSender(member, mockk<Guild>(), channel)
        RequirePermissionsGuard(listOf(Permission.BAN_MEMBERS)).check(sender)
    }

    @Test
    fun `require permissions rejects when missing a permission`() = runTest {
        val channel = mockk<GuildChannel>()
        val member = mockk<Member> { every { hasPermission(channel, Permission.BAN_MEMBERS) } returns false }
        val sender = guildSender(member, mockk<Guild>(), channel)
        val error = assertThrows<NoPermissionException> {
            RequirePermissionsGuard(listOf(Permission.BAN_MEMBERS)).check(sender)
        }
        assertEquals(Permission.BAN_MEMBERS.name, error.node)
    }

    @Test
    fun `require permissions rejects outside a guild`() = runTest {
        val sender = guildSender(member = null, guild = null, channel = null)
        assertThrows<NoPermissionException> {
            RequirePermissionsGuard(listOf(Permission.BAN_MEMBERS)).check(sender)
        }
    }

    @Test
    fun `guild only rejects in DMs and allows in guild`() = runTest {
        assertThrows<GuardRejectedException> {
            GuildOnlyGuard.check(guildSender(member = null, guild = null, channel = null))
        }
        GuildOnlyGuard.check(guildSender(mockk<Member>(), mockk<Guild>(), mockk<GuildChannel>()))
    }

    @Test
    fun `contributor emits guards for annotations`() {
        val contributor = JdaGuardContributor()
        val classGuards = contributor.contribute(AdminCommand::class)
        assertTrue(classGuards.any { it === GuildOnlyGuard })

        val kickFunction = AdminCommand::class.declaredMemberFunctions.first { it.name == "kick" }
        val leafGuards = contributor.contribute(kickFunction)
        val permGuard = leafGuards.filterIsInstance<RequirePermissionsGuard>().single()
        assertEquals(listOf(Permission.KICK_MEMBERS), permGuard.permissions)
    }

    @Test
    fun `message sender exposes the invoking channel`() {
        val event = mockk<MessageReceivedEvent>(relaxed = true)
        every { event.isFromGuild } returns true
        val sender = MessageCommandSender(event)
        assertEquals(event.channel, sender.channel)
        assertEquals(event.guildChannel, sender.guildChannel)
    }

    @Test
    fun `slash build syncs native permissions and guild-only context`() {
        val manager = JdaCommandManager().apply { register(AdminCommand()) }
        val data = manager.buildSlashCommands().single()
        assertEquals(Permission.BAN_MEMBERS.rawValue, data.defaultPermissions.permissionsRaw)
        assertTrue(InteractionContextType.GUILD in data.contexts)
    }

    @Test
    fun `unannotated command keeps default context set`() {
        val manager = JdaCommandManager().apply {
            register(command<JdaSender>("plain") { executes { } })
        }
        val data = manager.buildSlashCommands().single()
        assertFalse(data.contexts == setOf(InteractionContextType.GUILD))
    }
}
