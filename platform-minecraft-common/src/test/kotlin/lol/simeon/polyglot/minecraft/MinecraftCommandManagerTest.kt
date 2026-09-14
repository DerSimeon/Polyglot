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

package lol.simeon.polyglot.minecraft

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.annotation.Default
import lol.simeon.polyglot.annotation.Named
import lol.simeon.polyglot.annotation.Optional
import lol.simeon.polyglot.annotation.Permission
import lol.simeon.polyglot.annotation.Range
import lol.simeon.polyglot.argument.ArgumentQueue
import lol.simeon.polyglot.argument.ParseContext
import lol.simeon.polyglot.dsl.command
import lol.simeon.polyglot.exception.ArgumentParseException
import lol.simeon.polyglot.exception.GuardRejectedException
import lol.simeon.polyglot.exception.NoPermissionException
import lol.simeon.polyglot.minecraft.annotation.OpLevel
import lol.simeon.polyglot.minecraft.annotation.PlayerOnly
import lol.simeon.polyglot.minecraft.argument.DimensionSuggestionProvider
import lol.simeon.polyglot.minecraft.argument.PlayerNameSuggestionProvider
import lol.simeon.polyglot.minecraft.argument.RegistryArgumentParser
import lol.simeon.polyglot.minecraft.argument.RegistrySuggestionProvider
import lol.simeon.polyglot.minecraft.argument.ServerLevelArgumentParser
import lol.simeon.polyglot.minecraft.argument.ServerPlayerArgumentParser
import lol.simeon.polyglot.minecraft.guard.OpLevelGuard
import lol.simeon.polyglot.minecraft.guard.PlayerOnlyGuard
import lol.simeon.polyglot.suggestion.SuggestionContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.function.Supplier

class MinecraftCommandManagerTest {

    enum class Mode { SURVIVAL, CREATIVE }

    @Command(name = "party")
    class PartyCommand {
        @Default
        fun overview(sender: MinecraftSender) {
            sender.reply("overview")
        }

        @Command(name = "create")
        fun create(sender: MinecraftSender, name: String, @Optional @Range(min = 1.0, max = 16.0) size: Int?) {
            calls += "create:$name:${size ?: 4}"
        }

        @Command(name = "mode")
        @Permission("party.mode")
        fun mode(sender: MinecraftSender, mode: Mode) {
            calls += "mode:$mode"
        }

        @Command(name = "give")
        fun give(sender: MinecraftSender, item: Item, @Named("count") count: Int?) {
            calls += "give:${BuiltInRegistries.ITEM.getKey(item)}:${count ?: 1}"
        }

        @Command(name = "block")
        fun block(sender: MinecraftSender, block: Block) {
            calls += "block:${BuiltInRegistries.BLOCK.getKey(block)}"
        }

        @Command(name = "admin")
        @OpLevel(3)
        class Admin {
            @Command(name = "reset")
            fun reset(sender: MinecraftSender) {
                calls += "reset"
            }
        }

        @Command(name = "home")
        @PlayerOnly
        fun home(sender: MinecraftSender) {
            calls += "home:${sender.player?.let { "player" }}"
        }
    }

    companion object {
        val calls: MutableList<String> = mutableListOf()

        @JvmStatic
        @BeforeAll
        fun boot() {
            MinecraftFixtures.ensureBooted()
        }
    }

    private lateinit var manager: MinecraftCommandManager
    private lateinit var dispatcher: CommandDispatcher<CommandSourceStack>

    @BeforeEach
    fun setUp() {
        calls.clear()
        manager = MinecraftCommandManager()
        manager.register(PartyCommand())
        dispatcher = CommandDispatcher()
        manager.register(dispatcher, MinecraftFixtures.buildContext)
    }

