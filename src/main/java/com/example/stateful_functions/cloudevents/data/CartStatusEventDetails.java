package com.example.stateful_functions.cloudevents.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class CartStatusEventDetails {

    private String cartId;
    private List<CartItemStatusDetails> cartItemStatuses = new ArrayList<>();

    public CartStatusEventDetails(String cartId) {
        this.cartId = cartId;
    }

    @JsonCreator
    public CartStatusEventDetails(
            @JsonProperty("cartId") String cartId,
            @JsonProperty("cartItemStatuses") List<CartItemStatusDetails> cartItemStatuses)
    {
        this.cartId = cartId;
        this.cartItemStatuses = cartItemStatuses;
    }

    public String getCartId() {
        return cartId;
    }

    public List<CartItemStatusDetails> getCartItemStatuses() {
        return cartItemStatuses;
    }
}
