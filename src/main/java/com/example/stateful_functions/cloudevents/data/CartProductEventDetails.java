package com.example.stateful_functions.cloudevents.data;

import java.math.BigDecimal;

public class CartProductEventDetails {

    private String cartId;
    private String productId;
    private BigDecimal originPrice; // price the customer was seeing at time of 'add to cart'
    private int quantity;

    public CartProductEventDetails() {
    }

    public CartProductEventDetails(String cartId) {
        this.cartId = cartId;
    }

    private CartProductEventDetails(Builder builder) {
        cartId = builder.cartId;
        productId = builder.productId;
        originPrice = builder.originPrice;
        quantity = builder.quantity;
    }


    public String getCartId() {
        return cartId;
    }

    public String getProductId() {
        return productId;
    }

    public BigDecimal getOriginPrice() {
        return originPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public static final class Builder {
        private String cartId;
        private String productId;
        private BigDecimal originPrice;
        private int quantity;

        public Builder() {
        }

        public Builder cartId(String val) {
            cartId = val;
            return this;
        }

        public Builder productId(String val) {
            productId = val;
            return this;
        }

        public Builder originPrice(BigDecimal val) {
            originPrice = val;
            return this;
        }

        public Builder quantity(int val) {
            quantity = val;
            return this;
        }

        public CartProductEventDetails build() {
            return new CartProductEventDetails(this);
        }
    }
}