    @Test
    fun `translates and executes commands with native and core parsed arguments`() {
        val source = MinecraftFixtures.source(level = 2)
        assertEquals(1, dispatcher.execute("party create MyParty 6", source))
        assertEquals(1, dispatcher.execute("party mode creative", source))
        assertEquals(1, dispatcher.execute("party give minecraft:diamond --count 3", source))
        assertEquals(1, dispatcher.execute("party give stone", source))
        assertEquals(1, dispatcher.execute("party block minecraft:oak_log", source))
        assertEquals(
            listOf("create:MyParty:6", "mode:CREATIVE", "give:minecraft:diamond:3", "give:minecraft:stone:1", "block:minecraft:oak_log"),
            calls,
        )
        assertThrows<CommandSyntaxException> { dispatcher.execute("party create x 99", source) }
        assertThrows<CommandSyntaxException> { dispatcher.execute("party give not_an_item", source) }
    }

    @Test
    fun `default handler replies through the sender`() {
        val source = MinecraftFixtures.source()
        assertEquals(1, dispatcher.execute("party", source))
        val supplier = slot<Supplier<Component>>()
        verify { source.sendSuccess(capture(supplier), false) }
        assertEquals("overview", supplier.captured.get().string)
    }

    @Test
    fun `failures are reported with sendFailure`() {
        val source = MinecraftFixtures.source(level = 2)
        assertEquals(0, dispatcher.execute("party mode hardcore", source))
        val message = slot<Component>()
        verify { source.sendFailure(capture(message)) }
        assertTrue(message.captured.string.contains("mode"))
    }

    @Test
    fun `op level permission resolver gates string permissions`() {
        val party = dispatcher.root.getChild("party")
        assertFalse(party.getChild("mode").canUse(MinecraftFixtures.source(level = 1)))
        assertTrue(party.getChild("mode").canUse(MinecraftFixtures.source(level = 2)))
        assertThrows<CommandSyntaxException> { dispatcher.execute("party mode creative", MinecraftFixtures.source(level = 0)) }

        val strict = MinecraftCommandManager(permissionResolver = OpLevelPermissionResolver(level = 4))
        assertFalse(strict.permissionResolver.hasPermission(MinecraftSender(MinecraftFixtures.source(level = 3)), "x"))
        assertTrue(strict.permissionResolver.hasPermission(MinecraftSender(MinecraftFixtures.source(level = 4)), "x"))
    }

    @Test
    fun `op level and player only guards are mirrored and enforced`() = runTest {
        val party = dispatcher.root.getChild("party")
        assertFalse(party.getChild("admin").canUse(MinecraftFixtures.source(level = 2)))
        assertTrue(party.getChild("admin").canUse(MinecraftFixtures.source(level = 3)))
        assertEquals(1, dispatcher.execute("party admin reset", MinecraftFixtures.source(level = 4)))

        val player = mockk<ServerPlayer>(relaxed = true)
        assertFalse(party.getChild("home").canUse(MinecraftFixtures.source()))
        assertTrue(party.getChild("home").canUse(MinecraftFixtures.source(player = player)))
        assertEquals(1, dispatcher.execute("party home", MinecraftFixtures.source(player = player)))
        assertEquals(listOf("reset", "home:player"), calls)

        assertThrows<NoPermissionException> { OpLevelGuard(2).check(MinecraftSender(MinecraftFixtures.source(level = 1))) }
        assertThrows<GuardRejectedException> { PlayerOnlyGuard.check(MinecraftSender(MinecraftFixtures.source())) }
        PlayerOnlyGuard.check(MinecraftSender(MinecraftFixtures.source(player = player)))
    }

    @Test
    fun `dsl helpers attach the same guards`() {
        val node = command<MinecraftSender>("x") {
            opLevel(4)
            playerOnly()
            executes { }
        }
        assertEquals(4, (node.guards[0] as OpLevelGuard).level)
        assertSame(PlayerOnlyGuard, node.guards[1])
    }

    @Test
    fun `collects permission nodes and lets consumers add native types`() {
        assertEquals(setOf("party.mode"), manager.permissionNodes())
        manager.register(
            command<MinecraftSender>("x") {
                permission("x.use")
                subcommand("y") {
                    permission("x.y")
                    executes { }
                }
            },
        )
        assertEquals(setOf("party.mode", "x.use", "x.y"), manager.permissionNodes())
        assertFalse(manager.brigadierTypes.supports(Mode::class))
    }

