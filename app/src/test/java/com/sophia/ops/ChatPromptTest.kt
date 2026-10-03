package com.sophia.ops

import com.sophia.ops.ai.ChatTurn
import com.sophia.ops.ai.buildChatPrompt
import com.sophia.ops.ai.sanitizeChatResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptTest {

    private val context = "THREAT_SCORE=42/100\nCOUNTS wifi=5; bluetooth=3"

    @Test
    fun `prompt contains grounding rules scan context and question`() {
        val prompt = buildChatPrompt("Is my Wi-Fi safe?", context)

        assertTrue(prompt.contains("Is my Wi-Fi safe?"))
        assertTrue(prompt.contains("COUNTS wifi=5; bluetooth=3"))
        assertTrue(prompt.contains("Never invent device names"))
        assertTrue(prompt.endsWith("<start_of_turn>model"))
    }

    @Test
    fun `history turns appear before the current question in order`() {
        val history = listOf(
            ChatTurn(ChatTurn.ROLE_USER, "What is WPA3?"),
            ChatTurn(ChatTurn.ROLE_ASSISTANT, "WPA3 is the latest Wi-Fi security standard."),
            ChatTurn(ChatTurn.ROLE_USER, "And WPA2?"),
        )

        val prompt = buildChatPrompt("Which should I use?", context, history)

        val firstQuestion = prompt.indexOf("What is WPA3?")
        val firstAnswer = prompt.indexOf("WPA3 is the latest Wi-Fi security standard.")
        val secondQuestion = prompt.indexOf("And WPA2?")
        val currentQuestion = prompt.indexOf("Which should I use?")

        assertTrue(firstQuestion in 0 until firstAnswer)
        assertTrue(firstAnswer < secondQuestion)
        assertTrue(secondQuestion < currentQuestion)
        assertTrue(
            prompt.contains(
                "<start_of_turn>model\nWPA3 is the latest Wi-Fi security standard.\n<end_of_turn>",
            ),
        )
    }

    @Test
    fun `empty history prompt starts with the current user turn`() {
        val prompt = buildChatPrompt("Hello?", context, emptyList())
        assertTrue(prompt.startsWith("<start_of_turn>user\n"))
        // exactly two turn markers: the user turn and the pending model turn
        assertEquals(2, Regex("<start_of_turn>").findAll(prompt).count())
    }

    @Test
    fun `sanitizer strips markdown and role labels`() {
        val raw = "model\n**SOPHIA:** Stay away from *open* networks."
        assertEquals("Stay away from open networks.", sanitizeChatResponse(raw))
    }

    @Test
    fun `sanitizer strips leftover generation tags`() {
        val raw = "<start_of_turn>model\nUse <isolate>strong</isolate> passwords.\n<end_of_turn>"
        assertEquals("model Use strong passwords.", sanitizeChatResponse(raw))
    }

    @Test
    fun `sanitizer truncates runaway responses on a word boundary`() {
        val raw = "word ".repeat(300)
        val clean = sanitizeChatResponse(raw)
        assertTrue(clean.length <= 601)
        assertTrue(clean.endsWith("…"))
    }

    @Test
    fun `sanitizer falls back on an empty response`() {
        assertEquals(
            "I don't have a good answer for that right now.",
            sanitizeChatResponse("   \n"),
        )
    }
}
