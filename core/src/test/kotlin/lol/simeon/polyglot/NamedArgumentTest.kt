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

import kotlinx.coroutines.test.runTest
import lol.simeon.polyglot.annotation.Command
import lol.simeon.polyglot.annotation.Flag
import lol.simeon.polyglot.annotation.Named
import lol.simeon.polyglot.dsl.command
import lol.simeon.polyglot.exception.MissingArgumentException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NamedArgumentTest {

    @Command(name = "deploy")
    class DeployCommand {
        @Command(name = "run")
        fun run(
            sender: TestSender,
            service: String,
            @Named("count", shorthand = 'c') count: Int,
            @Named("region") region: String?,
            @Flag("dry", shorthand = 'd') dry: Boolean,
        ) {
            CommandFixtures.calls += "run:$service:$count:$region:$dry"
        }
    }

    private lateinit var manager: TestCommandManager

    @BeforeEach
    fun setUp() {
        CommandFixtures.calls.clear()
        manager = TestCommandManager()
        manager.register(DeployCommand())
    }

    @Test
    fun `binds named options in any order with positional`() = runTest {
        manager.dispatch(TestSender(), listOf("deploy", "run", "api", "--count", "3", "--region", "eu"))
        manager.dispatch(TestSender(), listOf("deploy", "run", "--region", "us", "--count", "1", "web"))
        assertEquals(listOf("run:api:3:eu:false", "run:web:1:us:false"), CommandFixtures.calls)
    }

    @Test
    fun `supports shorthand and boolean flag presence`() = runTest {
        manager.dispatch(TestSender(), listOf("deploy", "run", "api", "-c", "5", "-d"))
        assertEquals(listOf("run:api:5:null:true"), CommandFixtures.calls)
    }

    @Test
    fun `absent flag binds false and optional named binds null`() = runTest {
        manager.dispatch(TestSender(), listOf("deploy", "run", "api", "--count", "2"))
        assertEquals(listOf("run:api:2:null:false"), CommandFixtures.calls)
    }

    @Test
    fun `missing required named option throws`() = runTest {
        assertThrows<MissingArgumentException> {
            manager.dispatch(TestSender(), listOf("deploy", "run", "api"))
        }
    }

    @Test
    fun `dsl named and flag parity`() = runTest {
        manager.register(
            command<TestSender>("scale") {
                namedArgument<Int>("replicas", shorthand = 'r')
                flag("force", shorthand = 'f')
                executes { ctx ->
                    CommandFixtures.calls += "scale:${ctx.get<Int>("replicas")}:${ctx.get<Boolean>("force")}"
                }
            },
        )
        manager.dispatch(TestSender(), listOf("scale", "-r", "4", "--force"))
        assertEquals(listOf("scale:4:true"), CommandFixtures.calls)
    }

    @Test
    fun `completer suggests option names on dash`() = runTest {
        val values = manager.complete(TestSender(), listOf("deploy", "run", "api", "--")).map { it.value }
        assertTrue("--count" in values)
        assertTrue("--region" in values)
        assertTrue("--dry" in values)
    }
}
