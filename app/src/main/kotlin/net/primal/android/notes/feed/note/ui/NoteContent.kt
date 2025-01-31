package net.primal.android.notes.feed.note.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.primal.android.LocalContentDisplaySettings
import net.primal.android.LocalPrimalTheme
import net.primal.android.R
import net.primal.android.attachments.domain.NostrUriType
import net.primal.android.core.compose.PrimalClickableText
import net.primal.android.core.compose.attachment.model.isMediaAttachment
import net.primal.android.core.compose.icons.PrimalIcons
import net.primal.android.core.compose.icons.primaliconpack.Document
import net.primal.android.core.compose.preview.PrimalPreview
import net.primal.android.core.utils.TextMatch
import net.primal.android.core.utils.TextMatcher
import net.primal.android.nostr.ext.cleanNostrUris
import net.primal.android.notes.db.ReferencedNote
import net.primal.android.notes.db.ReferencedUser
import net.primal.android.notes.feed.model.NoteContentUi
import net.primal.android.notes.feed.model.NoteNostrUriUi
import net.primal.android.notes.feed.note.ui.attachment.NoteAttachments
import net.primal.android.notes.feed.note.ui.events.InvoicePayClickEvent
import net.primal.android.notes.feed.note.ui.events.NoteCallbacks
import net.primal.android.theme.AppTheme
import net.primal.android.theme.domain.PrimalTheme

private const val PROFILE_ID_ANNOTATION_TAG = "profileId"
private const val URL_ANNOTATION_TAG = "url"
private const val NOTE_ANNOTATION_TAG = "note"
private const val HASHTAG_ANNOTATION_TAG = "hashtag"
private const val NOSTR_ADDRESS_ANNOTATION_TAG = "naddr"

private fun List<NoteNostrUriUi>.filter(type: NostrUriType) = filter { it.type == type }

private fun List<NoteNostrUriUi>.filterUnhandledNostrAddressUris() =
    filter {
        it.uri.contains("naddr") && it.referencedUser == null && it.referencedNote == null
    }

private fun String.remove(texts: List<String>): String {
    var newContent = this
    texts.forEach {
        newContent = newContent.replace(it, "")
    }
    return newContent
}

private fun String.replaceNostrProfileUrisWithHandles(resources: List<NoteNostrUriUi>): String {
    var newContent = this
    resources.forEach {
        checkNotNull(it.referencedUser)
        newContent = newContent.replace(
            oldValue = it.uri,
            newValue = it.referencedUser.displayUsername,
            ignoreCase = true,
        )
    }
    return newContent
}

private val noteLinkLeftovers = listOf(
    "https://primal.net/e/ " to "",
    "https://www.primal.net/e/ " to "",
    "http://primal.net/e/ " to "",
    "http://www.primal.net/e/ " to "",
    "https://primal.net/e/\n" to "",
    "https://www.primal.net/e/\n" to "",
    "http://primal.net/e/\n" to "",
    "http://www.primal.net/e/\n" to "",
)

private val profileLinkLeftovers = listOf(
    "https://primal.net/p/@" to "@",
    "https://www.primal.net/p/@" to "@",
    "http://primal.net/p/@" to "@",
    "http://www.primal.net/p/@" to "@",
)

private fun String.clearParsedPrimalLinks(): String {
    var newContent = this
    (noteLinkLeftovers + profileLinkLeftovers).forEach {
        newContent = newContent.replace(
            oldValue = it.first,
            newValue = it.second,
            ignoreCase = false,
        )
    }
    noteLinkLeftovers.map { it.first.trim() }.toSet().forEach {
        if (newContent.endsWith(it)) {
            newContent = newContent.replace(
                oldValue = it,
                newValue = "",
            )
        }
    }
    return newContent
}

private fun String.limitLineBreaks(maxBreaks: Int = 2): String {
    val maxBreakPattern = "(\\n){${maxBreaks + 1},}".toRegex()
    return this.replace(maxBreakPattern, "\n".repeat(maxBreaks))
}

private const val ELLIPSIZE_THRESHOLD = 300

private fun String.ellipsize(expanded: Boolean, ellipsizeText: String): String {
    val shouldEllipsize = length > ELLIPSIZE_THRESHOLD
    return if (expanded || !shouldEllipsize) {
        this
    } else {
        substring(0, ELLIPSIZE_THRESHOLD).plus(" $ellipsizeText")
    }
}

