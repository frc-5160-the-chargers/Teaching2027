package org.example;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to attach a physical unit to a field, variable, method return type, or parameter.
 */
@Retention(RetentionPolicy.CLASS)
@Target({
    ElementType.FIELD,
    ElementType.LOCAL_VARIABLE,
    ElementType.METHOD,
    ElementType.PARAMETER,
    ElementType.TYPE_USE
})
public @interface HasUnit {
    String value();
}
