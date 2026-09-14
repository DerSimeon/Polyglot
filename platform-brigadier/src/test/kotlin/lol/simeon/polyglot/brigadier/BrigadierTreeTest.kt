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

package lol.simeon.polyglot.brigadier

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import lol.simeon.polyglot.annotation.Choice
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.annotation.Default
import lol.simeon.polyglot.annotation.Flag
import lol.simeon.polyglot.annotation.Greedy
import lol.simeon.polyglot.annotation.Named
import lol.simeon.polyglot.annotation.Optional
import lol.simeon.polyglot.annotation.Permission
import lol.simeon.polyglot.annotation.Range
import lol.simeon.polyglot.annotation.Suggestion
import lol.simeon.polyglot.argument.ArgumentParser
import lol.simeon.polyglot.dsl.command
import lol.simeon.polyglot.exception.ArgumentParseException
import lol.simeon.polyglot.exception.GuardRejectedException
import lol.simeon.polyglot.exception.NoPermissionException
import lol.simeon.polyglot.guard.CommandGuard
import lol.simeon.polyglot.suggestion.SuggestionProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

enum class Difficulty { PEACEFUL, EASY, NORMAL, HARD }

data class Colour(val name: String)

/** Guard that is also mirrored natively into `requires`. */
object PlayerOnlyGuard : CommandGuard<TestSender>, BrigadierRequirement<TestSource> {
    override suspend fun check(sender: TestSender) {
        if (!sender.source.player) throw GuardRejectedException("players only")
    }

    override fun test(source: TestSource): Boolean = source.player
}

class BrigadierTreeTest {

    @Command(name = "party", aliases = ["p"], description = "Party management")
    class PartyCommand {
        @Default
        fun overview(sender: TestSender) {
            calls += "overview"
        }

        @Command(name = "create", aliases = ["new"])
        fun create(sender: TestSender, name: String, @Optional @Range(min = 1.0, max = 16.0) size: Int?) {
            calls += "create:$name:${size ?: 4}"
        }

        @Command(name = "difficulty")
        @Permission("party.difficulty")
        fun difficulty(sender: TestSender, level: Difficulty) {
            calls += "difficulty:$level"
        }

        @Command(name = "paint")
        fun paint(sender: TestSender, colour: Colour, @Choice(["matte", "gloss"]) finish: String) {
            calls += "paint:${colour.name}:$finish"
        }

        @Command(name = "invite")
        fun invite(sender: TestSender, @Suggestion("friends") who: String) {
            calls += "invite:$who"
        }

        @Command(name = "admin")
        class Admin {
            @Command(name = "kick")
            fun kick(sender: TestSender, target: String, @Greedy reason: String) {
                calls += "kick:$target:$reason"
            }
        }
    }

    @Command(name = "deploy")
    class DeployCommand {
        @Command(name = "run")
        fun run(
            sender: TestSender,
            service: String,
            @Named("count", shorthand = 'c') count: Int,
            @Named("region") region: String?,
            @Flag("dry", shorthand = 'd') dry: Boolean,
        ) {
            calls += "run:$service:$count:$region:$dry"
        }

        @Command(name = "say")
        fun say(sender: TestSender, @Greedy text: String, @Flag("loud") loud: Boolean) {
            calls += "say:$text:$loud"
        }
    }

    companion object {
        val calls: MutableList<String> = mutableListOf()
    }

    private lateinit var manager: TestBrigadierManager

    @BeforeEach
    fun setUp() {
        calls.clear()
        manager = TestBrigadierManager()
        manager.parsers.register(Colour::class, ArgumentParser { ctx -> Colour(ctx.queue.next().lowercase()) })
        manager.suggestions.register(Colour::class, SuggestionProvider { listOf("red", "green").map { lol.simeon.polyglot.suggestion.Suggestion(it) } })
        manager.suggestions.register("friends", SuggestionProvider { listOf(lol.simeon.polyglot.suggestion.Suggestion("alice", "best friend")) })
        manager.register(PartyCommand())
        manager.register(DeployCommand())
    }

    @Test
    fun `builds literals for names, aliases and nested groups with typed arguments`() {
        val root = manager.dispatcher.root
        val party = root.getChild("party") as LiteralCommandNode
        assertNotNull(root.getChild("p"))
        assertNotNull(party.getChild("new"))
        assertNotNull((party.getChild("admin") as LiteralCommandNode).getChild("kick"))

        val create = party.getChild("create") as LiteralCommandNode
        val name = create.getChild("name") as ArgumentCommandNode<*, *>
        val size = name.getChild("size") as ArgumentCommandNode<*, *>
        assertEquals(StringArgumentType.StringType.QUOTABLE_PHRASE, (name.type as StringArgumentType).type)
        assertEquals(1, (size.type as IntegerArgumentType).minimum)
        assertEquals(16, (size.type as IntegerArgumentType).maximum)
        // optional trailing argument makes the preceding node executable, the literal itself not
        assertNotNull(name.command)
        assertNull(create.command)
        assertNotNull(party.command)
    }

    @Test
    fun `executes with natively parsed and core parsed arguments`() {
        assertEquals(1, manager.execute("party create MyParty 6"))
        assertEquals(1, manager.execute("p create \"My Party\""))
        assertEquals(1, manager.execute("party"))
        assertEquals(1, manager.execute("party paint RED gloss"))
        assertEquals(listOf("create:MyParty:6", "create:My Party:4", "overview", "paint:red:gloss"), calls)
    }

