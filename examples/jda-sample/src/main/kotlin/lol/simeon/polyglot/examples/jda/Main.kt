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

package lol.simeon.polyglot.examples.jda

import kotlinx.coroutines.delay
import lol.simeon.polyglot.annotation.Argument
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.annotation.Named
import lol.simeon.polyglot.jda.JdaCommandManager
import lol.simeon.polyglot.jda.JdaSender
import lol.simeon.polyglot.jda.PushType
import lol.simeon.polyglot.jda.annotation.GuildOnly
import lol.simeon.polyglot.jda.annotation.RequirePermissions
import lol.simeon.polyglot.jda.ktx.withPolyglot
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import java.awt.Color

@Command(name = "poll", description = "Poll commands")
class PollCommand {
    @Command(name = "create")
    fun create(
        sender: JdaSender,
        @Argument("question") question: String,
        @Named("options") options: String?,
    ) {
        // Rich reply DSL: an embed plus a text file attachment, built once and sent on either backend.
        sender.reply {
            content = "New poll created"
            embed {
                setTitle("Poll")
                setDescription(question)
                addField("Options", options ?: "yes/no", false)
                setColor(Color.CYAN)
            }
            file("poll.txt", "$question\n${options ?: "yes/no"}".toByteArray())
        }
    }

    @Command(name = "purge", description = "Delete recent messages (mods only)")
    @GuildOnly
    @RequirePermissions(Permission.MESSAGE_MANAGE)
    suspend fun purge(sender: JdaSender, @Argument("count") count: Int) {
        // Defer: keeps the interaction alive past Discord's 3s window while we work.
        sender.defer(ephemeral = true)
        delay(1_000) // stand-in for an async query / bulk delete
        val where = sender.guildChannel?.name ?: "this channel"
        sender.reply("Purged $count messages in #$where")
    }
}

fun main() {
    val manager = JdaCommandManager(prefixProvider = { listOf("!") })
    manager.register(PollCommand())

    // One call wires the interaction/message listener AND auto-syncs slash commands on ready — no
    // hand-written GuildReadyHandler, no manual updateCommands. PushType.GLOBAL pushes once when the
    // session is ready; use PushType.GUILD for instant propagation while developing.
    val builder = JDABuilder.createDefault(System.getenv("DISCORD_TOKEN") ?: "TOKEN")
        .withPolyglot(manager, PushType.GLOBAL)
    //   val jda = builder.enableIntents(GatewayIntent.MESSAGE_CONTENT).build()
    //   println(manager.inviteUrl(jda, Permission.MESSAGE_HISTORY))

    println("Configured builder: $builder")
    println("Slash payloads: " + manager.buildSlashCommands().joinToString { it.name })
}
