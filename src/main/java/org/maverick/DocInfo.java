package org.maverick;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация, которой помечаются документируемые элементы.
 * Читается документатором во время выполнения (RUNTIME).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD,
         ElementType.CONSTRUCTOR, ElementType.PARAMETER})
public @interface DocInfo {

    /** Текстовое описание элемента, попадающее в документ. */
    String value() default "";

    /** Автор / ответственный за элемент. */
    String author() default "";

    /** Версия, в которой элемент появился. */
    String since() default "";

    /**
     * Если false — документатор не будет рекурсивно разбирать классы,
     * на которые ссылается данный тип (или данное поле).
     */
    boolean deep() default true;
}
