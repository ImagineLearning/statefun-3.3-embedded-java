package com.example.stateful_functions.function.subscriber;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.flink.api.common.typeinfo.TypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInfoFactory;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Parameters required to maintain a subscription by another function.
 */
@TypeInfo(FunctionSubscriber.StatefunSubscriberTypeInfoFactory.class)
public class FunctionSubscriber {
    private String namespace;
    private String type;
    private String id;
    private LocalDateTime dateCreated = LocalDateTime.now(ZoneOffset.UTC);
    private String correlationId;

    public static class StatefunSubscriberTypeInfoFactory extends TypeInfoFactory<FunctionSubscriber> {
        @Override
        public TypeInformation<FunctionSubscriber> createTypeInfo(
                Type t, Map<String, TypeInformation<?>> genericParameters) {

            return Types.POJO(FunctionSubscriber.class, new HashMap<>() { {
                put("namespace", Types.STRING);
                put("type", Types.STRING);
                put("id", Types.STRING);
                put("dateCreated", Types.LOCAL_DATE_TIME);
                put("correlationId", Types.STRING);
            } } );
        }
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDateTime dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getCorrelationId() { return correlationId; }

    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    /** Returns false if this FunctionSubscriber object is not properly initialized */
    public boolean validate() {
        return StringUtils.isNotBlank(namespace) &&
                StringUtils.isNotBlank(type) &&
                StringUtils.isNotBlank(id);
    }

    public String getSubscriberId() {
        return new StringBuilder(namespace)
                .append(':')
                .append(type)
                .append(':')
                .append(id)
                .toString();
    }
    @Override
    public String toString() {
        return new ToStringBuilder(this).
                append("namespace", namespace).
                append("functionType", type).
                append("id", id).
                toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        FunctionSubscriber that = (FunctionSubscriber) o;

        return new EqualsBuilder()
                .append(namespace, that.namespace)
                .append(type, that.type)
                .append(id, that.id)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(namespace)
                .append(type)
                .append(id)
                .toHashCode();
    }
}

