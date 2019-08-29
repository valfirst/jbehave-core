package org.jbehave.core.condition;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.function.Predicate;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DefaultConditionCheckerBehaviour
{
    private final ConditionChecker checker = new DefaultConditionChecker();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldReturnExpectedValue() {
        assertThat(checker.check(TestCondition.class, false), is(false));
        assertThat(checker.check(TestCondition.class, true), is(true));
    }
    
    @Test
    public void shouldFailIfConditionHasNoArgsConstructor() {
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Condition implementation class must have public no-args constructor");
        checker.check(ErrorCondition.class, false);
    }

    public static class TestCondition implements Predicate<Object> {
        @Override
        public boolean test(Object t) {
            return t.equals(Boolean.TRUE);
        }
    }

    public static class ErrorCondition implements Predicate<Object> {
        public ErrorCondition(String arg) {}

        @Override
        public boolean test(Object t) {
            return t.equals(Boolean.TRUE);
        }
    }
}
