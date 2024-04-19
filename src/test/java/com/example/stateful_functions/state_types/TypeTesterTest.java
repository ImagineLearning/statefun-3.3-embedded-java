package com.example.stateful_functions.state_types;

import com.example.stateful_functions.AbstractStatefulFunctionTest;
import com.example.stateful_functions.function.FunctionProvider;
import org.apache.flink.api.common.typeinfo.LocalTimeTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.GenericTypeInfo;
import org.apache.flink.api.java.typeutils.ListTypeInfo;
import org.apache.flink.api.java.typeutils.MapTypeInfo;
import org.apache.flink.api.java.typeutils.PojoTypeInfo;
import org.apache.flink.statefun.sdk.FunctionType;
import org.apache.flink.statefun.sdk.StatefulFunction;
import org.apache.flink.statefun.sdk.state.PersistedTable;
import org.apache.flink.statefun.sdk.state.PersistedValue;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;


/**
 * Ensures that the stateful function state model classes meet the requirements for Kyro serialization
 */
public class TypeTesterTest  extends AbstractStatefulFunctionTest {


    private static final Logger LOG = LoggerFactory.getLogger(TypeTesterTest.class);

    @Autowired
    ApplicationContext applicationContext;

    @Test
    public void run() throws Exception {
        LOG.info("Checking StatefulFunction Values for Kyro");


        FunctionProvider functionProvider = new FunctionProvider();
        functionProvider.setApplicationContext(applicationContext);
        functionProvider.afterPropertiesSet();

        Map<FunctionType,Class> functionsByType = functionProvider.getFunctionsByType();

        List<Class> classesToCheck = new ArrayList<>();

        for(Map.Entry<FunctionType, Class> entry : functionsByType.entrySet()) {
            Class statefulFunctionClass = entry.getValue();
            StatefulFunction statefulFunctionObject = functionProvider.functionOfType(entry.getKey());

            for(Field field : statefulFunctionClass.getDeclaredFields()) {
                if (field.getType() == PersistedValue.class) {
                    field.setAccessible(true);
                    PersistedValue persistedValueObject = (PersistedValue)field.get(statefulFunctionObject);
                    Class persistedValueClass = persistedValueObject.type();
                    classesToCheck.add(persistedValueClass);
                }
                else if (field.getType() == PersistedTable.class) {
                    field.setAccessible(true);
                    PersistedTable persistedTableObject = (PersistedTable)field.get(statefulFunctionObject);
                    Class persistedValueClass = persistedTableObject.valueType();
                    classesToCheck.add(persistedValueClass);
                }
            }
        }

        for(Class c : classesToCheck) {
            TypeTesterTest.check(c);
        }

    }

    public static void check(Class c)
    {
        HashSet<Class> hits = new HashSet<>();
        check(c, hits);
    }

