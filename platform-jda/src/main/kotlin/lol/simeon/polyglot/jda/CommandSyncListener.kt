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

package lol.simeon.polyglot.jda

import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/**
 * Pushes [manager]'s registered commands to Discord automatically once JDA is ready, removing the
 * need for a hand-written ready handler. Register it alongside [JdaCommandManager.eventListener].
 *
 * - [PushType.GLOBAL] syncs once on `ReadyEvent`.
 * - [PushType.GUILD] syncs on each `GuildReadyEvent`.
 * - [PushType.NONE] never syncs.
 *
 * Usable from Java (`new CommandSyncListener(manager, PushType.GLOBAL)`); Kotlin callers can also
 * obtain one via [JdaCommandManager.commandSyncListener].
 */
public class CommandSyncListener(
    private val manager: JdaCommandManager,
    private val pushType: PushType,
) : ListenerAdapter() {

    override fun onReady(event: ReadyEvent) {
        if (pushType == PushType.GLOBAL) manager.updateGlobalCommands(event.jda)
    }

    override fun onGuildReady(event: GuildReadyEvent) {
        if (pushType == PushType.GUILD) manager.updateGuildCommands(event.guild)
    }
}
