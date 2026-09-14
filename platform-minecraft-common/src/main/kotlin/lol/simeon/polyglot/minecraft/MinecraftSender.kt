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

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

/** Wraps a vanilla [CommandSourceStack] (console, player, command block, …) as a Polyglot sender. */
public class MinecraftSender(public val source: CommandSourceStack) {
    /** The executing player, or `null` when the source is not a player. */
    public val player: ServerPlayer? get() = source.entity as? ServerPlayer

    /** The executing entity, or `null` for the console / RCON. */
    public val entity: Entity? get() = source.entity

    /** The level the command runs in. */
    public val level: ServerLevel get() = source.level

    /** The server the command runs on. */
    public val server: MinecraftServer get() = source.server

    /** Whether the source is a player. */
    public val isPlayer: Boolean get() = player != null

    /** Sends [message] to the source only (never broadcast to ops). */
    public fun reply(message: String) {
        reply(Component.literal(message))
    }

    /** Sends [message] to the source only (never broadcast to ops). */
    public fun reply(message: Component) {
        source.sendSuccess({ message }, false)
    }

    /** Sends [message] as a failure (rendered red) to the source. */
    public fun replyError(message: String) {
        replyError(Component.literal(message))
    }

    /** Sends [message] as a failure (rendered red) to the source. */
    public fun replyError(message: Component) {
        source.sendFailure(message)
    }
}
