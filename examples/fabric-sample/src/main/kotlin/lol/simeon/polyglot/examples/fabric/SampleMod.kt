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

package lol.simeon.polyglot.examples.fabric

import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.annotation.Flag
import lol.simeon.polyglot.annotation.Greedy
import lol.simeon.polyglot.annotation.Named
import lol.simeon.polyglot.annotation.Optional
import lol.simeon.polyglot.annotation.Permission
import lol.simeon.polyglot.annotation.Range
import lol.simeon.polyglot.fabric.FabricPlatform
import lol.simeon.polyglot.minecraft.MinecraftSender
import lol.simeon.polyglot.minecraft.annotation.OpLevel
import lol.simeon.polyglot.minecraft.annotation.PlayerOnly
import net.fabricmc.api.ModInitializer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

enum class Difficulty { PEACEFUL, EASY, NORMAL, HARD }

@Command(name = "party", description = "Party management")
class PartyCommand {

    @Command(name = "create", aliases = ["new"])
    fun create(sender: MinecraftSender, name: String, @Optional @Range(min = 1.0, max = 16.0) size: Int?) {
        sender.reply("Created '$name' (size ${size ?: 4})")
    }

    @Command(name = "difficulty")
    @Permission("party.difficulty")
    fun difficulty(sender: MinecraftSender, level: Difficulty) {
        sender.reply("Difficulty = $level")
    }

    @Command(name = "kit")
    @PlayerOnly
    fun kit(sender: MinecraftSender, item: Item, @Named("count", shorthand = 'c') count: Int?, @Flag("silent") silent: Boolean) {
        val player = sender.player ?: return
        player.inventory.add(ItemStack(item, count ?: 1))
        if (!silent) sender.reply("Gave ${count ?: 1}x ${item.descriptionId}")
    }

    @Command(name = "admin")
    @OpLevel(3)
    class Admin {
        @Command(name = "kick")
        fun kick(sender: MinecraftSender, target: ServerPlayer, @Greedy reason: String) {
            target.connection.disconnect(net.minecraft.network.chat.Component.literal(reason))
            sender.reply("Kicked ${target.gameProfile.name}: $reason")
        }
    }
}

object SampleMod : ModInitializer {
    override fun onInitialize() {
        val manager = FabricPlatform.createManager()
        manager.register(PartyCommand())
        FabricPlatform.install(manager)
    }
}
