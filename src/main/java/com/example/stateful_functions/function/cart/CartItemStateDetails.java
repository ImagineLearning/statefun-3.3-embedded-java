package com.example.stateful_functions.function.cart;


import com.example.stateful_functions.function.product.ProductStateAvailability;
import org.apache.flink.api.common.typeinfo.TypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInfoFactory;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@TypeInfo(CartItemStateDetails.CartItemStateDetailsTypeInfoFactory.class)
public class CartItemStateDetails {

    private String productId;
    private boolean subscribedToProduct;
    private BigDecimal originPrice;
    private BigDecimal price;
    private int quantity;
    private ProductStateAvailability availability;

    public CartItemStateDetails() {
    }

    public static class CartItemStateDetailsTypeInfoFactory extends TypeInfoFactory<CartItemStateDetails> {
        @Override
        public TypeInformation<CartItemStateDetails> createTypeInfo(
                Type t, Map<String, TypeInformation<?>> genericParameters) {

            return Types.POJO(CartItemStateDetails.class, new HashMap<>() { {
                put("productId", Types.STRING);
                put("subscribedToProduct", Types.BOOLEAN);
                put("originPrice", Types.BIG_DEC);
                put("price", Types.BIG_DEC);
                put("quantity", Types.INT);
                put("availability", Types.ENUM(ProductStateAvailability.class));
            } } );
        }
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public boolean isSubscribedToProduct() {
        return subscribedToProduct;
    }

    public void setSubscribedToProduct(boolean subscribedToProduct) {
        this.subscribedToProduct = subscribedToProduct;
    }

    public BigDecimal getOriginPrice() {
        return originPrice;
    }

    public void setOriginPrice(BigDecimal originPrice) {
        this.originPrice = originPrice;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public ProductStateAvailability getAvailability() {
        return availability;
    }

    public void setAvailability(ProductStateAvailability availability) {
        this.availability = availability;
    }
}
