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

package lol.simeon.polyglot.message

import lol.simeon.polyglot.exception.ArgumentParseException
import lol.simeon.polyglot.exception.CommandExecutionException
import lol.simeon.polyglot.exception.GuardRejectedException
import lol.simeon.polyglot.exception.MissingArgumentException
import lol.simeon.polyglot.exception.NoPermissionException
import lol.simeon.polyglot.exception.PolyglotException
import lol.simeon.polyglot.exception.UnknownCommandException
import java.util.ResourceBundle

/**
 * Formats errors from a [ResourceBundle]. Templates use positional placeholders `{0}`, `{1}`, …;
 * for example `polyglot.invalidArgument=Invalid value '{0}' for '{1}' (expected {2})`. Falls back to
 * the exception's own message when a key is missing.
 */
public class ResourceBundleMessageProvider<S>(
    private val bundle: ResourceBundle,
) : MessageProvider<S> {

    override fun format(sender: S, error: PolyglotException): String {
        val (key, args) = describe(error)
        if (!bundle.containsKey(key.bundleKey)) return error.message ?: key.bundleKey
        return args.foldIndexed(bundle.getString(key.bundleKey)) { index, acc, value ->
            acc.replace("{$index}", value)
        }
    }

    private fun describe(error: PolyglotException): Pair<MessageKey, List<String>> = when (error) {
        is UnknownCommandException -> MessageKey.UNKNOWN_COMMAND to listOf(error.input)
        is NoPermissionException -> MessageKey.NO_PERMISSION to listOf(error.node)
        is MissingArgumentException -> MessageKey.MISSING_ARGUMENT to listOf(error.argumentName)
        is ArgumentParseException ->
            MessageKey.INVALID_ARGUMENT to listOf(error.input, error.argumentName, error.expectedType)
        is CommandExecutionException -> MessageKey.EXECUTION_ERROR to listOf(error.commandPath)
        is GuardRejectedException -> MessageKey.GUARD_REJECTED to listOf(error.reason)
    }
}
