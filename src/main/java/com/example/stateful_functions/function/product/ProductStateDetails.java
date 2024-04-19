package com.example.stateful_functions.function.product;


import org.apache.flink.api.common.typeinfo.TypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInfoFactory;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@TypeInfo(ProductStateDetails.ProductStateDetailsTypeInfoFactory.class)
public class ProductStateDetails {

    private String id;
    private String title;
    private String description;
    private BigDecimal price;
    private ProductStateAvailability availability;

    public static class ProductStateDetailsTypeInfoFactory extends TypeInfoFactory<ProductStateDetails> {
        @Override
        public TypeInformation<ProductStateDetails> createTypeInfo(
                Type t, Map<String, TypeInformation<?>> genericParameters) {

            return Types.POJO(ProductStateDetails.class, new HashMap<>() { {
                put("id", Types.STRING);
                put("title", Types.STRING);
                put("description", Types.STRING);
                put("price", Types.BIG_DEC);
                put("availability", Types.ENUM(ProductStateAvailability.class));
            } } );
        }
    }

    public ProductStateDetails() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public ProductStateAvailability getAvailability() {
        return availability;
    }

    public void setAvailability(ProductStateAvailability availability) {
        this.availability = availability;
    }
}
