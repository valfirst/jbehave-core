package org.jbehave.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Predicate;

/**
 * <p>Indicates that the step should be performed only if the specified condition is met. A <em>condition</em>
 * is a state that can be determined programmatically before the step is performed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Conditional {
    Class<? extends Predicate<Object>> condition();

    String value() default "";
}
