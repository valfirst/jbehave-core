package org.jbehave.core.steps;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Queue;

import org.jbehave.core.model.StepPattern;

/**
 * Interface to monitor step events
 */
public interface StepMonitor {

    void stepMatchesType(String stepAsString, String previousAsString, boolean matchesType, StepType stepType,
            Method method, Object stepsInstance);

    void stepMatchesPattern(String step, boolean matches, StepPattern stepPattern, Method method, Object stepsInstance);

    /**
     * @deprecated Use {@link #convertedValueOfType(String, Type, Object, Queue)}
     */
    void convertedValueOfType(String value, Type type, Object converted, Class<?> converterClass);

    void convertedValueOfType(String value, Type type, Object converted, Queue<Class<?>> converterClasses);

    /**
     * @deprecated Use {@link #beforePerforming(String, boolean, Method)} and
     * {@link #afterPerforming(String, boolean, Method)}
     */
    @Deprecated
    void performing(String step, boolean dryRun);

    void beforePerforming(String step, boolean dryRun, Method method);

    void afterPerforming(String step, boolean dryRun, Method method);

    void usingAnnotatedNameForParameter(String name, int position);

    void usingParameterNameForParameter(String name, int position);

    void usingTableAnnotatedNameForParameter(String name, int position);

    void usingTableParameterNameForParameter(String name, int position);

    void usingNaturalOrderForParameter(int position);

    void foundParameter(String parameter, int position);

    void usingStepsContextParameter(String parameter);
}
