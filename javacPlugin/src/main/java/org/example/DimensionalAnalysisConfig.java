package org.example;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Per-class or per-method configuration for the {@code DimensionalAnalysisPlugin}.
 * <p>
 * The effective configuration for any code location is derived from the nearest enclosing
 * {@code @DimensionalAnalysisConfig} scopes (a method-level annotation wins over a class-level one):
 * <ul>
 *   <li>{@code enabled}: when {@code false}, dimensional analysis is skipped for the annotated class
 *       (including its nested types) or for the annotated method's body. An inner scope cannot
 *       re-enable a scope whose enclosing scope is disabled. A disabled method's body is not
 *       analyzed, but its declared {@code @HasUnit}/{@code @OverrideUnit} return unit is still
 *       honored at call sites.</li>
 *   <li>{@code allowedOperationsForUnitlessValues}: controls how unitless values (bare numbers and
 *       dimensionless expressions) may be combined with unit-bearing values:
 *       <ul>
 *         <li>{@code MULTIPLICATION_DIVISION} (default): {@code *} and {@code /} are allowed,
 *             {@code +} and {@code -} are errors.</li>
 *         <li>{@code ALL}: {@code +} and {@code -} with unitless values are allowed too.</li>
 *         <li>{@code NONE}: no operation may mix a unitless value with a unit-bearing value,
 *             not even {@code *} and {@code /}.</li>
 *       </ul>
 *       Arithmetic between two unitless values is always allowed.</li>
 * </ul>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DimensionalAnalysisConfig {
    boolean enabled() default true;
    AllowedOperations allowedOperationsForUnitlessValues() default AllowedOperations.MULTIPLICATION_DIVISION;

    enum AllowedOperations {
        MULTIPLICATION_DIVISION,
        ALL,
        NONE
    }
}
