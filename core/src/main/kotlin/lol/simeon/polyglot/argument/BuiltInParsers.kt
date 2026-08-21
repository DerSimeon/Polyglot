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

/**
 * Installs parsers for the Kotlin/Java primitive and [String] types. Enum types are handled
 * separately by [ParserRegistry] via [EnumArgumentParser].
 */
public object BuiltInParsers {
    public fun <S> install(registry: ParserRegistry<S>) {
        registry.register(String::class, LambdaArgumentParser("String") { it })
        registry.register(Boolean::class, LambdaArgumentParser("Boolean", ::parseBoolean))
        registry.register(Char::class, LambdaArgumentParser("Char") { requireSingleChar(it) })
        registry.register(Byte::class, LambdaArgumentParser("Byte", String::toByte))
        registry.register(Short::class, LambdaArgumentParser("Short", String::toShort))
        registry.register(Int::class, LambdaArgumentParser("Int", String::toInt))
        registry.register(Long::class, LambdaArgumentParser("Long", String::toLong))
        registry.register(Float::class, LambdaArgumentParser("Float", String::toFloat))
        registry.register(Double::class, LambdaArgumentParser("Double", String::toDouble))
    }

    private fun parseBoolean(token: String): Boolean = when (token.lowercase()) {
        "true", "yes", "y", "on", "1" -> true
        "false", "no", "n", "off", "0" -> false
        else -> throw IllegalArgumentException("not a boolean")
    }

    private fun requireSingleChar(token: String): Char {
        require(token.length == 1) { "expected a single character" }
        return token[0]
    }
}
