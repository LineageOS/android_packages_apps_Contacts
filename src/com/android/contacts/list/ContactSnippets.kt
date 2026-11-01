/*
 * SPDX-FileCopyrightText: 2008 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.contacts.list

import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.SearchSnippets
import com.android.contacts.util.SearchUtil

/**
 * Extracts the part of a contact that matched a search query, e.g. a phone number or an email
 * address, from the search snippets of the contacts provider.
 */
object ContactSnippets {
    private const val SNIPPET_START_MATCH = '['
    private const val SNIPPET_END_MATCH = ']'

    /**
     * Matches an email address as a single token, or a word.
     */
    private val SPLIT_PATTERN = Regex("([\\w-\\.]+)@((?:[\\w]+\\.)+)([a-zA-Z]{2,4})|[\\w]+")

    /**
     * Returns the snippet of the contact at the current position of [cursor], read from its
     * [SearchSnippets.SNIPPET] column [snippetColumn], or null if there is nothing to show.
     * Snippets longer than [maxLength] are shortened around the match.
     */
    @JvmStatic
    fun fromCursor(cursor: Cursor, snippetColumn: Int, maxLength: Int): String? {
        if (cursor.columnCount <= snippetColumn
            || SearchSnippets.SNIPPET != cursor.getColumnName(snippetColumn)
        ) {
            return null
        }
        val snippet = cursor.getString(snippetColumn) ?: return null

        // Do client side snippeting if provider didn't do it.
        val extras = cursor.extras
        if (extras.getBoolean(ContactsContract.DEFERRED_SNIPPETING)) {
            val displayName = cursor.getColumnIndex(Contacts.DISPLAY_NAME)
                .takeIf { it >= 0 }
                ?.let(cursor::getString)
            return forQuery(
                snippet,
                extras.getString(ContactsContract.DEFERRED_SNIPPETING_QUERY),
                displayName,
                maxLength,
            )
        }

        // Keep the lines of the match, without its markers.
        val start = snippet.indexOf(SNIPPET_START_MATCH)
        if (start == -1) {
            return null
        }
        val from = snippet.lastIndexOf('\n', start) + 1
        val end = snippet.lastIndexOf(SNIPPET_END_MATCH)
        val to = if (end != -1) {
            snippet.indexOf('\n', end).takeIf { it != -1 } ?: snippet.length
        } else {
            snippet.length
        }
        return snippet.substring(from, to)
            .filter { it != SNIPPET_START_MATCH && it != SNIPPET_END_MATCH }
    }

    /**
     * Returns the line of the deferred [snippet] matching [query], or null if the
     * [displayName] already matches it. Lines longer than [maxLength] are shortened around the
     * match.
     */
    @JvmStatic
    fun forQuery(snippet: String?, query: String?, displayName: String?, maxLength: Int): String? {
        if (snippet.isNullOrEmpty() || query.isNullOrEmpty()) {
            return null
        }
        val cleanQuery = SearchUtil.cleanStartAndEndOfSearchQuery(query.lowercase())

        // If the display name already contains the query term, snippets are not needed.
        if (!displayName.isNullOrEmpty()) {
            val nameTokens = SPLIT_PATTERN.findAll(displayName.lowercase()).map { it.value }
            if (nameTokens.any { it.startsWith(cleanQuery) }) {
                return null
            }
        }

        // The snippet may contain multiple data lines. Show the first line that matches.
        val matched = SearchUtil.findMatchingLine(snippet, cleanQuery) ?: return null
        val line = matched.line ?: return null

        // Tokenize long strings since the match may be at the end of them.
        return if (line.length > maxLength) {
            snippetize(line, matched.startIndex, maxLength)
        } else {
            line
        }
    }

    /**
     * Shows up to [maxLength] characters of [line] around the match at [matchIndex], as full
     * tokens, with as many tokens before the match as possible.
     */
    private fun snippetize(line: String, matchIndex: Int, maxLength: Int): String {
        var remainingLength = maxLength
        var tempRemainingLength = remainingLength

        // Start the end token after the matched query.
        var index = matchIndex
        var endTokenIndex = index

        // Find the match token first.
        while (index < line.length) {
            if (!line[index].isLetterOrDigit()) {
                endTokenIndex = index
                remainingLength = tempRemainingLength
                break
            }
            tempRemainingLength--
            index++
        }

        // Find as much content before the match.
        index = matchIndex - 1
        tempRemainingLength = remainingLength
        var startTokenIndex = matchIndex
        while (index > -1 && tempRemainingLength > 0) {
            if (!line[index].isLetterOrDigit()) {
                startTokenIndex = index
                remainingLength = tempRemainingLength
            }
            tempRemainingLength--
            index--
        }

        // Find remaining content after the match.
        index = endTokenIndex
        tempRemainingLength = remainingLength
        while (index < line.length && tempRemainingLength > 0) {
            if (!line[index].isLetterOrDigit()) {
                endTokenIndex = index
            }
            tempRemainingLength--
            index++
        }

        // Append an ellipsis if there is content before or after.
        return buildString {
            if (startTokenIndex > 0) {
                append("...")
            }
            append(line, startTokenIndex, endTokenIndex)
            if (endTokenIndex < line.length) {
                append("...")
            }
        }
    }
}
