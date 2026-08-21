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

package lol.simeon.polyglot.paper

import io.mockk.every
import io.mockk.mockk
import io.papermc.paper.command.brigadier.CommandSourceStack
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.annotation.Permission
import lol.simeon.polyglot.paper.legacy.LegacyPaperPlatform
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

private object Recorder {
    val calls: MutableList<String> = mutableListOf()
}

@Command(name = "give")
private class GiveCommand {
    @Command(name = "item")
    fun item(sender: PaperSender, material: Material) {
        Recorder.calls += material.name
    }

    @Command(name = "secret")
    @Permission("give.secret")
    fun secret(sender: PaperSender) {
        Recorder.calls += "secret"
    }

    @Command(name = "world")
    fun world(sender: PaperSender, target: World) {
        Recorder.calls += "world:${target.name}"
    }

    @Command(name = "who")
    fun who(sender: PaperSender, target: Player) {
        Recorder.calls += "who:${target.name}"
    }
}

class PaperMockBukkitTest {
    private lateinit var server: ServerMock
    private lateinit var manager: PaperCommandManager

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        manager = PaperCommandManager(MockBukkit.createMockPlugin())
        manager.register(GiveCommand())
        Recorder.calls.clear()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    private fun sourceOf(sender: CommandSender): CommandSourceStack = mockk {
        every { this@mockk.sender } returns sender
        every { executor } returns null
    }

    @Test
    fun `parses a Material argument through dispatch`() {
        manager.executeCommand(sourceOf(server.consoleSender), "give", arrayOf("item", "stone"))
        assertEquals(listOf("STONE"), Recorder.calls)
    }

    @Test
    fun `suggests material names for the in-progress token`() {
        val suggestions = manager.suggestCommand(sourceOf(server.consoleSender), "give", arrayOf("item", "st"))
        assertTrue("stone" in suggestions)
    }

    @Test
    fun `enforces bukkit permissions`() {
        val player = server.addPlayer("Alex") // not op, lacks give.secret
        manager.executeCommand(sourceOf(player), "give", arrayOf("secret"))
        assertTrue(Recorder.calls.isEmpty())
        assertTrue(player.nextMessage()!!.contains("permission", ignoreCase = true))
    }

    @Test
    fun `parses a World argument`() {
        server.addSimpleWorld("nether")
        manager.executeCommand(sourceOf(server.consoleSender), "give", arrayOf("world", "nether"))
        assertEquals(listOf("world:nether"), Recorder.calls)
    }

    @Test
    fun `resolves and suggests online players`() {
        server.addPlayer("Alex")
        manager.executeCommand(sourceOf(server.consoleSender), "give", arrayOf("who", "Alex"))
        assertEquals(listOf("who:Alex"), Recorder.calls)

        val suggestions = manager.suggestCommand(sourceOf(server.consoleSender), "give", arrayOf("who", "Al"))
        assertTrue("Alex" in suggestions)
    }

    @Test
    fun `platform factory exposes version and builds a manager`() {
        assertEquals("1.21.11", LegacyPaperPlatform.MINECRAFT_VERSION)
        val created = LegacyPaperPlatform.createManager(MockBukkit.createMockPlugin())
        assertTrue(created.commands.isEmpty())
    }
}
