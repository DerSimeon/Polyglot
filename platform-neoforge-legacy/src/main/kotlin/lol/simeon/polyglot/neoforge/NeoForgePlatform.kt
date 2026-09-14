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

package lol.simeon.polyglot.neoforge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import lol.simeon.polyglot.minecraft.MinecraftCommandManager
import lol.simeon.polyglot.minecraft.MinecraftPermissions
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent

/**
 * NeoForge entry point. Create a [MinecraftCommandManager] with [createManager], register your
 * commands, then [install] it during mod construction:
 *
 * ```kotlin
 * val manager = NeoForgePlatform.createManager(namespace = MOD_ID)
 * manager.register(PartyCommand())
 * NeoForgePlatform.install(manager)
 * ```
 */
public object NeoForgePlatform {

    /**
     * Creates a manager whose `@Permission` nodes are exposed through NeoForge's `PermissionAPI`
     * as `<namespace>:<node>`, defaulting to operator [fallbackOpLevel].
     */
    public fun createManager(
        namespace: String = NeoForgePermissionResolver.DEFAULT_NAMESPACE,
        fallbackOpLevel: Int = MinecraftPermissions.DEFAULT_LEVEL,
        coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    ): MinecraftCommandManager =
        MinecraftCommandManager(NeoForgePermissionResolver(namespace, fallbackOpLevel), coroutineScope)

    /**
     * Publishes the manager's commands on every [RegisterCommandsEvent] and, when the manager uses a
     * [NeoForgePermissionResolver], registers its permission nodes on [PermissionGatherEvent.Nodes].
     */
    public fun install(manager: MinecraftCommandManager, bus: IEventBus = NeoForge.EVENT_BUS) {
        bus.addListener(RegisterCommandsEvent::class.java) { event ->
            manager.register(event.dispatcher, event.buildContext)
        }
        val resolver = manager.permissionResolver as? NeoForgePermissionResolver ?: return
        bus.addListener(PermissionGatherEvent.Nodes::class.java) { event ->
            resolver.gather(event, manager.permissionNodes())
        }
    }
}
