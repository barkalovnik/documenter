package org.maverick;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public record ClassRenderRecord(
        Class<?> theClass,
        List<Type> referencedTypes,
        ArrayList<Field> fields,
        ArrayList<Constructor<?>> ctors,
        ArrayList<Method> methods
) {}
