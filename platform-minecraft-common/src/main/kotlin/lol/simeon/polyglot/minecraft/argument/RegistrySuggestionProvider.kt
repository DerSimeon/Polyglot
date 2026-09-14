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

package lol.simeon.polyglot.minecraft.argument

import lol.simeon.polyglot.minecraft.MinecraftSender
import lol.simeon.polyglot.suggestion.Suggestion
import lol.simeon.polyglot.suggestion.SuggestionContext
import lol.simeon.polyglot.suggestion.SuggestionProvider
import net.minecraft.core.Registry

/**
 * Suggests the ids of a [Registry] (looked up lazily); the `minecraft:` namespace may be omitted
 * while typing.
 */
public class RegistrySuggestionProvider(registry: () -> Registry<*>) : SuggestionProvider<MinecraftSender> {

    private val registry: Registry<*> by lazy(registry)

    override suspend fun suggest(context: SuggestionContext<MinecraftSender>): List<Suggestion> {
        val partial = context.input
        return registry.keySet()
            .asSequence()
            .map { it.toString() }
            .filter { id ->
                id.startsWith(partial, ignoreCase = true) ||
                    id.removePrefix(DEFAULT_NAMESPACE).startsWith(partial, ignoreCase = true)
            }
            .map { Suggestion(it) }
            .toList()
    }

    private companion object {
        const val DEFAULT_NAMESPACE = "minecraft:"
    }
}
