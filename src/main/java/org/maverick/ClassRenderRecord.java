package org.maverick;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;

public record ClassRenderRecord(
        Class<?> theClass,
        List<Type> referencedTypes,
        Field[] fields
) {}
