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

package lol.simeon.polyglot.jda.ktx

import lol.simeon.polyglot.jda.JdaCommandManager
import lol.simeon.polyglot.jda.PrefixProvider
import lol.simeon.polyglot.jda.PushType
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder

/**
 * Wires [manager] into this builder: adds its interaction/message listener and, unless [pushType]
 * is [PushType.NONE], an auto-sync listener that pushes commands once JDA is ready. Returns this
 * builder for chaining. Use when you already hold a [JdaCommandManager] reference.
 */
public fun JDABuilder.withPolyglot(
    manager: JdaCommandManager,
    pushType: PushType = PushType.NONE,
): JDABuilder = apply {
    addEventListeners(manager.eventListener)
    if (pushType != PushType.NONE) {
        addEventListeners(manager.commandSyncListener(pushType))
    }
}

/**
 * Creates a [JdaCommandManager], registers commands via [configure], and wires it into this builder
 * (see the manager overload). Returns this builder for chaining, so the whole bot bootstrap reads:
 *
 * ```
 * val jda = JDABuilder.createDefault(token)
 *     .withPolyglot(PushType.GLOBAL) { register(PollCommand()) }
 *     .enableIntents(GatewayIntent.MESSAGE_CONTENT)
 *     .build()
 * ```
 *
 * Pass a [prefixProvider] to enable prefix (message) commands.
 */
public fun JDABuilder.withPolyglot(
    pushType: PushType = PushType.NONE,
    prefixProvider: PrefixProvider = PrefixProvider { emptyList() },
    configure: JdaCommandManager.() -> Unit,
): JDABuilder {
    val manager = JdaCommandManager(prefixProvider).apply(configure)
    return withPolyglot(manager, pushType)
}

/**
 * Wires [manager] into an already-built JDA: adds its listener and, per [pushType], pushes commands
 * immediately against the live session ([PushType.GLOBAL]) or every connected guild
 * ([PushType.GUILD]). Returns this JDA for chaining.
 */
public fun JDA.withPolyglot(
    manager: JdaCommandManager,
    pushType: PushType = PushType.NONE,
): JDA = apply {
    addEventListener(manager.eventListener)
    when (pushType) {
        PushType.GLOBAL -> manager.updateGlobalCommands(this)
        PushType.GUILD -> guilds.forEach(manager::updateGuildCommands)
        PushType.NONE -> Unit
    }
}
