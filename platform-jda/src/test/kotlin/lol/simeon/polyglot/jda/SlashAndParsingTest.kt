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

package lol.simeon.polyglot.jda

import lol.simeon.polyglot.dsl.command
import net.dv8tion.jda.api.interactions.commands.OptionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SlashAndParsingTest {

    private enum class Speed { SLOW, FAST }

    @Test
    fun `message parser strips longest matching prefix`() {
        assertEquals(listOf("ban", "spammer"), MessageCommandParser.parse("!!ban spammer", listOf("!", "!!")))
        assertNull(MessageCommandParser.parse("hello", listOf("!")))
        assertNull(MessageCommandParser.parse("!", listOf("!")))
    }

    @Test
    fun `option type mapper covers primitives entities and enums`() {
        assertEquals(OptionType.INTEGER, OptionTypeMapper.map(Int::class))
        assertEquals(OptionType.NUMBER, OptionTypeMapper.map(Double::class))
        assertEquals(OptionType.BOOLEAN, OptionTypeMapper.map(Boolean::class))
        assertEquals(OptionType.STRING, OptionTypeMapper.map(String::class))
        assertEquals(OptionType.STRING, OptionTypeMapper.map(Speed::class))
    }

    @Test
    fun `slash builder maps subcommands groups and options`() {
        val tree = command<Unit>("weather") {
            description("Weather commands")
            subcommand("today") {
                argument<String>("city")
                argument<Speed>("speed")
                executes { }
            }
            subcommand("region") {
                subcommand("forecast") {
                    argument<Int>("days", optional = true)
                    executes { }
                }
            }
        }

        val data = SlashCommandTreeBuilder().build(tree)

        assertEquals("weather", data.name)
        assertEquals(listOf("today"), data.subcommands.map { it.name })
        assertEquals(listOf("region"), data.subcommandGroups.map { it.name })

        val today = data.subcommands.single { it.name == "today" }
        assertEquals(listOf("city", "speed"), today.options.map { it.name })
        val speed = today.options.single { it.name == "speed" }
        assertEquals(OptionType.STRING, speed.type)
        assertEquals(listOf("SLOW", "FAST"), speed.choices.map { it.asString })

        val forecast = data.subcommandGroups.single().subcommands.single { it.name == "forecast" }
        assertFalse(forecast.options.single { it.name == "days" }.isRequired)
    }

    @Test
    fun `slash builder flattens nesting beyond discord depth into hyphen names`() {
        val tree = command<Unit>("root") {
            subcommand("group") {
                subcommand("deep") {
                    subcommand("leaf") { executes { } }
                }
            }
        }

        val data = SlashCommandTreeBuilder().build(tree)

        val group = data.subcommandGroups.single { it.name == "group" }
        assertEquals(listOf("deep-leaf"), group.subcommands.map { it.name })
    }

    @Test
    fun `slash builder rejects hyphen in command names`() {
        val tree = command<Unit>("root") {
            subcommand("bad-name") { executes { } }
        }
        assertThrows<IllegalArgumentException> { SlashCommandTreeBuilder().build(tree) }
    }

    @Test
    fun `slash builder maps flags to boolean options`() {
        val tree = command<Unit>("cfg") {
            subcommand("set") {
                flag("force")
                executes { }
            }
        }
        val option = SlashCommandTreeBuilder().build(tree).subcommands.single().options.single()
        assertEquals("force", option.name)
        assertEquals(OptionType.BOOLEAN, option.type)
        assertFalse(option.isRequired)
    }
}
