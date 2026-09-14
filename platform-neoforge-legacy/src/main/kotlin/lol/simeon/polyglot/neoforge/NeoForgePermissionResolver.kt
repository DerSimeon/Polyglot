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

import lol.simeon.polyglot.minecraft.MinecraftPermissions
import lol.simeon.polyglot.minecraft.MinecraftSender
import lol.simeon.polyglot.permission.PermissionResolver
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.server.permission.PermissionAPI
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent
import net.neoforged.neoforge.server.permission.nodes.PermissionNode
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves string `@Permission` nodes through NeoForge's `PermissionAPI`, so permission mods can
 * grant them per player. Every node is exposed as a boolean [PermissionNode] named
 * `<namespace>:<node>` (a node that already contains `:` is used verbatim) whose default — and the
 * answer for non-player sources or nodes no handler knows yet — is operator [fallbackLevel].
 *
 * Nodes must be registered during [PermissionGatherEvent.Nodes]; [NeoForgePlatform.install] does
 * that for every node declared by the manager's commands.
 */
public class NeoForgePermissionResolver(
    public val namespace: String = DEFAULT_NAMESPACE,
    public val fallbackLevel: Int = MinecraftPermissions.DEFAULT_LEVEL,
) : PermissionResolver<MinecraftSender> {

    private val nodes = ConcurrentHashMap<String, PermissionNode<Boolean>>()

    /** The [PermissionNode] backing [node], created on first use. */
    public fun nodeFor(node: String): PermissionNode<Boolean> = nodes.computeIfAbsent(node) { name ->
        val id = if (':' in name) Identifier.parse(name) else Identifier.fromNamespaceAndPath(namespace, name)
        PermissionNode(id, PermissionTypes.BOOLEAN, { player, _, _ ->
            player != null && MinecraftPermissions.hasLevel(player.createCommandSourceStack(), fallbackLevel)
        })
    }

    /** Registers the nodes for [nodeNames] with [event]. */
    public fun gather(event: PermissionGatherEvent.Nodes, nodeNames: Collection<String>) {
        event.addNodes(nodeNames.map { nodeFor(it) })
    }

    override fun hasPermission(sender: MinecraftSender, node: String): Boolean {
        val player = sender.player ?: return MinecraftPermissions.hasLevel(sender.source, fallbackLevel)
        val permissionNode = nodeFor(node)
        if (permissionNode !in PermissionAPI.getRegisteredNodes()) {
            return MinecraftPermissions.hasLevel(sender.source, fallbackLevel)
        }
        return PermissionAPI.getPermission(player, permissionNode)
    }

    public companion object {
        /** Namespace used for nodes that do not carry one. */
        public const val DEFAULT_NAMESPACE: String = "polyglot"
    }
}
