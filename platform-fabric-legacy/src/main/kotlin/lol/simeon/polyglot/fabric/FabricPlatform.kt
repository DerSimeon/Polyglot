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

package lol.simeon.polyglot.fabric

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import lol.simeon.polyglot.minecraft.MinecraftCommandManager
import lol.simeon.polyglot.minecraft.MinecraftPermissions
import lol.simeon.polyglot.minecraft.OpLevelPermissionResolver
import lol.simeon.polyglot.permission.PermissionResolver
import lol.simeon.polyglot.minecraft.MinecraftSender
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.loader.api.FabricLoader

/**
 * Fabric entry point. Create a [MinecraftCommandManager] with [createManager], register your
 * commands, then [install] it from your mod initializer:
 *
 * ```kotlin
 * val manager = FabricPlatform.createManager()
 * manager.register(PartyCommand())
 * FabricPlatform.install(manager)
 * ```
 */
public object FabricPlatform {

    /** Mod id of fabric-permissions-api, picked up automatically when present. */
    public const val PERMISSIONS_API_MOD_ID: String = "fabric-permissions-api-v0"

    /** Whether fabric-permissions-api is loaded. */
    public val permissionsApiLoaded: Boolean
        get() = FabricLoader.getInstance().isModLoaded(PERMISSIONS_API_MOD_ID)

    /**
     * Creates a manager whose `@Permission` nodes resolve through fabric-permissions-api when
     * that mod is loaded, and through operator [fallbackOpLevel] otherwise.
     */
    public fun createManager(
        fallbackOpLevel: Int = MinecraftPermissions.DEFAULT_LEVEL,
        coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    ): MinecraftCommandManager = MinecraftCommandManager(permissionResolver(fallbackOpLevel), coroutineScope)

    /** The resolver [createManager] would pick for [fallbackOpLevel]. */
    public fun permissionResolver(fallbackOpLevel: Int = MinecraftPermissions.DEFAULT_LEVEL): PermissionResolver<MinecraftSender> =
        if (permissionsApiLoaded) FabricPermissionsResolver(fallbackOpLevel) else OpLevelPermissionResolver(fallbackOpLevel)

    /** Publishes the manager's commands whenever the server (re)builds its command tree. */
    public fun install(manager: MinecraftCommandManager) {
        CommandRegistrationCallback.EVENT.register { dispatcher, buildContext, _ ->
            manager.register(dispatcher, buildContext)
        }
    }
}
