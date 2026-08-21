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

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import java.io.InputStream

/**
 * DSL for composing a rich reply — text content, embeds and file attachments — into a JDA
 * [MessageCreateData]. Both slash interactions and prefix commands accept the built payload, so the
 * same handler code works across either. Wraps JDA's own [EmbedBuilder] and [FileUpload] rather than
 * re-abstracting them, keeping the full API within reach.
 */
public class ReplyBuilder {
    /** Plain-text body of the message; `null` leaves it empty (e.g. an embed-only reply). */
    public var content: String? = null

    private val embeds = mutableListOf<MessageEmbed>()
    private val files = mutableListOf<FileUpload>()

    /** Adds an embed built inline via JDA's [EmbedBuilder]. */
    public fun embed(block: EmbedBuilder.() -> Unit) {
        embeds += EmbedBuilder().apply(block).build()
    }

    /** Adds an already-built [embed]. */
    public fun embed(embed: MessageEmbed) {
        embeds += embed
    }

    /** Attaches a pre-built [upload]. */
    public fun file(upload: FileUpload) {
        files += upload
    }

    /** Attaches a file named [name] streamed from [data]. */
    public fun file(name: String, data: InputStream) {
        files += FileUpload.fromData(data, name)
    }

    /** Attaches a file named [name] from raw [bytes]. */
    public fun file(name: String, bytes: ByteArray) {
        files += FileUpload.fromData(bytes, name)
    }

    internal fun build(): MessageCreateData = MessageCreateBuilder()
        .apply { content?.let { setContent(it) } }
        .setEmbeds(embeds)
        .setFiles(files)
        .build()
}
