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

package lol.simeon.polyglot.scanner

import lol.simeon.polyglot.context.CommandContext
import lol.simeon.polyglot.model.CommandHandler
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.isAccessible

/**
 * Invokes an annotated command function via reflection, binding the sender (or the whole
 * [CommandContext]) to its first parameter and the parsed arguments to the remainder. Supports both
 * `suspend` and regular functions.
 */
public class ReflectiveCommandHandler<S>(
    private val instance: Any,
    private val function: KFunction<*>,
    private val receiverParameter: KParameter?,
    private val injectContext: Boolean,
    private val argumentParameters: Map<String, KParameter>,
) : CommandHandler<S> {

    init {
        // allow commands declared in encapsulated (private/internal) classes to be invoked
        function.isAccessible = true
    }

    @Suppress("SpreadOperator")
    override suspend fun handle(context: CommandContext<S>) {
        val call = HashMap<KParameter, Any?>()
        function.instanceParameter?.let { call[it] = instance }
        receiverParameter?.let { call[it] = if (injectContext) context else context.sender }

        for ((name, parameter) in argumentParameters) {
            val value = context.getOrNull<Any?>(name)
            // absent optional -> leave unbound so the Kotlin default applies
            if (value == null && parameter.isOptional) continue
            call[parameter] = value
        }

        if (function.isSuspend) {
            function.callSuspendBy(call)
        } else {
            function.callBy(call)
        }
    }
}
