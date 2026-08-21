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

package lol.simeon.polyglot.jda.guard

import lol.simeon.polyglot.exception.NoPermissionException
import lol.simeon.polyglot.guard.CommandGuard
import lol.simeon.polyglot.jda.JdaSender
import net.dv8tion.jda.api.Permission

/**
 * Rejects the invocation unless the member holds **all** of [permissions], checked against the
 * channel the command ran in (so per-channel overrides apply). Rejects outside a guild, since these
 * are guild permissions. Backs the `@RequirePermissions` annotation and the `requirePermissions`
 * DSL helper.
 */
public class RequirePermissionsGuard(
    public val permissions: List<Permission>,
) : CommandGuard<JdaSender> {

    override suspend fun check(sender: JdaSender) {
        val member = sender.member
        val channel = sender.guildChannel
        if (member == null || sender.guild == null || channel == null) {
            throw NoPermissionException(permissions.firstOrNull()?.name ?: "guild")
        }
        val missing = permissions.firstOrNull { !member.hasPermission(channel, it) }
        if (missing != null) throw NoPermissionException(missing.name)
    }
}