const val TWEET_MODE_THRESHOLD = 21
const val MAX_LINE_BREAKS_IN_TWEET = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteContent(
    modifier: Modifier = Modifier,
    data: NoteContentUi,
    expanded: Boolean,
    noteCallbacks: NoteCallbacks,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    enableTweetsMode: Boolean = false,
    textSelectable: Boolean = false,
    referencedEventsHaveBorder: Boolean = false,
    highlightColor: Color = AppTheme.colorScheme.secondary,
    contentColor: Color = AppTheme.colorScheme.onSurface,
    referencedEventsContainerColor: Color = AppTheme.extraColorScheme.surfaceVariantAlt1,
    onClick: ((offset: Offset) -> Unit)? = null,
    onUrlClick: ((url: String) -> Unit)? = null,
) {
    val isDarkTheme = LocalPrimalTheme.current.isDarkTheme
    val displaySettings = LocalContentDisplaySettings.current
    val seeMoreText = stringResource(id = R.string.feed_see_more)
    val contentText = remember {
        renderContentAsAnnotatedString(
            data = data,
            expanded = expanded,
            seeMoreText = seeMoreText,
            highlightColor = highlightColor,
        )
    }

    Column(modifier = modifier) {
        if (contentText.isNotEmpty()) {
            val tweetMode = enableTweetsMode && displaySettings.tweetsModeEnabled &&
                contentText.length <= TWEET_MODE_THRESHOLD &&
                contentText.count { it == '\n' } < MAX_LINE_BREAKS_IN_TWEET

            PrimalClickableText(
                modifier = Modifier.padding(bottom = 4.dp),
                style = AppTheme.typography.bodyMedium.copy(
                    color = contentColor,
                    fontSize = if (!tweetMode) {
                        displaySettings.contentAppearance.noteBodyFontSize
                    } else {
                        displaySettings.contentAppearance.tweetFontSize
                    },
                    lineHeight = if (!tweetMode) {
                        displaySettings.contentAppearance.noteBodyLineHeight
                    } else {
                        displaySettings.contentAppearance.tweetLineHeight
                    },
                ),
                text = contentText,
                maxLines = maxLines,
                overflow = overflow,
                textSelectable = textSelectable,
                onClick = { position, offset ->
                    val annotation = contentText.getStringAnnotations(
                        start = position,
                        end = position,
                    ).firstOrNull()

                    annotation?.handleAnnotationClick(
                        onProfileClick = noteCallbacks.onProfileClick,
                        onUrlClick = onUrlClick,
                        onPostClick = noteCallbacks.onNoteClick,
                        onHashtagClick = noteCallbacks.onHashtagClick,
                        onArticleClick = noteCallbacks.onArticleClick,
                    ) ?: onClick?.invoke(offset)
                },
            )
        }

        val referencedHighlights = data.nostrUris.filter(type = NostrUriType.Highlight)
        if (referencedHighlights.isNotEmpty()) {
            referencedHighlights
                .mapNotNull { it.referencedHighlight }
                .forEachIndexed { index, highlight ->
                    ReferencedHighlight(
                        highlight = highlight,
                        isDarkTheme = isDarkTheme,
                        onClick = { naddr -> noteCallbacks.onArticleClick?.invoke(naddr) },
                    )

                    if (index < referencedHighlights.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
        }

        if (data.invoices.isNotEmpty()) {
            NoteLightningInvoice(
                modifier = Modifier.padding(top = if (contentText.isEmpty()) 4.dp else 6.dp),
                invoice = data.invoices.first(),
                onPayClick = { lnbc -> noteCallbacks.onPayInvoiceClick?.invoke(InvoicePayClickEvent(lnbc = lnbc)) },
            )
        }

        if (data.attachments.isNotEmpty()) {
            NoteAttachments(
                modifier = Modifier
                    .padding(top = if (contentText.isEmpty()) 4.dp else 6.dp)
                    .heightIn(min = 0.dp, max = 500.dp),
                attachments = data.attachments,
                blossoms = data.blossoms,
                onUrlClick = onUrlClick,
                onMediaClick = noteCallbacks.onMediaClick,
            )
        }

        val referencedPostResources = data.nostrUris.filter(type = NostrUriType.Note)
        if (referencedPostResources.isNotEmpty()) {
            ReferencedNotesColumn(
                modifier = Modifier.padding(top = 4.dp),
                postResources = referencedPostResources,
                expanded = expanded,
                containerColor = referencedEventsContainerColor,
                noteCallbacks = noteCallbacks,
                hasBorder = referencedEventsHaveBorder,
            )
        }

        val referencedArticleResources = data.nostrUris.filter(type = NostrUriType.Article)
        if (referencedArticleResources.isNotEmpty()) {
            ReferencedArticlesColumn(
                modifier = Modifier.padding(top = 4.dp),
                articleResources = referencedArticleResources,
                expanded = expanded,
                containerColor = referencedEventsContainerColor,
                noteCallbacks = noteCallbacks,
                hasBorder = referencedEventsHaveBorder,
            )
        }

        val genericEvents = data.nostrUris.filter(type = NostrUriType.Unsupported)
        if (genericEvents.isNotEmpty()) {
            genericEvents.forEachIndexed { index, nostrUriUi ->
                NoteUnknownEvent(
                    modifier = Modifier.fillMaxWidth(),
                    icon = nostrUriUi.uri.nostrUriToMissingEventIcon(),
                    altDescription = nostrUriUi.referencedEventAlt
                        ?: nostrUriUi.uri.nostrUriToMissingEventAltDescription(),
                )

                if (index < genericEvents.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun String.nostrUriToMissingEventAltDescription(): String {
    return if (contains("note1")) {
        stringResource(R.string.feed_missing_event_alt_description_note)
    } else {
        stringResource(R.string.feed_missing_event_alt_description_event)
    }
}

@Composable
private fun String.nostrUriToMissingEventIcon(): ImageVector {
    return if (contains("note1")) {
        Icons.Outlined.ErrorOutline
    } else {
        PrimalIcons.Document
    }
}

private fun AnnotatedString.Range<String>.handleAnnotationClick(
    onProfileClick: ((String) -> Unit)?,
    onUrlClick: ((String) -> Unit)?,
    onPostClick: ((String) -> Unit)?,
    onHashtagClick: ((String) -> Unit)?,
    onArticleClick: ((naddr: String) -> Unit)?,
) = when (this.tag) {
    PROFILE_ID_ANNOTATION_TAG -> onProfileClick?.invoke(this.item)
    URL_ANNOTATION_TAG -> onUrlClick?.invoke(this.item)
    NOTE_ANNOTATION_TAG -> onPostClick?.invoke(this.item)
    HASHTAG_ANNOTATION_TAG -> onHashtagClick?.invoke(this.item)
    NOSTR_ADDRESS_ANNOTATION_TAG -> {
        this.item.split(":").lastOrNull()?.let { address ->
            onArticleClick?.invoke(address)
        }
    }

    else -> Unit
}

fun renderContentAsAnnotatedString(
    data: NoteContentUi,
    expanded: Boolean,
    seeMoreText: String,
    highlightColor: Color,
    shouldKeepNostrNoteUris: Boolean = false,
): AnnotatedString {
    val mediaAttachments = data.attachments.filter { it.isMediaAttachment() }
    val linkAttachments = data.attachments.filterNot { it.isMediaAttachment() }
    val mentionedUsers = data.nostrUris.filter(type = NostrUriType.Profile)
    val unhandledNostrAddressUris = data.nostrUris.filterUnhandledNostrAddressUris()

    val refinedContent = data.content
        .cleanNostrUris()
        .replaceNostrProfileUrisWithHandles(resources = mentionedUsers)
        .remove(texts = mediaAttachments.map { it.url })
        .remove(texts = if (!shouldKeepNostrNoteUris) data.nostrUris.map { it.uri } else emptyList())
        .remove(texts = linkAttachments.map { it.url })
        .remove(texts = data.invoices)
        .clearParsedPrimalLinks()
        .limitLineBreaks(maxBreaks = 2)
        .trim()
        .ellipsize(expanded = expanded, ellipsizeText = seeMoreText)

    return buildAnnotatedString {
        append(refinedContent)

        if (refinedContent.endsWith(seeMoreText)) {
            addStyle(
                style = SpanStyle(color = highlightColor),
                start = refinedContent.length - seeMoreText.length,
                end = refinedContent.length,
            )
        }

        unhandledNostrAddressUris.forEach {
            addNostrAddressAnnotation(
                nostrUri = it,
                content = refinedContent,
                highlightColor = highlightColor,
            )
        }

        data.attachments
            .filterNot { it.isMediaAttachment() }
            .map { it.url }
            .forEach {
                addUrlAnnotation(
                    url = it,
                    content = refinedContent,
                    highlightColor = highlightColor,
                )
            }

        mentionedUsers.forEach {
            checkNotNull(it.referencedUser)
            addProfileAnnotation(
                referencedUser = it.referencedUser,
                content = refinedContent,
                highlightColor = highlightColor,
            )
        }

        TextMatcher(
            content = refinedContent,
            texts = data.hashtags,
            repeatingOccurrences = true,
        )
            .matches()
            .forEach {
                addHashtagAnnotation(
                    textMatch = it,
                    highlightColor = highlightColor,
                )
            }
    }
}

private fun AnnotatedString.Builder.addHashtagAnnotation(highlightColor: Color, textMatch: TextMatch) {
    addStyle(
        style = SpanStyle(color = highlightColor),
        start = textMatch.startIndex,
        end = textMatch.endIndex,
    )
    addStringAnnotation(
        tag = HASHTAG_ANNOTATION_TAG,
        annotation = textMatch.value,
        start = textMatch.startIndex,
        end = textMatch.endIndex,
    )
}

private fun AnnotatedString.Builder.addProfileAnnotation(
    referencedUser: ReferencedUser,
    content: String,
    highlightColor: Color,
) {
    val displayHandle = referencedUser.displayUsername
    val startIndex = content.indexOf(displayHandle)
    if (startIndex >= 0) {
        val endIndex = startIndex + displayHandle.length
        addStyle(
            style = SpanStyle(color = highlightColor),
            start = startIndex,
            end = endIndex,
        )
        addStringAnnotation(
            tag = PROFILE_ID_ANNOTATION_TAG,
            annotation = referencedUser.userId,
            start = startIndex,
            end = endIndex,
        )
    }
}

private fun AnnotatedString.Builder.addUrlAnnotation(
    url: String,
    content: String,
    highlightColor: Color,
) {
    val startIndex = content.indexOf(url)
    if (startIndex >= 0) {
        val endIndex = startIndex + url.length
        addStyle(
            style = SpanStyle(color = highlightColor),
            start = startIndex,
            end = endIndex,
        )
        addStringAnnotation(
            tag = URL_ANNOTATION_TAG,
            annotation = url,
            start = startIndex,
            end = endIndex,
        )
    }
}

private fun AnnotatedString.Builder.addNostrAddressAnnotation(
    nostrUri: NoteNostrUriUi,
    content: String,
    highlightColor: Color,
) {
    val startIndex = content.indexOf(nostrUri.uri)
    if (startIndex >= 0) {
        val endIndex = startIndex + nostrUri.uri.length
        addStyle(
            style = SpanStyle(color = highlightColor),
            start = startIndex,
            end = endIndex,
        )
        addStringAnnotation(
            tag = NOSTR_ADDRESS_ANNOTATION_TAG,
            annotation = nostrUri.uri,
            start = startIndex,
            end = endIndex,
        )
    }
}

@Preview
@Composable
fun PreviewPostContent() {
    PrimalPreview(primalTheme = PrimalTheme.Sunset) {
        Surface {
            NoteContent(
                data = NoteContentUi(
                    noteId = "",
                    content = """
                        Hey there nostr:referencedUser, how is life? #nostr 
                    """.trimIndent(),
                    attachments = emptyList(),
                    nostrUris = listOf(
                        NoteNostrUriUi(
                            uri = "nostr:referencedUser",
                            type = NostrUriType.Profile,
                            referencedEventAlt = null,
                            referencedNote = null,
                            referencedUser = ReferencedUser(
                                userId = "nostr:referencedUser",
                                handle = "alex",
                            ),
                            referencedArticle = null,
                            referencedHighlight = null,
                        ),
                    ),
                    hashtags = listOf("#nostr"),
                ),
                expanded = false,
                enableTweetsMode = false,
                onClick = {},
                onUrlClick = {},
                noteCallbacks = NoteCallbacks(),
            )
        }
    }
}

@Preview
@Composable
fun PreviewPostUnknownReferencedEventWithAlt() {
    PrimalPreview(primalTheme = PrimalTheme.Sunset) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            NoteContent(
                data = NoteContentUi(
                    noteId = "",
                    content = "This is amazing! nostr:nevent124124124214123412",
                    attachments = emptyList(),
                    nostrUris = listOf(
                        NoteNostrUriUi(
                            uri = "nostr:nevent124124124214123412",
                            type = NostrUriType.Unsupported,
                            referencedEventAlt = "This is a music song.",
                            referencedNote = null,
                            referencedUser = null,
                            referencedArticle = null,
                            referencedHighlight = null,
                        ),
                    ),
                    hashtags = listOf("#nostr"),
                ),
                expanded = false,
                enableTweetsMode = false,
                onClick = {},
                onUrlClick = {},
                noteCallbacks = NoteCallbacks(),
            )
        }
    }
}

@Preview
@Composable
fun PreviewPostUnknownReferencedEventWithoutAlt() {
    PrimalPreview(primalTheme = PrimalTheme.Sunset) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            NoteContent(
                data = NoteContentUi(
                    noteId = "",
                    content = "This is amazing! nostr:note111",
                    attachments = emptyList(),
                    nostrUris = listOf(
                        NoteNostrUriUi(
                            uri = "nostr:note111",
                            type = NostrUriType.Unsupported,
                            referencedEventAlt = null,
                            referencedNote = null,
                            referencedUser = null,
                            referencedArticle = null,
                            referencedHighlight = null,
                        ),
                    ),
                    hashtags = listOf("#nostr"),
                ),
                expanded = false,
                enableTweetsMode = false,
                onClick = {},
                onUrlClick = {},
                noteCallbacks = NoteCallbacks(),
            )
        }
    }
}

@Preview
@Composable
fun PreviewPostContentWithReferencedPost() {
    PrimalPreview(primalTheme = PrimalTheme.Sunset) {
        Surface {
            NoteContent(
                data = NoteContentUi(
                    noteId = "",
                    content = """
                        Unfortunately the days of using pseudonyms in metaspace are numbered. #nostr
                        
                        nostr:referencedPost
                        
                        Or maybe not.
                        
                        nostr:referenced2Post
                    """.trimIndent(),
                    attachments = emptyList(),
                    nostrUris = listOf(
                        NoteNostrUriUi(
                            uri = "nostr:referencedPost",
                            type = NostrUriType.Note,
                            referencedNote = ReferencedNote(
                                postId = "postId",
                                createdAt = 0,
                                content = "This is referenced post.",
                                authorId = "authorId",
                                authorName = "primal",
                                authorAvatarCdnImage = null,
                                authorInternetIdentifier = "hi@primal.net",
                                authorLightningAddress = "h@getalby.com",
                                attachments = emptyList(),
                                nostrUris = emptyList(),
                                authorLegendProfile = null,
                            ),
                            referencedUser = null,
                            referencedArticle = null,
                            referencedEventAlt = null,
                            referencedHighlight = null,
                        ),
                        NoteNostrUriUi(
                            uri = "nostr:referenced2Post",
                            type = NostrUriType.Note,
                            referencedNote = ReferencedNote(
                                postId = "postId",
                                createdAt = 0,
                                content = "This is referenced post #2.",
                                authorId = "authorId",
                                authorName = "primal",
                                authorAvatarCdnImage = null,
                                authorInternetIdentifier = "hi@primal.net",
                                authorLightningAddress = "h@getalby.com",
                                attachments = emptyList(),
                                nostrUris = emptyList(),
                                authorLegendProfile = null,
                            ),
                            referencedUser = null,
                            referencedArticle = null,
                            referencedEventAlt = null,
                            referencedHighlight = null,
                        ),
                    ),
                    hashtags = listOf("#nostr"),
                    invoices = listOf(
                        "lnbc888550n1pnp6fz9pp5als09l5nfj9pkqk7mpj6cz6075nd4v95ljz0p65n8zkz03p75t3" +
                            "sdp9wdshgueqvehhygr3v9q8qunfd4skctnwv46r5cqzzsxqrrs0fppqyyu34ypjxgclynk64hz2r6" +
                            "ddudpaf5mesp5c8mv8xdu67pra93m3j9aw9mxh08gk09upmjsdpspjxcgcrfjyc0s9qyyssqng6uu0" +
                            "z84h7wlcrlyqywl6jlfd4630k4yd056d3q9h9rg9tzmza5adpzjn489fees4vq0armdskuqgxxvug3" +
                            "et34cqdxj6ldu8lkd2cqcvx5am",
                    ),
                ),
                expanded = false,
                enableTweetsMode = false,
                onClick = {},
                onUrlClick = {},
                noteCallbacks = NoteCallbacks(),
            )
        }
    }
}

@Preview
@Composable
fun PreviewPostContentWithTweet() {
    PrimalPreview(primalTheme = PrimalTheme.Sunset) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            NoteContent(
                data = NoteContentUi(
                    noteId = "",
                    content = "Rise and shine!",
                    attachments = emptyList(),
                ),
                expanded = false,
                enableTweetsMode = true,
                onClick = {},
                onUrlClick = {},
                noteCallbacks = NoteCallbacks(),
            )
        }
    }
}