    @Test
    fun `brigadier enforces numeric bounds before the engine runs`() {
        assertThrows<CommandSyntaxException> { manager.execute("party create x 99") }
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `enum and choice failures are reported through onError`() {
        assertEquals(0, manager.execute("party difficulty impossible", TestSource(permissions = mutableSetOf("party.difficulty"))))
        assertEquals(0, manager.execute("party paint red shiny"))
        assertEquals(2, manager.errors.size)
        assertTrue(manager.errors.all { it is ArgumentParseException })
        assertEquals(1, manager.execute("party difficulty hard", TestSource(permissions = mutableSetOf("party.difficulty"))))
        assertEquals(listOf("difficulty:HARD"), calls)
    }

    @Test
    fun `permission is mirrored into requires and hides the node`() {
        val party = manager.dispatcher.root.getChild("party")
        assertFalse(party.getChild("difficulty").canUse(TestSource()))
        assertTrue(party.getChild("difficulty").canUse(TestSource(permissions = mutableSetOf("party.difficulty"))))
        assertThrows<CommandSyntaxException> { manager.execute("party difficulty hard") }
    }

    @Test
    fun `guards implementing BrigadierRequirement are mirrored and still checked by the engine`() {
        manager.register(
            command<TestSender>("home") {
                guard(PlayerOnlyGuard)
                executes { calls += "home" }
            },
        )
        val home = manager.dispatcher.root.getChild("home")
        assertFalse(home.canUse(TestSource(player = false)))
        assertEquals(1, manager.execute("home", TestSource(player = true)))
        assertEquals(listOf("home"), calls)
    }

    @Test
    fun `engine level failures on the path surface as NoPermission`() {
        // a guard without native mirroring is only enforced by the engine
        manager.register(
            command<TestSender>("vip") {
                permission("vip.use")
                subcommand("lounge") {
                    guard(CommandGuard { sender -> if (sender.source.name != "vip") throw NoPermissionException("vip.lounge") })
                    executes { calls += "lounge" }
                }
            },
        )
        val source = TestSource(name = "nobody", permissions = mutableSetOf("vip.use"))
        assertEquals(0, manager.execute("vip lounge", source))
        assertTrue(manager.errors.single() is NoPermissionException)
    }

    @Test
    fun `suggests enum constants, custom providers, choices and named ids`() {
        val ops = TestSource(permissions = mutableSetOf("party.difficulty"))
        assertEquals(listOf("HARD"), manager.suggest("party difficulty h", ops))
        assertEquals(listOf("EASY", "HARD", "NORMAL", "PEACEFUL"), manager.suggest("party difficulty ", ops).sorted())
        assertEquals(listOf("green", "red"), manager.suggest("party paint ").sorted())
        assertEquals(listOf("gloss"), manager.suggest("party paint red g"))
        assertEquals(listOf("alice"), manager.suggest("party invite a"))
    }

    @Test
    fun `greedy arguments capture the rest of the input`() {
        assertEquals(1, manager.execute("party admin kick Bob being rude   twice"))
        assertEquals(listOf("kick:Bob:being rude   twice"), calls)
        val kick = (manager.dispatcher.root.getChild("party").getChild("admin").getChild("kick").getChild("target")
            .getChild("reason") as ArgumentCommandNode<*, *>)
        assertEquals(StringArgumentType.StringType.GREEDY_PHRASE, (kick.type as StringArgumentType).type)
    }

    @Test
    fun `named options and flags live in a trailing options node`() {
        val run = manager.dispatcher.root.getChild("deploy").getChild("run").getChild("service")
        assertNotNull(run.getChild("options"))
        assertEquals(1, manager.execute("deploy run api --count 3 --region eu"))
        assertEquals(1, manager.execute("deploy run web -c 1 -d"))
        assertEquals(1, manager.execute("deploy run db --count 2"))
        assertEquals(0, manager.execute("deploy run db"))
        assertEquals(0, manager.execute("deploy run db --count 2 stray"))
        assertEquals(listOf("run:api:3:eu:false", "run:web:1:null:true", "run:db:2:null:false"), calls)
        assertEquals(2, manager.errors.size)
    }

    @Test
    fun `option names are suggested inside the options node`() {
        assertEquals(listOf("--count", "--dry", "--region"), manager.suggest("deploy run api --").sorted())
        assertEquals(listOf("--region"), manager.suggest("deploy run api --count 3 --r"))
    }

    @Test
    fun `a trailing greedy argument absorbs options`() {
        val say = manager.dispatcher.root.getChild("deploy").getChild("say").getChild("text")
        assertNull(say.getChild("options"))
        assertEquals(1, manager.execute("deploy say hello --loud world"))
        assertEquals(1, manager.execute("deploy say plain"))
        assertEquals(listOf("say:hello world:true", "say:plain:false"), calls)
        assertEquals(listOf("--loud"), manager.suggest("deploy say hello --"))
    }

    @Test
    fun `buildAll covers every registered root`() {
        val builder = BrigadierTreeBuilder<TestSender, TestSource>(
            manager = manager,
            types = manager.types,
            senderFactory = ::TestSender,
            permissionResolver = { _, _ -> true },
            onError = { _, _ -> },
        )
        assertEquals(listOf("party", "p", "deploy"), builder.buildAll().map { it.literal })
        assertEquals("options", builder.optionsNodeName)
    }
}
