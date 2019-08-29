package org.jbehave.core.condition;

import java.lang.reflect.Constructor;
import java.util.function.Predicate;

public class DefaultConditionChecker implements ConditionChecker {
    @Override
    public boolean check(Class<? extends Predicate<Object>> type, Object value) {
        try {
            Constructor<? extends Predicate<Object>> constructor = type.getConstructor();
            constructor.setAccessible(true);
            Predicate<Object> instance = constructor.newInstance();
            return instance.test(value);
        }
        catch (NoSuchMethodException e) {
            throw new IllegalStateException("Condition implementation class must have public no-args constructor");
        }
        catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new IllegalStateException(e);
        }
    }
}
