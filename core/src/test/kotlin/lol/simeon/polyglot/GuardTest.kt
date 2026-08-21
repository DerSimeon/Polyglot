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

package lol.simeon.polyglot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.dsl.command
import lol.simeon.polyglot.exception.GuardRejectedException
import lol.simeon.polyglot.guard.CommandGuard
import lol.simeon.polyglot.model.CommandNode
import lol.simeon.polyglot.scanner.GuardContributor
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.full.hasAnnotation

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
private annotation class Blocked

/** Manager whose contributor turns a `@Blocked` annotation into a rejecting guard. */
private class GuardManager : CommandManager<TestSender>() {
    override val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    override val guardContributors: List<GuardContributor<TestSender>> = listOf(
        GuardContributor { element ->
            if (element.hasAnnotation<Blocked>()) {
                listOf(CommandGuard { throw GuardRejectedException("blocked") })
            } else {
                emptyList()
            }
        },
    )

    override fun registerNative(node: CommandNode<TestSender>) = Unit
}

@Command(name = "root")
private class RootCommand {
    @Command(name = "open")
    fun open(sender: TestSender) = Unit

    @Command(name = "secret")
    @Blocked
    fun secret(sender: TestSender) = Unit
}

class GuardTest {

    @Test
    fun `contributed guard rejects the annotated leaf`() = runTest {
        val manager = GuardManager().apply { register(RootCommand()) }
        assertThrows<GuardRejectedException> {
            manager.dispatch(TestSender(), listOf("root", "secret"))
        }
        // unguarded sibling still runs
        manager.dispatch(TestSender(), listOf("root", "open"))
    }

    @Test
    fun `guarded subcommand is hidden from completion`() = runTest {
        val manager = GuardManager().apply { register(RootCommand()) }
        val names = manager.complete(TestSender(), listOf("root", "")).map { it.value }
        assertTrue("open" in names)
        assertFalse("secret" in names)
    }

    @Test
    fun `dsl guard blocks execution`() = runTest {
        val manager = GuardManager()
        manager.register(
            command<TestSender>("dsl") {
                guard(CommandGuard { throw GuardRejectedException("no") })
                executes { }
            },
        )
        assertThrows<GuardRejectedException> {
            manager.dispatch(TestSender(), listOf("dsl"))
        }
    }
}
