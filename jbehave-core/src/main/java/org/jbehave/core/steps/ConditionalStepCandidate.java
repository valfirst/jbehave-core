package org.jbehave.core.steps;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.jbehave.core.condition.ConditionChecker;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.Keywords;
import org.jbehave.core.parsers.StepPatternParser;
import org.jbehave.core.steps.context.StepsContext;

public class ConditionalStepCandidate extends StepCandidate {
    private final List<Method> methods;
    private final Map<Method, InjectableStepsFactory> stepsFactories;
    private final ConditionChecker conditionalChecker;

    private ConditionalStepCandidate(String patternAsString, int priority, StepType stepType, List<Method> methods,
            Class<?> stepsType, Map<Method, InjectableStepsFactory> stepsFactories, StepsContext stepsContext,
            Keywords keywords, StepPatternParser stepPatternParser, ParameterConverters parameterConverters,
            ParameterControls parameterControls, ConditionChecker conditionalChecker) {
        super(patternAsString, priority, stepType, null, stepsType, null, stepsContext, keywords, stepPatternParser,
                parameterConverters, parameterControls);
        this.methods = methods;
        this.stepsFactories = stepsFactories;
        this.conditionalChecker = conditionalChecker;
    }

    @Override
    public Method getMethod() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InjectableStepsFactory getStepsFactory() {
        throw new UnsupportedOperationException();
    }

    public List<Method> getMethods() {
        return methods;
    }

    public Map<Method, InjectableStepsFactory> getStepsFactories() {
        return stepsFactories;
    }

    @Override
    public Step createMatchedStep(String stepAsString, Map<String, String> namedParameters, List<Step> composedSteps) {
        return getStepCreator().createConditionalParametrisedStep(conditionalChecker, getMethods(), getStepsFactories(),
                stepAsString, stripStartingWord(stepAsString), namedParameters, composedSteps);
    }

    public static StepCandidate from(StepCandidate baseCandidate, List<Method> methods,
            Map<Method, InjectableStepsFactory> stepsFactories, Configuration configuration) {
        ConditionalStepCandidate candidate = new ConditionalStepCandidate(baseCandidate.getPatternAsString(),
                baseCandidate.getPriority(), baseCandidate.getStepType(), methods, baseCandidate.getStepsType(),
                stepsFactories, configuration.stepsContext(), configuration.keywords(), configuration.stepPatternParser(),
                configuration.parameterConverters(), configuration.parameterControls(),
                configuration.conditionChecker());
        candidate.useStepMonitor(configuration.stepMonitor());
        candidate.useParanamer(configuration.paranamer());
        candidate.doDryRun(configuration.storyControls().dryRun());
        return candidate;
    }
}
