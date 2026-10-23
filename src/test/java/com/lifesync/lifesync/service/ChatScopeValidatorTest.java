package com.lifesync.lifesync.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit test suite for {@link ChatScopeValidator}.
 * Verifies that the LifeSync AI Coach strictly accepts valid health, nutrition,
 * fitness, BMI, and pantry questions while rejecting off-topic queries
 * (e.g. coding, politics, movie trivia, finance, general homework).
 */
public class ChatScopeValidatorTest {

    @Test
    public void testOnTopicQueriesAreAllowed() {
        String[] onTopicQueries = {
                "Hi",
                "Hello!",
                "Good morning",
                "What can you do?",
                "Who are you?",
                "Help me with my health goals",
                "What is a healthy BMI?",
                "How do I calculate my daily calorie target?",
                "What can I cook with eggs, bread, and spinach?",
                "I am lactose intolerant, what are good dairy-free proteins?",
                "Can you suggest a low impact workout for overweight individuals?",
                "How many grams of protein should I eat per meal?",
                "How much water should I drink after a morning jog?",
                "Suggest a healthy breakfast recipe using my pantry items",
                "How does sleep affect my weight loss journey?",
                "Can I do pushups and squats if my BMI is normal?",
                "How to plan my meals around my study schedule?"
        };

        for (String query : onTopicQueries) {
            assertFalse(ChatScopeValidator.isOffTopic(query),
                    "Expected on-topic query to be allowed: '" + query + "'");
        }
    }

    @Test
    public void testOffTopicQueriesAreRejected() {
        String[] offTopicQueries = {
                "Can you write a python script to calculate Fibonacci numbers?",
                "Write a Java class with a unit test for sorting",
                "Who won the 2024 presidential election?",
                "Tell me about the history of the Roman Empire",
                "What is the stock price of Apple today?",
                "Should I invest in Bitcoin or Ethereum?",
                "What is the plot of Avengers Endgame?",
                "Solve the quadratic equation x^2 + 5x + 6 = 0",
                "Write a romance poem about the beach",
                "Who is the best Fortnite player in the world?",
                "Explain quantum physics and thermodynamics equations",
                "What are the latest celebrity gossip news in Hollywood?"
        };

        for (String query : offTopicQueries) {
            assertTrue(ChatScopeValidator.isOffTopic(query),
                    "Expected off-topic query to be rejected: '" + query + "'");
        }
    }

    @Test
    public void testRefusalMessageContent() {
        String refusal = ChatScopeValidator.getRefusalMessage();
        assertNotNull(refusal);
        assertTrue(refusal.contains("LifeSync AI Coach"));
        assertTrue(refusal.contains("health, nutrition, fitness"));
    }

    @Test
    public void testNullOrBlankHandling() {
        assertFalse(ChatScopeValidator.isOffTopic(null));
        assertFalse(ChatScopeValidator.isOffTopic(""));
        assertFalse(ChatScopeValidator.isOffTopic("   "));
    }
}
