package com.example.stateful_functions.function.cart;


import org.apache.flink.api.common.typeinfo.TypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInfoFactory;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;

import java.lang.reflect.Type;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TypeInfo(CartStateDetails.CartStateDetailsTypeInfoFactory.class)
public class CartStateDetails {

    private String id;
    private LocalDateTime dateCreatedUtc = LocalDateTime.now(Clock.systemUTC());
    private Map<String,CartItemStateDetails> items = new HashMap<>();

    public CartStateDetails() {
    }


    public CartStateDetails(String id) {
        this.id = id;
    }

    public static class CartStateDetailsTypeInfoFactory extends TypeInfoFactory<CartStateDetails> {
        @Override
        public TypeInformation<CartStateDetails> createTypeInfo(
                Type t, Map<String, TypeInformation<?>> genericParameters) {

            return Types.POJO(CartStateDetails.class, new HashMap<>() { {
                put("id", Types.STRING);
                put("dateCreatedUtc", Types.LOCAL_DATE_TIME);
                put("items", Types.MAP(Types.STRING, TypeInformation.of(CartItemStateDetails.class)));
            } } );
        }
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getDateCreatedUtc() {
        return dateCreatedUtc;
    }

    public void setDateCreatedUtc(LocalDateTime dateCreatedUtc) {
        this.dateCreatedUtc = dateCreatedUtc;
    }

    public Map<String, CartItemStateDetails> getItems() {
        return items;
    }

    public void setItems(Map<String, CartItemStateDetails> items) {
        this.items.clear();
        this.items.putAll(items);
    }
}
