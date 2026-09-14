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

package lol.simeon.polyglot.brigadier

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import kotlin.reflect.KClass

/**
 * Holds the [ArgumentTypeMapping] for every argument type that has a native Brigadier
 * representation. Types without a mapping fall back to a string node parsed by the core
 * [lol.simeon.polyglot.argument.ArgumentParser]. Consumer-extensible.
 */
public class BrigadierTypeRegistry<Src> {
    private val byType: MutableMap<KClass<*>, ArgumentTypeMapping<Src, *>> = HashMap()

    /** Registers [mapping] for [type], replacing any previous binding. */
    public fun <T : Any> register(type: KClass<T>, mapping: ArgumentTypeMapping<Src, T>) {
        byType[type] = mapping
    }

    /** Convenience for reified registration. */
    public inline fun <reified T : Any> register(
        noinline type: (lol.simeon.polyglot.model.CommandArgument) -> ArgumentType<*>,
        noinline extract: (CommandContext<Src>, String) -> T,
    ) {
        register(T::class, ArgumentTypeMapping(type, extract))
    }

    /** Resolves the mapping for [type], or `null` when the type has no native representation. */
    public fun mappingFor(type: KClass<*>): ArgumentTypeMapping<Src, *>? = byType[type]

    /** Whether [type] maps to a native Brigadier argument. */
    public fun supports(type: KClass<*>): Boolean = type in byType

    /** Copies every mapping into [target], overriding bindings of the same type. */
    public fun copyInto(target: BrigadierTypeRegistry<Src>) {
        target.byType.putAll(byType)
    }
}
