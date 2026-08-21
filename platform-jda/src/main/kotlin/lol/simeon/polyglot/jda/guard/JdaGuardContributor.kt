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

import lol.simeon.polyglot.guard.CommandGuard
import lol.simeon.polyglot.jda.JdaSender
import lol.simeon.polyglot.jda.annotation.GuildOnly
import lol.simeon.polyglot.jda.annotation.RequirePermissions
import lol.simeon.polyglot.scanner.GuardContributor
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

/** Translates the JDA `@RequirePermissions` and `@GuildOnly` annotations into runtime guards. */
public class JdaGuardContributor : GuardContributor<JdaSender> {
    override fun contribute(element: KAnnotatedElement): List<CommandGuard<JdaSender>> {
        val guards = mutableListOf<CommandGuard<JdaSender>>()
        element.findAnnotation<RequirePermissions>()
            ?.value
            ?.takeIf { it.isNotEmpty() }
            ?.let { guards += RequirePermissionsGuard(it.toList()) }
        if (element.hasAnnotation<GuildOnly>()) guards += GuildOnlyGuard
        return guards
    }
}
