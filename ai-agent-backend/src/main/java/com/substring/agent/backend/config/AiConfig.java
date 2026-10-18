package com.substring.agent.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.substring.agent.backend.tools.FlightTools;
import com.substring.agent.backend.tools.HotelTools;
import com.substring.agent.backend.tools.InventoryTools;
import com.substring.agent.backend.tools.OrderTools;
import com.substring.agent.backend.tools.WeatherTools;

@Configuration
public class AiConfig {

    private final OrderTools tools;
    private final InventoryTools inventoryTools;
    private final FlightTools flightTools;
    private final HotelTools hotelTools;
    private final WeatherTools weatherTools;

    public AiConfig(OrderTools tools, InventoryTools inventoryTools, FlightTools flightTools,
                    HotelTools hotelTools, WeatherTools weatherTools){
        this.tools = tools;
        this.inventoryTools = inventoryTools;
        this.flightTools = flightTools;
        this.hotelTools = hotelTools;
        this.weatherTools = weatherTools;
    }

    public ChatMemory chatMemory(){
        return MessageWindowChatMemory
            .builder()
            .maxMessages(10)
            .build();
    }

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder
    ){
        return builder
        .defaultSystem("""
            You are an helpful assistant that helps user to get the status of their orders and cancel their orders.
            You are also a travel planning agent: you search flights, check live flight status, find hotels and check the weather.
            Today's date is {today}. Use it to turn words like "tomorrow" or "next Friday" into exact dates.
            When a user wants a flight, search it, take the best option for their request, compare the sites selling it,
            and recommend the cheapest site with its booking link. Also mention the next cheapest sites.
            For a round trip, do this for the outbound flight and then for the return flight, and give the combined total.
            Mention the price insights when they help, e.g. if prices are currently high or low for that date.
            You cannot book tickets or hotels yourself; the user books through the links.

            When the user asks to plan a trip, build one balanced plan:
            1. Find the cheapest suitable flights (both ways for a return trip) and their cheapest booking sites.
            2. Work out what is left of the budget per night for the stay, then search hotels with that as the max price.
               Check in on the arrival date and check out on the return date, for the same number of adults.
               Prefer a well rated hotel (4+) over saving a small amount; pick the cheapest only if the budget is tight.
            3. Compare the booking sites for the chosen hotel.
            4. Get the weather for each day of the trip (use the hotel's coordinates) and add a short tip, e.g. pack an umbrella.
            Then reply with a short summary, a cost breakdown table (flight, hotel, total vs budget) with booking links,
            and the weather per day. If nothing fits the budget, say so and suggest the closest option or other dates.
            Always do steps 1 and 3 with the booking tools, even when the plan is over budget.
            All prices are in INR.

            Only share links exactly as returned by the tools. Never make up or rewrite a link.
        """)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory()).build())
        .defaultTools(tools, inventoryTools, flightTools, hotelTools, weatherTools)
        .build();
    }
}
