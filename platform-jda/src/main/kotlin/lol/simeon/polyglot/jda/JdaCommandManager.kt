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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lol.simeon.polyglot.CommandManager
import lol.simeon.polyglot.exception.PolyglotException
import lol.simeon.polyglot.model.CommandArgument
import lol.simeon.polyglot.jda.guard.GuildOnlyGuard
import lol.simeon.polyglot.jda.guard.JdaGuardContributor
import lol.simeon.polyglot.jda.guard.RequirePermissionsGuard
import lol.simeon.polyglot.model.CommandNode
import lol.simeon.polyglot.scanner.GuardContributor
import lol.simeon.polyglot.suggestion.SuggestionContext
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

/**
 * JDA adapter supporting both slash commands and configurable prefix (message) commands over a
 * single command tree.
 *
 * Register [eventListener] with your JDA instance, then push slash definitions with
 * [buildSlashCommands] via `jda.updateCommands()` or `guild.updateCommands()`.
 *
 * Prefix commands require the `MESSAGE_CONTENT` gateway intent.
 */
public open class JdaCommandManager(
    private val prefixProvider: PrefixProvider = PrefixProvider { emptyList() },
    override val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : CommandManager<JdaSender>() {

    private val slashBuilder = SlashCommandTreeBuilder()

    override val guardContributors: List<GuardContributor<JdaSender>> = listOf(JdaGuardContributor())

    init {
        parsers.register(Member::class, MemberArgumentParser)
        parsers.register(Role::class, RoleArgumentParser)
        parsers.register(User::class, UserArgumentParser)
        suggestions.register(Member::class, MemberSuggestionProvider)
    }

    override fun registerNative(node: CommandNode<JdaSender>) {
        // slash definitions are pushed explicitly via buildSlashCommands(); nothing to do per-node
    }

    override fun onError(sender: JdaSender, error: PolyglotException) {
        sender.reply(formatError(sender, error), ephemeral = true)
    }

    /** Builds the slash-command payloads for every registered root command. */
    public fun buildSlashCommands(): List<SlashCommandData> =
        rootNodes().map { node -> syncNativeGuards(node, slashBuilder.build(node)) }

    /**
     * Mirrors a root command's runtime guards onto Discord's native command metadata so unauthorized
     * users don't even see it: `@RequirePermissions` becomes [DefaultMemberPermissions], `@GuildOnly`
     * a guild-only interaction context. Only root-level guards sync (Discord's limit); every node is
     * still enforced at runtime.
     */
    private fun syncNativeGuards(root: CommandNode<JdaSender>, data: SlashCommandData): SlashCommandData {
        val permissions = root.guards.filterIsInstance<RequirePermissionsGuard>().flatMap { it.permissions }
        if (permissions.isNotEmpty()) {
            data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(permissions))
        }
        if (root.guards.any { it === GuildOnlyGuard }) {
            data.setContexts(InteractionContextType.GUILD)
        }
        return data
    }

    /**
     * Pushes every registered command to [guild] only. Guild-scoped updates propagate instantly, so
     * this is preferred during development. For production use [updateGlobalCommands].
     */
    public fun updateGuildCommands(guild: Guild) {
        guild.updateCommands().addCommands(buildSlashCommands()).queue()
    }

    /**
     * Pushes every registered command globally. No guild is required. Global propagation can lag
     * (up to an hour), so prefer [updateGuildCommands] while iterating.
     */
    public fun updateGlobalCommands(jda: JDA) {
        jda.updateCommands().addCommands(buildSlashCommands()).queue()
    }

    /**
     * Builds a [ListenerAdapter] that pushes commands automatically once JDA is ready, per [pushType]:
     * [PushType.GLOBAL] syncs on `ReadyEvent`, [PushType.GUILD] on each `GuildReadyEvent`,
     * [PushType.NONE] does nothing. Add it alongside [eventListener] to skip a hand-written handler.
     */
    public fun commandSyncListener(pushType: PushType): ListenerAdapter = CommandSyncListener(this, pushType)

    /**
     * Builds a bot invite URL via [jda]. Aggregates the permissions declared through
     * `@RequirePermissions` on root commands — a heuristic: those are the *user* permissions a command
     * gates on, which usually match the *bot* permissions it needs — then adds
     * [Permission.USE_APPLICATION_COMMANDS] and any [additional] bot-only permissions (e.g.
     * [Permission.MESSAGE_HISTORY]).
     */
    public fun inviteUrl(jda: JDA, vararg additional: Permission): String {
        val declared = rootNodes()
            .flatMap { it.guards }
            .filterIsInstance<RequirePermissionsGuard>()
            .flatMap { it.permissions }
        val permissions = (declared + additional + Permission.USE_APPLICATION_COMMANDS).distinct()
        return jda.getInviteUrl(permissions)
    }

    /** JDA listener bridging interactions and messages into the command engine. */
    public val eventListener: ListenerAdapter = object : ListenerAdapter() {
        override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
            handleSlash(event)
        }

        override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
            handleAutoComplete(event)
        }

        override fun onMessageReceived(event: MessageReceivedEvent) {
            handleMessage(event)
        }
    }

    private fun handleSlash(event: SlashCommandInteractionEvent) {
        val root = resolveRoot(event.name) ?: return
        val path = slashPath(event.subcommandGroup, event.subcommandName)
        val leaf = descend(root, path)
        val argTokens = leaf?.arguments?.flatMap { arg -> optionTokens(event.getOption(arg.name.lowercase()), arg) }
            ?: emptyList()
        launchDispatch(SlashCommandSender(event), listOf(event.name) + path + argTokens)
    }

    private fun handleAutoComplete(event: CommandAutoCompleteInteractionEvent) {
        val root = resolveRoot(event.name) ?: return
        val path = slashPath(event.subcommandGroup, event.subcommandName)
        val leaf = descend(root, path) ?: return
        val arg = leaf.arguments.firstOrNull { it.name.equals(event.focusedOption.name, ignoreCase = true) }
            ?: return
        val partial = event.focusedOption.value
        val sender = AutoCompleteCommandSender(event)
        coroutineScope.launch {
            val choices = suggestionsFor(arg, sender, partial).take(MAX_AUTOCOMPLETE)
            event.replyChoiceStrings(choices).queue()
        }
    }

    private fun handleMessage(event: MessageReceivedEvent) {
        if (event.author.isBot) return
        val tokens = MessageCommandParser.parse(event.message.contentRaw, prefixProvider.prefixes(event))
            ?: return
        launchDispatch(MessageCommandSender(event), tokens)
    }

    /** Reverses slash flattening: a group plus a hyphen-joined subcommand back into path segments. */
    private fun slashPath(group: String?, subcommand: String?): List<String> = buildList {
        group?.let { add(it) }
        subcommand?.let { addAll(it.split("-")) }
    }

    /** Emits GNU-style tokens so the core engine parses named options and flags from slash input. */
    private fun optionTokens(
        mapping: net.dv8tion.jda.api.interactions.commands.OptionMapping?,
        arg: CommandArgument,
    ): List<String> = when {
        mapping == null -> emptyList()
        arg.flag -> if (mapping.asBoolean) listOf("--${arg.name}") else emptyList()
        arg.named -> listOf("--${arg.name}", OptionTokens.tokenFor(mapping))
        else -> listOf(OptionTokens.tokenFor(mapping))
    }

    private suspend fun suggestionsFor(arg: CommandArgument, sender: JdaSender, partial: String): List<String> {
        val provider = arg.suggestionId?.let { suggestions.forId(it) } ?: suggestions.forType(arg.type)
        if (provider != null) {
            return provider.suggest(SuggestionContext(sender, partial, arg.name))
                .map { it.value }
                .filter { it.startsWith(partial, ignoreCase = true) }
        }
        return arg.choices.filter { it.startsWith(partial, ignoreCase = true) }
    }

    private fun descend(root: CommandNode<JdaSender>, path: List<String>): CommandNode<JdaSender>? {
        var node: CommandNode<JdaSender>? = root
        for (segment in path) {
            node = node?.child(segment)
        }
        return node
    }

    private fun rootNodes(): List<CommandNode<JdaSender>> = commands.values.distinct()

    private companion object {
        const val MAX_AUTOCOMPLETE = 25
    }
}
