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

package lol.simeon.polyglot.argument

import kotlin.reflect.KClass

/**
 * Holds the [ArgumentParser] for each supported argument type. Enum types resolve to a shared
 * [EnumArgumentParser] on demand, so consumers never register enums explicitly.
 */
public class ParserRegistry<S> {
    private val byType: MutableMap<KClass<*>, ArgumentParser<S, *>> = HashMap()

    /** Registers [parser] as the handler for [type], replacing any previous binding. */
    public fun <T : Any> register(type: KClass<T>, parser: ArgumentParser<S, T>) {
        byType[type] = parser
    }

    /** Convenience for reified registration. */
    public inline fun <reified T : Any> register(parser: ArgumentParser<S, T>) {
        register(T::class, parser)
    }

    /** Whether a parser (explicit or enum-derived) exists for [type]. */
    public fun supports(type: KClass<*>): Boolean = parserFor(type) != null

    /** Resolves the parser for [type], falling back to a cached enum parser for enum classes. */
    @Suppress("UNCHECKED_CAST")
    public fun parserFor(type: KClass<*>): ArgumentParser<S, *>? {
        byType[type]?.let { return it }
        if (type.java.isEnum) {
            val parser = EnumArgumentParser<S>(type.java as Class<out Enum<*>>)
            byType[type] = parser
            return parser
        }
        return null
    }
}
