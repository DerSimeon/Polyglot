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

import io.mockk.every
import io.mockk.mockk
import net.minecraft.SharedConstants
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.world.flag.FeatureFlags
import java.util.stream.Stream

/** Boots the vanilla registries once per JVM (needed by item/block parsing). */
object MinecraftFixtures {
    init {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    /** Build context exposing the built-in item and block registries. */
    val buildContext: CommandBuildContext = CommandBuildContext.simple(
        HolderLookup.Provider.create(Stream.of(BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK)),
        FeatureFlags.REGISTRY.allFlags(),
    )

    /** A relaxed [CommandSourceStack] mock with the given operator level and optional player. */
    fun source(level: Int = 0, player: ServerPlayer? = null, server: MinecraftServer? = null): CommandSourceStack {
        val source = mockk<CommandSourceStack>(relaxed = true)
        every { source.permissions() } returns LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level))
        every { source.entity } returns player
        every { source.player } returns player
        if (server != null) every { source.server } returns server
        return source
    }

    fun ensureBooted() {
        // touching the object runs init
    }
}
