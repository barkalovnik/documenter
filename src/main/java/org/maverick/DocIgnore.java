package org.maverick;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Элемент (класс, поле, метод, конструктор), помеченный этой аннотацией,
 * не попадает в документ и не участвует в рекурсивном обходе.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface DocIgnore {
}
