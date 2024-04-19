package com.example.stateful_functions.cloudevents.data;

import java.math.BigDecimal;

public class ProductEventDetails {

    private String id;
    private String title;
    private String description;
    private BigDecimal price;
    private ProductAvailability availability;

    public ProductEventDetails() {
    }

    private ProductEventDetails(Builder builder) {
        id = builder.id;
        title = builder.title;
        description = builder.description;
        price = builder.price;
        availability = builder.availability;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public ProductAvailability getAvailability() {
        return availability;
    }


    public static final class Builder {
        private String id;
        private String title;
        private String description;
        private BigDecimal price;
        private ProductAvailability availability;

        public Builder() {
        }

        public Builder(ProductEventDetails productEventDetails) {
            id = productEventDetails.id;
            title = productEventDetails.title;
            description = productEventDetails.description;
            price = productEventDetails.price;
            availability = productEventDetails.availability;
        }

        public Builder id(String val) {
            id = val;
            return this;
        }

        public Builder title(String val) {
            title = val;
            return this;
        }

        public Builder description(String val) {
            description = val;
            return this;
        }

        public Builder price(BigDecimal val) {
            price = val;
            return this;
        }

        public Builder availability(ProductAvailability val) {
            availability = val;
            return this;
        }

        public ProductEventDetails build() {
            return new ProductEventDetails(this);
        }
    }
}

