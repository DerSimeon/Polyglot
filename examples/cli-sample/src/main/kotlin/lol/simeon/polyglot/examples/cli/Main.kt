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

package lol.simeon.polyglot.examples.cli

import kotlinx.coroutines.runBlocking
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.annotation.Flag
import lol.simeon.polyglot.cli.CliCommandManager
import lol.simeon.polyglot.cli.CommandSender
import lol.simeon.polyglot.cli.ConsoleCommandSender

@Command(name = "greet", description = "Greeting commands")
class GreetCommand {
    @Command(name = "hello")
    fun hello(sender: CommandSender, name: String, @Flag("loud", shorthand = 'l') loud: Boolean) {
        val message = "Hello, $name!"
        sender.sendMessage(if (loud) message.uppercase() else message)
    }
}

fun main() {
    val manager = CliCommandManager()
    manager.register(GreetCommand())
    val console = ConsoleCommandSender()

    runBlocking {
        manager.execute(console, """greet hello "Ada Lovelace"""")
        manager.execute(console, "greet hello World --loud")
        manager.execute(console, "greet hello") // missing arg -> reports an error
        println("completions for 'greet ': " + manager.completeLine(console, "greet ").joinToString { it.value })
    }
}
