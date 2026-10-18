package com.substring.agent.backend.tools;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component 
public class InventoryTools {
    //dummy data
    private final Map<String, Integer> stock = Map.of(
        "Bluetooth Headphones", 5,
        "Wireless mouse", 42,
        "Laptop Stand", 12,
        "USB-C Cable", 0,
        "Smartphone case", 0,
        "Webcam", 8
    );

    @Tool(description = "check stock availability of a product by its name")
    public String checkStock (String productName){
        System.out.println("calling check stock tool");
        Integer quantity = stock.get(productName);
        if(quantity == null)
        {
            return "Product not found";
        }
        else if(quantity > 0)
        {
            return "In stock: " + quantity + "units available";
        }
        else
        {
            return "Out of Stock";
        }
    }

    @Tool(description = "get the total count of products in stock")
    public Integer getTotalProductsInStock()
    {
        System.out.println("get total products called");
        return stock.size();
    }

    // tool to get all products
    @Tool (description = "Get a list of all products in stock")
    public List<String> getAllProductsInStock() {
        System.out.println("Calling get all products in stock");
        return stock.keySet().stream().filter(name -> stock.get(name) > 0).collect(java.util.stream.Collectors.toList());
    }
}