    @Test
    fun `sender exposes source details and error replies`() {
        val player = mockk<ServerPlayer>(relaxed = true)
        val level = mockk<ServerLevel>(relaxed = true)
        val server = mockk<MinecraftServer>(relaxed = true)
        val source = MinecraftFixtures.source(player = player, server = server)
        every { source.level } returns level
        val sender = MinecraftSender(source)
        assertSame(player, sender.player)
        assertSame(player, sender.entity)
        assertSame(level, sender.level)
        assertSame(server, sender.server)
        assertTrue(sender.isPlayer)
        sender.replyError("nope")
        val message = slot<Component>()
        verify { source.sendFailure(capture(message)) }
        assertEquals("nope", message.captured.string)
        assertFalse(MinecraftSender(MinecraftFixtures.source()).isPlayer)
    }

    @Test
    fun `core parsers resolve players, dimensions and registry entries`() = runTest {
        val player = mockk<ServerPlayer>(relaxed = true)
        val overworld = mockk<ServerLevel>(relaxed = true)
        val playerList = mockk<PlayerList>(relaxed = true)
        val server = mockk<MinecraftServer>(relaxed = true)
        every { server.playerList } returns playerList
        every { playerList.getPlayerByName("Steve") } returns player
        every { playerList.getPlayerByName("Nobody") } returns null
        every { server.playerNames } returns arrayOf("Steve", "Alex")
        every { server.getLevel(any()) } returns null
        every { server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"))) } returns overworld
        every { server.levelKeys() } returns setOf(Level.OVERWORLD, Level.NETHER)
        val sender = MinecraftSender(MinecraftFixtures.source(server = server))

        assertSame(player, ServerPlayerArgumentParser.parse(ParseContext(sender, "p", ArgumentQueue(listOf("Steve")))))
        assertThrows<ArgumentParseException> { ServerPlayerArgumentParser.parse(ParseContext(sender, "p", ArgumentQueue(listOf("Nobody")))) }
        assertSame(overworld, ServerLevelArgumentParser.parse(ParseContext(sender, "w", ArgumentQueue(listOf("overworld")))))
        assertThrows<ArgumentParseException> { ServerLevelArgumentParser.parse(ParseContext(sender, "w", ArgumentQueue(listOf("nether")))) }
        assertThrows<ArgumentParseException> { ServerLevelArgumentParser.parse(ParseContext(sender, "w", ArgumentQueue(listOf("bad id!")))) }

        val items = RegistryArgumentParser({ BuiltInRegistries.ITEM }, "Item")
        assertSame(Items.DIAMOND, items.parse(ParseContext(sender, "i", ArgumentQueue(listOf("diamond")))))
        assertThrows<ArgumentParseException> { items.parse(ParseContext(sender, "i", ArgumentQueue(listOf("unobtainium")))) }
        assertThrows<ArgumentParseException> { items.parse(ParseContext(sender, "i", ArgumentQueue(listOf("bad id!")))) }
        val blocks = RegistryArgumentParser({ BuiltInRegistries.BLOCK }, "Block")
        assertSame(Blocks.STONE, blocks.parse(ParseContext(sender, "b", ArgumentQueue(listOf("minecraft:stone")))))

        assertEquals(listOf("Steve"), PlayerNameSuggestionProvider.suggest(SuggestionContext(sender, "st", "p")).map { it.value })
        assertEquals(
            listOf("minecraft:the_nether"),
            DimensionSuggestionProvider.suggest(SuggestionContext(sender, "minecraft:the_n", "w")).map { it.value },
        )
        val diamonds = RegistrySuggestionProvider { BuiltInRegistries.ITEM }.suggest(SuggestionContext(sender, "diamond_s", "i")).map { it.value }
        assertTrue("minecraft:diamond_sword" in diamonds)
        assertTrue(diamonds.all { it.startsWith("minecraft:diamond_s") })
    }
}
