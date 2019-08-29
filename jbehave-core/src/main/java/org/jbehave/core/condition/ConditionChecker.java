package org.jbehave.core.condition;

import java.util.function.Predicate;

public interface ConditionChecker {
    /**
     * Checks whether the <b>condition</b> matches the input <b>value</b>
     * @param condition an implementation of the condition
     * @param value the argument to be matched against the condition
     * @return the condition evaluation result
     */
    boolean check(Class<? extends Predicate<Object>> condition, Object value);
}
