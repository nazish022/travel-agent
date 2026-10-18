package com.substring.agent.backend.service;

import java.time.LocalDate;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;



@Service 
public class ChatService {
    private final ChatClient chatClient;
    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String chat(String query, String conversationId){
        return chatClient
        .prompt()
        // Fills {today} in the default system prompt on every request
        .system(s -> s.param("today", LocalDate.now().toString()))
        .user(query)
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
        .call().content();
    }
}
