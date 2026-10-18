package com.substring.agent.backend.tools;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component 
public class OrderTools {
    //fake data:

    private final Map<String, String> orders = Map.of(
        "1042", "shipped - arriving tomorrow",
        "1043", "processing - not yet shipped",
        "1044", "Cancelled - out of stock",
        "1045", "shipped - arriving next week"
    );

    @Tool(description = "Get the status of an customer order by its order Id")
    public String getOrderStatus(String orderId){
        System.out.println("Fetching order status for order ID: " + orderId);
        return orders.getOrDefault(orderId, "Order not found");
    }


    @Tool(description = "Cancel the customer order by its order Id")
    public String cancelOrder(String orderId){
        System.out.println("Attempting to cancel order ID: " + orderId);
        if (orders.containsKey(orderId)){
            return "Order" + orderId + "has been cancelled";
        }
        else {
            return "Order not found";
        }
    }

    @Tool(description = "Get the total count of customer orders")
    public Integer getOrderCount() {
        System.out.println("calling orders count tool");
        return orders.size();
    } 
    
}
