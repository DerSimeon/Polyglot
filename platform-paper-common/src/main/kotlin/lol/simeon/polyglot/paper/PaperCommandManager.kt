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

import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import lol.simeon.polyglot.CommandManager
import lol.simeon.polyglot.exception.PolyglotException
import lol.simeon.polyglot.model.CommandNode
import lol.simeon.polyglot.permission.PermissionResolver
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Paper command adapter. Publishes commands through the Brigadier lifecycle API (as
 * [io.papermc.paper.command.brigadier.BasicCommand]s) and enforces Bukkit permissions.
 *
 * Usage: register your commands, then call [install] during plugin bootstrap/enable so they are
 * published when the `COMMANDS` lifecycle event fires. Commands run on the server thread.
 *
 * Ships parsers and suggestions for [Material], [Player] and [org.bukkit.World]; enums and
 * primitives are supported out of the box by the core engine.
 */
public open class PaperCommandManager(
    private val plugin: Plugin,
    override val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) : CommandManager<PaperSender>() {

    override val permissionResolver: PermissionResolver<PaperSender> =
        PermissionResolver { sender, node -> sender.bukkitSender.hasPermission(node) }

    init {
        parsers.register(Material::class, MaterialArgumentParser)
        parsers.register(Player::class, PlayerArgumentParser)
        parsers.register(org.bukkit.World::class, WorldArgumentParser)
        suggestions.register(Material::class, MaterialSuggestionProvider)
        suggestions.register(Player::class, PlayerSuggestionProvider)
    }

    override fun registerNative(node: CommandNode<PaperSender>) {
        // Registration is deferred to the COMMANDS lifecycle event; see install().
    }

    /** Registers the lifecycle handler that publishes all registered commands to the server. */
    public fun install() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            commands.values.distinct().forEach { root ->
                registrar.register(
                    root.name,
                    root.description.ifEmpty { null },
                    root.aliases,
                    PolyglotBasicCommand(this, root),
                )
            }
        }
    }

    /** Executes a command on the calling (server) thread, reporting errors to the sender. */
    public fun executeCommand(source: CommandSourceStack, name: String, args: Array<out String>) {
        val sender = PaperSender(source)
        runBlocking {
            try {
                dispatch(sender, listOf(name) + args.toList())
            } catch (error: PolyglotException) {
                sender.bukkitSender.sendMessage(formatError(sender, error))
            }
        }
    }

    /** Computes tab-completions for a command on the calling thread. */
    public fun suggestCommand(
        source: CommandSourceStack,
        name: String,
        args: Array<out String>,
    ): Collection<String> {
        val sender = PaperSender(source)
        return runBlocking {
            complete(sender, listOf(name) + args.toList()).map { it.value }
        }
    }
}
