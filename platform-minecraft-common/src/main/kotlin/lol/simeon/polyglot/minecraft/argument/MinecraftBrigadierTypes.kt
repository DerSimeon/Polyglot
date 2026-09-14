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

package lol.simeon.polyglot.minecraft.argument

import lol.simeon.polyglot.brigadier.BrigadierTypeRegistry
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceArgument
import net.minecraft.commands.arguments.blocks.BlockStateArgument
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block

/**
 * Native Brigadier mappings for vanilla types: players and entities (selectors), dimensions,
 * items and blocks. Item/block parsing needs the [CommandBuildContext] handed out at registration.
 */
public object MinecraftBrigadierTypes {

    /** Installs the vanilla mappings into [registry]. */
    public fun install(registry: BrigadierTypeRegistry<CommandSourceStack>, buildContext: CommandBuildContext) {
        registry.register<ServerPlayer>(
            type = { EntityArgument.player() },
            extract = { ctx, name -> EntityArgument.getPlayer(ctx, name) },
        )
        registry.register<Entity>(
            type = { EntityArgument.entity() },
            extract = { ctx, name -> EntityArgument.getEntity(ctx, name) },
        )
        registry.register<ServerLevel>(
            type = { DimensionArgument.dimension() },
            extract = { ctx, name -> DimensionArgument.getDimension(ctx, name) },
        )
        // ResourceArgument (not ItemArgument) keeps the class files binary-compatible across
        // 1.21.11 and 26.2, whose ItemInput accessors differ.
        registry.register<Item>(
            type = { ResourceArgument.resource(buildContext, Registries.ITEM) },
            extract = { ctx, name -> ResourceArgument.getResource(ctx, name, Registries.ITEM).value() },
        )
        registry.register<Block>(
            type = { BlockStateArgument.block(buildContext) },
            extract = { ctx, name -> BlockStateArgument.getBlock(ctx, name).state.block },
        )
    }
}
