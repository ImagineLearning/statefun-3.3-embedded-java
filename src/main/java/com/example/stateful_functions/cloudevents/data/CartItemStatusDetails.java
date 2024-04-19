package com.example.stateful_functions.cloudevents.data;

import java.math.BigDecimal;

public class CartItemStatusDetails {
    private String productId;
    private BigDecimal originPrice; // price of the product the last time the customer saw it
    private int quantity;
    private BigDecimal currentPrice;
    private ProductAvailability availability;

    public CartItemStatusDetails() {
    }

    private CartItemStatusDetails(Builder builder) {
        productId = builder.productId;
        originPrice = builder.originPrice;
        quantity = builder.quantity;
        currentPrice = builder.currentPrice;
        availability = builder.availability;
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

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public ProductAvailability getAvailability() {
        return availability;
    }


    public static final class Builder {
        private String productId;
        private BigDecimal originPrice;
        private int quantity;
        private BigDecimal currentPrice;
        private ProductAvailability availability;

        public Builder() {
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

        public Builder currentPrice(BigDecimal val) {
            currentPrice = val;
            return this;
        }

        public Builder availability(ProductAvailability val) {
            availability = val;
            return this;
        }

        public CartItemStatusDetails build() {
            return new CartItemStatusDetails(this);
        }
    }
}
