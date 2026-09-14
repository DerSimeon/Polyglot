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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import lol.simeon.polyglot.CommandManager
import lol.simeon.polyglot.brigadier.BrigadierTreeBuilder
import lol.simeon.polyglot.brigadier.BrigadierTypeRegistry
import lol.simeon.polyglot.brigadier.BuiltInBrigadierTypes
import lol.simeon.polyglot.exception.PolyglotException
import lol.simeon.polyglot.minecraft.argument.DimensionSuggestionProvider
import lol.simeon.polyglot.minecraft.argument.MinecraftBrigadierTypes
import lol.simeon.polyglot.minecraft.argument.PlayerNameSuggestionProvider
import lol.simeon.polyglot.minecraft.argument.RegistryArgumentParser
import lol.simeon.polyglot.minecraft.argument.RegistrySuggestionProvider
import lol.simeon.polyglot.minecraft.argument.ServerLevelArgumentParser
import lol.simeon.polyglot.minecraft.argument.ServerPlayerArgumentParser
import lol.simeon.polyglot.minecraft.guard.MinecraftGuardContributor
import lol.simeon.polyglot.model.CommandNode
import lol.simeon.polyglot.permission.PermissionResolver
import lol.simeon.polyglot.scanner.GuardContributor
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block

/**
 * Vanilla-Minecraft command adapter shared by the Fabric and NeoForge modules. Translates every
 * registered command into a native Brigadier tree (see [BrigadierTreeBuilder]) and publishes it
 * into the server dispatcher through [register], which the loader modules call from their command
 * registration event. Commands run on the server thread.
 *
 * Ships native argument types for [ServerPlayer], [net.minecraft.world.entity.Entity],
 * [ServerLevel], [Item] and [Block]; enums and primitives are supported out of the box.
 *
 * @param permissionResolver how string `@Permission` nodes are checked; defaults to vanilla
 *   operator level [MinecraftPermissions.DEFAULT_LEVEL]
 */
public open class MinecraftCommandManager(
    public override val permissionResolver: PermissionResolver<MinecraftSender> = OpLevelPermissionResolver(),
    override val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) : CommandManager<MinecraftSender>() {

    override val guardContributors: List<GuardContributor<MinecraftSender>> = listOf(MinecraftGuardContributor())

    /** Extra native argument mappings (beyond the built-in ones) consumers may register. */
    public val brigadierTypes: BrigadierTypeRegistry<CommandSourceStack> = BrigadierTypeRegistry()

    init {
        // registries are referenced lazily: managers are typically built during mod construction,
        // before the game bootstraps them
        parsers.register(ServerPlayer::class, ServerPlayerArgumentParser)
        parsers.register(ServerLevel::class, ServerLevelArgumentParser)
        parsers.register(Item::class, RegistryArgumentParser({ BuiltInRegistries.ITEM }, "Item"))
        parsers.register(Block::class, RegistryArgumentParser({ BuiltInRegistries.BLOCK }, "Block"))
        suggestions.register(ServerPlayer::class, PlayerNameSuggestionProvider)
        suggestions.register(ServerLevel::class, DimensionSuggestionProvider)
        suggestions.register(Item::class, RegistrySuggestionProvider { BuiltInRegistries.ITEM })
        suggestions.register(Block::class, RegistrySuggestionProvider { BuiltInRegistries.BLOCK })
    }

    override fun registerNative(node: CommandNode<MinecraftSender>) {
        // Publication is deferred to register(dispatcher, buildContext), fired by the loader event.
    }

    /**
     * Builds the Brigadier tree of every registered command and registers it with [dispatcher].
     * Call from the loader's command registration event (it fires again on `/reload`).
     */
    public fun register(dispatcher: CommandDispatcher<CommandSourceStack>, buildContext: CommandBuildContext) {
        val types = BrigadierTypeRegistry<CommandSourceStack>()
        BuiltInBrigadierTypes.install(types)
        MinecraftBrigadierTypes.install(types, buildContext)
        brigadierTypes.copyInto(types)
        val builder = BrigadierTreeBuilder(
            manager = this,
            types = types,
            senderFactory = ::MinecraftSender,
            permissionResolver = permissionResolver,
            onError = ::onError,
        )
        builder.buildAll().forEach { dispatcher.register(it) }
    }

    /** Every string permission node declared by the registered commands (for loader permission APIs). */
    public fun permissionNodes(): Set<String> {
        val nodes = LinkedHashSet<String>()
        fun walk(node: CommandNode<MinecraftSender>) {
            node.permission?.let { nodes += it }
            node.childNodes.forEach(::walk)
        }
        commands.values.distinct().forEach(::walk)
        return nodes
    }

    override fun onError(sender: MinecraftSender, error: PolyglotException) {
        sender.source.sendFailure(Component.literal(formatError(sender, error)))
    }
}
