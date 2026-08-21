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

import lol.simeon.polyglot.model.CommandArgument
import lol.simeon.polyglot.model.CommandNode
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData

/**
 * Converts a [CommandNode] tree into JDA [SlashCommandData]. Discord limits nesting to
 * group → subcommand (depth 2), so paths deeper than that are flattened: a leaf at
 * `root → a → b → c` becomes command `root`, group `a`, subcommand `b-c`. The manager reverses this
 * mapping on dispatch. Command/subcommand names therefore must not contain '-'.
 */
public class SlashCommandTreeBuilder {
    private companion object {
        const val MAX_CHOICES = 25
        const val MAX_DESCRIPTION = 100
    }

    public fun build(root: CommandNode<*>): SlashCommandData {
        validateNames(root)
        val data = Commands.slash(root.name.lowercase(), describe(root))

        val leaves = mutableListOf<Pair<List<String>, CommandNode<*>>>()
        collect(root, emptyList(), leaves)

        if (leaves.size == 1 && leaves.single().first.isEmpty()) {
            return data.addOptions(optionsFor(root))
        }

        leaves.filter { it.first.size == 1 }.forEach { (path, node) ->
            data.addSubcommands(subcommand(path.last(), node))
        }
        leaves.filter { it.first.size >= 2 }
            .groupBy { it.first.first() }
            .forEach { (group, members) ->
                val groupData = SubcommandGroupData(group.lowercase(), group.take(MAX_DESCRIPTION))
                members.forEach { (path, node) ->
                    groupData.addSubcommands(subcommand(path.drop(1).joinToString("-"), node))
                }
                data.addSubcommandGroups(groupData)
            }
        return data
    }

    private fun collect(
        node: CommandNode<*>,
        path: List<String>,
        out: MutableList<Pair<List<String>, CommandNode<*>>>,
    ) {
        if (node.childNodes.isEmpty() && node.executable) {
            out += path to node
            return
        }
        // a group's own default handler is not representable as a slash subcommand; only leaves are
        node.childNodes.forEach { collect(it, path + it.name, out) }
    }

    private fun validateNames(node: CommandNode<*>) {
        require('-' !in node.name) {
            "Command name '${node.name}' must not contain '-' (reserved as the slash-flattening separator)"
        }
        node.childNodes.forEach { validateNames(it) }
    }

    private fun subcommand(name: String, node: CommandNode<*>): SubcommandData =
        SubcommandData(name.lowercase(), describe(node)).addOptions(optionsFor(node))

    private fun optionsFor(node: CommandNode<*>): List<OptionData> = node.arguments.map { option(it) }

    private fun option(argument: CommandArgument): OptionData {
        val type = if (argument.flag) OptionType.BOOLEAN else OptionTypeMapper.map(argument.type)
        val description = argument.description.ifEmpty { argument.name }.take(MAX_DESCRIPTION)
        val option = OptionData(type, argument.name.lowercase(), description, !argument.optional)

        val enumChoices = if (argument.type.java.isEnum) {
            argument.type.java.enumConstants.map { (it as Enum<*>).name }
        } else {
            emptyList()
        }
        val choices = (argument.choices + enumChoices).distinct().take(MAX_CHOICES)

        when {
            choices.isNotEmpty() && type == OptionType.STRING -> choices.forEach { option.addChoice(it, it) }
            argument.suggestionId != null && type == OptionType.STRING -> option.isAutoComplete = true
        }
        return option
    }

    private fun describe(node: CommandNode<*>): String =
        node.description.ifEmpty { node.name }.take(MAX_DESCRIPTION)
}
