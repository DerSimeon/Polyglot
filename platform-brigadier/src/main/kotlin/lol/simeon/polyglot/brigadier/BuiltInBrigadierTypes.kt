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

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import lol.simeon.polyglot.model.CommandArgument

/**
 * Native Brigadier mappings for the primitive types the core engine supports. Numeric bounds from
 * `@Range` become Brigadier bounds, so the client rejects out-of-range input before it reaches the
 * server. Strings use [StringArgumentType.string] (quotable) or [StringArgumentType.greedyString]
 * for `@Greedy` arguments.
 */
public object BuiltInBrigadierTypes {

    /** Installs every built-in mapping into [registry]. */
    public fun <Src> install(registry: BrigadierTypeRegistry<Src>) {
        registry.register<Int>(
            type = { arg ->
                IntegerArgumentType.integer(
                    arg.range?.start?.toInt() ?: Int.MIN_VALUE,
                    arg.range?.endInclusive?.toInt() ?: Int.MAX_VALUE,
                )
            },
            extract = { ctx, name -> IntegerArgumentType.getInteger(ctx, name) },
        )
        registry.register<Long>(
            type = { arg ->
                LongArgumentType.longArg(
                    arg.range?.start?.toLong() ?: Long.MIN_VALUE,
                    arg.range?.endInclusive?.toLong() ?: Long.MAX_VALUE,
                )
            },
            extract = { ctx, name -> LongArgumentType.getLong(ctx, name) },
        )
        registry.register<Double>(
            type = { arg ->
                DoubleArgumentType.doubleArg(
                    arg.range?.start ?: -Double.MAX_VALUE,
                    arg.range?.endInclusive ?: Double.MAX_VALUE,
                )
            },
            extract = { ctx, name -> DoubleArgumentType.getDouble(ctx, name) },
        )
        registry.register<Float>(
            type = { arg ->
                FloatArgumentType.floatArg(
                    arg.range?.start?.toFloat() ?: -Float.MAX_VALUE,
                    arg.range?.endInclusive?.toFloat() ?: Float.MAX_VALUE,
                )
            },
            extract = { ctx, name -> FloatArgumentType.getFloat(ctx, name) },
        )
        registry.register<Boolean>(
            type = { BoolArgumentType.bool() },
            extract = { ctx, name -> BoolArgumentType.getBool(ctx, name) },
        )
        registry.register<String>(
            type = { arg -> stringType(arg) },
            extract = { ctx, name -> StringArgumentType.getString(ctx, name) },
        )
    }

    /** The string node used for `String` arguments and for every type without a native mapping. */
    public fun stringType(arg: CommandArgument): StringArgumentType =
        if (arg.greedy) StringArgumentType.greedyString() else StringArgumentType.string()
}