    private static void check(Class modelClass, Set<Class> hits)
    {
        if (hits.contains(modelClass))
            return;
        hits.add(modelClass);
        LOG.info("Checking: " + modelClass.getSimpleName());

        Object modelObject = null;

        try {
            if (modelClass == Integer.class) {
                modelObject = Integer.valueOf(0);
            } else if (modelClass == Long.class) {
                modelObject = Long.valueOf(0);
            } else if (modelClass == List.class) {
                modelObject = Arrays.stream((new ArrayList[] {})).collect(Collectors.toList());
            }
            else {
                modelObject = modelClass.getConstructor().newInstance();
            }
        } catch (Exception ex) {
            assertTrue(modelClass.getSimpleName() + " needs parameterless constructor", false);
        }

        TypeInformation flinkTypeInfo = TypeInformation.of(modelClass);
        if (flinkTypeInfo.isBasicType())
            return;

        if (modelObject instanceof Collection<?>)
            return;

        assertTrue(modelClass.getSimpleName() + " must be A POJO, found " + flinkTypeInfo, flinkTypeInfo instanceof PojoTypeInfo);

        PojoTypeInfo pojoFlinkTypeInfo = (PojoTypeInfo)flinkTypeInfo;

        ArrayList<TypeInformation> toCheck = new ArrayList<>();

        for(Class c = modelClass; c != null; c = c.getSuperclass()) {
            for (Field modelClassField : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(modelClassField.getModifiers())) {
                    continue;
                }
                String classFieldName = modelClassField.getName();
                LOG.info("  Field: " + classFieldName);

                int flinkFieldIndex = pojoFlinkTypeInfo.getFieldIndex(classFieldName);

                // If we have a member that Flink TypeInfo does not then we will not serialize it, so fail test.
                if (-1 == flinkFieldIndex) {
                    fail("Field: " + classFieldName + " not found in Flink TypeInfo for class " + c.getName());
                }

                // is it a good type?
                Class modelFieldClass = modelClassField.getType();
                TypeInformation flinkFieldTypeInfo = pojoFlinkTypeInfo.getTypeAt(flinkFieldIndex);
                toCheck.add(flinkFieldTypeInfo);

                // Is the field in our class compatible with flink's type info entry for our field
                assertTrue(c.getSimpleName() + "." + classFieldName + " is not compatible with " + flinkFieldTypeInfo, isCompatible(modelObject, modelClass, modelClassField, modelFieldClass, flinkFieldTypeInfo, toCheck));
                assertTrue(c.getSimpleName() + "." + classFieldName + " needs correctly typed getters and setters.", hasGettersSetters(modelClass, modelFieldClass, modelClassField));

                if (!flinkFieldTypeInfo.isBasicType()) {
                    assertFalse(c.getSimpleName() + "." + classFieldName + " must not be generic, found " + flinkFieldTypeInfo, flinkFieldTypeInfo instanceof GenericTypeInfo);

                    if (flinkFieldTypeInfo instanceof MapTypeInfo) {
                        MapTypeInfo mapTypeInfo = (MapTypeInfo) flinkFieldTypeInfo;
                        toCheck.add(mapTypeInfo.getKeyTypeInfo());
                        toCheck.add(mapTypeInfo.getValueTypeInfo());
                    }

                }
            }
        }
        LOG.info("Checking: " + modelClass.getSimpleName() + " Completed.");
        for (TypeInformation checkTypeInfo : toCheck) {
            if (checkTypeInfo instanceof PojoTypeInfo) {
                TypeTesterTest.check(checkTypeInfo.getTypeClass(), hits);
            }
        }
    }

    private static boolean isCompatible(Object modelObject, Class modelClass, Field modelClassField, Class modelFieldCLass, TypeInformation flinkTypeInformation, ArrayList<TypeInformation> toCheck)
    {
        if (modelFieldCLass == flinkTypeInformation.getTypeClass()) {
            if (!isCompatibleGenerics(modelClassField, flinkTypeInformation, toCheck)) {
                return false;
            }
            return true;
        } else if (modelFieldCLass == boolean.class && flinkTypeInformation.getTypeClass() == Boolean.class) {
            return true;
        } else if (modelFieldCLass == double.class && flinkTypeInformation.getTypeClass() == Double.class) {
            return true;
        } else if (modelFieldCLass == long.class && flinkTypeInformation.getTypeClass() == Long.class) {
            return true;
        } else if (modelFieldCLass == int.class && flinkTypeInformation.getTypeClass() == Integer.class) {
            return true;
        } else if (modelFieldCLass == Set.class) {
            return false;
        } else {
            return false;
        }
    }

    private static boolean isCompatibleGenerics(Field modelClassField, TypeInformation flinkTypeInformation, ArrayList<TypeInformation> toCheck) {
        if (flinkTypeInformation instanceof MapTypeInfo) {
            MapTypeInfo flinkMapTypeInfo = (MapTypeInfo)flinkTypeInformation;
            Type modelClassFieldGenericType = modelClassField.getGenericType();

            if (modelClassFieldGenericType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType)modelClassFieldGenericType;
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

                TypeInformation flinkKeyTypeInformation = flinkMapTypeInfo.getKeyTypeInfo();
                TypeInformation flinkValueTypeInformation = flinkMapTypeInfo.getValueTypeInfo();

                if (!isCompatibleGenerics(actualTypeArguments[0], flinkKeyTypeInformation)) {
                    return false;
                }

                if (!isCompatibleGenerics(actualTypeArguments[1], flinkValueTypeInformation)) {
                    return false;
                }

                toCheck.add(flinkKeyTypeInformation);
                toCheck.add(flinkValueTypeInformation);
            }
            else {
                return false;
            }
        }
        else if (flinkTypeInformation instanceof ListTypeInfo) {
            ListTypeInfo flinkListTypeInfo = (ListTypeInfo)flinkTypeInformation;
            Type modelClassFieldGenericType = modelClassField.getGenericType();

            if (modelClassFieldGenericType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType)modelClassFieldGenericType;
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

                TypeInformation flinkElementTypeInformation = flinkListTypeInfo.getElementTypeInfo();
                if (!isCompatibleGenerics(actualTypeArguments[0], flinkElementTypeInformation)) {
                    return false;
                }

                toCheck.add(flinkElementTypeInformation);
            }
            else {
                return false;
            }
        }

        return true;
    }

    private static boolean isCompatibleGenerics(Type actualTypeArgument, TypeInformation flinkTypeInformation) {
        if (flinkTypeInformation.isBasicType()) {
            if (flinkTypeInformation.getTypeClass() != actualTypeArgument) {
                return false;
            }
        } else if( flinkTypeInformation instanceof ListTypeInfo) {
            if (!(actualTypeArgument instanceof ParameterizedType)) {
                return false;
            }
            ParameterizedType listValueParameterizedType = (ParameterizedType)actualTypeArgument;

            if (listValueParameterizedType.getRawType() != flinkTypeInformation.getTypeClass()) {
                return false;
            }
        } else if (flinkTypeInformation instanceof PojoTypeInfo){
            PojoTypeInfo flinkPojoValueTypeInformation = (PojoTypeInfo)flinkTypeInformation;

            if (flinkPojoValueTypeInformation.getTypeClass() != actualTypeArgument)  {
                return false;
            }
        } else if (flinkTypeInformation instanceof LocalTimeTypeInfo){
            LocalTimeTypeInfo flinkValueLocalTimeTypeInformation = (LocalTimeTypeInfo)flinkTypeInformation;

            if (flinkValueLocalTimeTypeInformation.getTypeClass() != actualTypeArgument)  {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    private static boolean hasGettersSetters(Class modelClass, Class fieldClass, Field field) {
        if ((field.getModifiers() & Modifier.PUBLIC) == 0) {
            String getterName = "get" + upperCaseFirstLetter(field.getName());
            String setterName = "set" + upperCaseFirstLetter(field.getName());
            boolean isBoolean = (fieldClass == boolean.class || fieldClass == Boolean.class);
            String isGetterName = "is" + upperCaseFirstLetter(field.getName());


            if (!hasSetterMethod(modelClass, fieldClass, setterName)) {
                return false;
            }

            boolean hasGetter = hasGetterMethod(modelClass, fieldClass, getterName);
            if (isBoolean && hasGetterMethod(modelClass, fieldClass, isGetterName)) {
                hasGetter = true;
            }

            if (!hasGetter) {
                return false;
            }
        }
        return true;
    }

    private static String upperCaseFirstLetter(String str)
    {
        return  str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static boolean hasSetterMethod(Class modelClass, Class fieldClass, String setterName) {
        try {
            Method setterMethod = modelClass.getMethod(setterName, fieldClass);
            if (setterMethod.getReturnType() != void.class) {
                return false;
            }
        } catch (NoSuchMethodException ex) {
            return false;
        }
        return true;
    }

    private static boolean hasGetterMethod(Class modelClass, Class fieldClass, String getterName) {
        try {
            Method getterMethod = modelClass.getMethod(getterName);
            if (getterMethod.getReturnType() != fieldClass) {
                return false;
            }
        } catch (NoSuchMethodException ex) {
            return false;
        }
        return true;
    }

}

