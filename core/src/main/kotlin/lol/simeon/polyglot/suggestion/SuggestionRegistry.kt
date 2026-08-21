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

package lol.simeon.polyglot.suggestion

import kotlin.reflect.KClass

/**
 * Resolves [SuggestionProvider]s either by a named id (bound with `@Suggestion`) or by argument
 * type. Enum types resolve to a cached [EnumSuggestionProvider] automatically.
 */
public class SuggestionRegistry<S> {
    private val byId: MutableMap<String, SuggestionProvider<S>> = HashMap()
    private val byType: MutableMap<KClass<*>, SuggestionProvider<S>> = HashMap()

    /** Registers [provider] under the named [id] referenced by `@Suggestion(id)`. */
    public fun register(id: String, provider: SuggestionProvider<S>) {
        byId[id] = provider
    }

    /** Registers a default [provider] for every argument of [type]. */
    public fun <T : Any> register(type: KClass<T>, provider: SuggestionProvider<S>) {
        byType[type] = provider
    }

    /** Convenience for reified type registration. */
    public inline fun <reified T : Any> register(provider: SuggestionProvider<S>) {
        register(T::class, provider)
    }

    public fun forId(id: String): SuggestionProvider<S>? = byId[id]

    /** Resolves a provider for [type], creating an enum provider on demand. */
    public fun forType(type: KClass<*>): SuggestionProvider<S>? {
        byType[type]?.let { return it }
        if (type.java.isEnum) {
            @Suppress("UNCHECKED_CAST")
            val provider = EnumSuggestionProvider<S>(type.java as Class<out Enum<*>>)
            byType[type] = provider
            return provider
        }
        return null
    }
}
