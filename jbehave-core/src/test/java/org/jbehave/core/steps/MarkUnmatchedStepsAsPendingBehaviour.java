package org.jbehave.core.steps;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.*;

import org.hamcrest.Matchers;
import org.jbehave.core.annotations.*;
import org.jbehave.core.annotations.AfterScenario.Outcome;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.embedder.MatchingStepMonitor;
import org.jbehave.core.failures.PendingStepFound;
import org.jbehave.core.failures.UUIDExceptionWrapper;
import org.jbehave.core.model.ExamplesTable;
import org.jbehave.core.model.Lifecycle;
import org.jbehave.core.model.Meta;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.steps.AbstractStepResult.Ignorable;
import org.jbehave.core.parsers.StepPatternParser;
import org.jbehave.core.steps.AbstractStepResult.Comment;
import org.jbehave.core.steps.StepCollector.Stage;
import org.jbehave.core.steps.StepCreator.PendingStep;
import org.jbehave.core.steps.StepFinder.ByLevenshteinDistance;
import org.junit.Test;
import org.reflections.ReflectionUtils;

import com.thoughtworks.xstream.XStream;

public class MarkUnmatchedStepsAsPendingBehaviour {

    private MarkUnmatchedStepsAsPending stepCollector = new MarkUnmatchedStepsAsPending();

    private Map<String, String> parameters = new HashMap<>();

    @Test
    public void shouldCreateExecutableStepsWhenCandidatesAreMatched() {
        // Given
        StepCandidate candidate = mock(StepCandidate.class);
        Step executableStep = mock(Step.class);

        String stepAsString = "my step";
        when(candidate.matches(stepAsString, null)).thenReturn(true);
        when(candidate.getName()).thenReturn(stepAsString);
        when(candidate.createMatchedStep(stepAsString, parameters, Collections.<Step>emptyList())).thenReturn(executableStep);
        List<CandidateSteps> steps = mockCandidateSteps(candidate);

        // When
        List<Step> executableSteps = stepCollector.collectScenarioSteps(steps, createScenario(stepAsString),
                parameters);

        // Then
        assertThat(executableSteps.size(), equalTo(1));
        assertThat(executableSteps.get(0), equalTo(executableStep));
    }

    @Test
    public void shouldCreateExecutableStepsOnlyFromPreviousNonAndStep() {
        // Given
        StepCandidate candidate = mockStepCandidate(StepType.WHEN, "candidate");
        StepCandidate andCandidate = mockStepCandidate(StepType.WHEN, "andCandidate");
        Step step = mock(Step.class);
        Step andStep = mock(Step.class);

        String myStep = "my step";
        when(candidate.matches(myStep)).thenReturn(true);
        when(candidate.createMatchedStep(myStep, parameters, Collections.<Step>emptyList())).thenReturn(step);
        String myAndStep = "And my step";
        when(andCandidate.matches(myAndStep)).thenReturn(true);
        when(andCandidate.createMatchedStep(myAndStep, parameters, Collections.<Step>emptyList())).thenReturn(andStep);

        List<CandidateSteps> steps = mockCandidateSteps(candidate, andCandidate);

        // When
        List<Step> executableSteps = stepCollector.collectScenarioSteps(steps,
                createScenario(myStep, myAndStep), parameters);

        // Then
        assertThat(executableSteps.size(), equalTo(2));
    }

    @Test
    public void shouldCreateExecutableStepsUponOutcome() {
        // Given
        StepCandidate anyCandidate = mockStepCandidate(StepType.WHEN, "anyCandidate");
        StepCandidate successCandidate = mockStepCandidate(StepType.WHEN, "successCandidate");
        StepCandidate failureCandidate = mockStepCandidate(StepType.WHEN, "failureCandidate");
        Step anyStep = mock(Step.class, "anyStep");
        Step successStep = mock(Step.class, "successStep");
        Step failureStep = mock(Step.class, "failureStep");

        String myAnyStep = "my any step";
        when(anyCandidate.matches(myAnyStep, null)).thenReturn(true);
        when(anyCandidate.createMatchedStepUponOutcome(myAnyStep, parameters, Collections.<Step>emptyList(), Outcome.ANY)).thenReturn(anyStep);
        String mySuccessStep = "my success step";
        when(successCandidate.matches(mySuccessStep, null)).thenReturn(true);
        when(successCandidate.createMatchedStepUponOutcome(mySuccessStep, parameters, Collections.<Step>emptyList(), Outcome.SUCCESS)).thenReturn(successStep);
        String myFailureStep = "my failure step";
        when(successCandidate.matches(myFailureStep, null)).thenReturn(true);
        when(successCandidate.createMatchedStepUponOutcome(myFailureStep, parameters, Collections.<Step>emptyList(), Outcome.FAILURE)).thenReturn(failureStep);

        List<CandidateSteps> steps = mockCandidateSteps(anyCandidate, successCandidate, failureCandidate);

        Lifecycle lifecycle = new Lifecycle(ExamplesTable.EMPTY, Arrays.<Lifecycle.Steps>asList(),
                asList(new Lifecycle.Steps(Outcome.ANY, asList(myAnyStep)),
                new Lifecycle.Steps(Outcome.SUCCESS, asList(mySuccessStep)),
                new Lifecycle.Steps(Outcome.FAILURE, asList(myFailureStep))));

        // When
        List<Step> executableSteps = stepCollector.collectLifecycleSteps(steps, lifecycle, Meta.EMPTY, Scope.SCENARIO,
                new MatchingStepMonitor()).get(Stage.AFTER);

        // Then
        assertThat(executableSteps.size(), equalTo(3));
        assertThat(executableSteps.get(0), equalTo(anyStep));
        assertThat(executableSteps.get(1), equalTo(successStep));
        assertThat(executableSteps.get(2), equalTo(failureStep));
    }

    @Test
    public void shouldCreateExecutableStepsUponOutcomeAndScope() {
        // Given
        StepCandidate anyCandidate = mockStepCandidate(StepType.WHEN, "anyCandidate");
        StepCandidate successCandidate = mockStepCandidate(StepType.WHEN, "successCandidate");
        StepCandidate failureCandidate = mockStepCandidate(StepType.WHEN, "failureCandidate");
        Step anyStep = mock(Step.class, "anyStep");
        Step successStep = mock(Step.class, "successStep");
        Step failureStep = mock(Step.class, "failureStep");

        String myAnyStep = "my any step";
        when(anyCandidate.matches(myAnyStep, null)).thenReturn(true);
        when(anyCandidate.createMatchedStepUponOutcome(myAnyStep, parameters, Collections.<Step>emptyList(), Outcome.ANY)).thenReturn(anyStep);
        String mySuccessStep = "my success step";
        when(successCandidate.matches(mySuccessStep, null)).thenReturn(true);
        when(successCandidate.createMatchedStepUponOutcome(mySuccessStep, parameters, Collections.<Step>emptyList(), Outcome.SUCCESS)).thenReturn(successStep);
        String myFailureStep = "my failure step";
        when(successCandidate.matches(myFailureStep, null)).thenReturn(true);
        when(successCandidate.createMatchedStepUponOutcome(myFailureStep, parameters, Collections.<Step>emptyList(), Outcome.FAILURE)).thenReturn(failureStep);

        List<CandidateSteps> steps = mockCandidateSteps(anyCandidate, successCandidate, failureCandidate);

        Scope scope = Scope.STORY;
        Lifecycle lifecycle = new Lifecycle(Arrays.<Lifecycle.Steps>asList(),
                asList(new Lifecycle.Steps(scope, Outcome.ANY, asList(myAnyStep)),
                new Lifecycle.Steps(scope, Outcome.SUCCESS, asList(mySuccessStep)),
                new Lifecycle.Steps(scope, Outcome.FAILURE, asList(myFailureStep))));

        // When
        List<Step> executableSteps = stepCollector.collectLifecycleSteps(steps, lifecycle, Meta.EMPTY, scope,
                new MatchingStepMonitor()).get(Stage.AFTER);

        // Then
        assertThat(executableSteps.size(), equalTo(3));
        assertThat(executableSteps.get(0), equalTo(anyStep));
        assertThat(executableSteps.get(1), equalTo(successStep));
        assertThat(executableSteps.get(2), equalTo(failureStep));
    }

    @Test
    public void shouldAddPrioritizedComposedStepsWhenACompositeIsMatched() {
        // Given
        StepCandidate compositeCandidate = mockStepCandidate(StepType.WHEN, "compositeCandidate");
        StepCandidate composedCandidate1 = mockStepCandidate(StepType.WHEN, "composedCandidate1");
        StepCandidate composedCandidate2 = mockStepCandidate(StepType.WHEN, "composedCandidate2");
        when(compositeCandidate.getPriority()).thenReturn(3);
        when(composedCandidate1.getPriority()).thenReturn(2);
        when(composedCandidate2.getPriority()).thenReturn(1);
        Step executableComposite = mock(Step.class, "composite");

        List<CandidateSteps> steps = mockCandidateSteps(compositeCandidate, composedCandidate2, composedCandidate1);
        String compositeAsText = "my composite step";
        when(compositeCandidate.matches(compositeAsText, null)).thenReturn(true);
        when(compositeCandidate.isComposite()).thenReturn(true);
        when(compositeCandidate.createMatchedStep(compositeAsText, parameters, Collections.<Step>emptyList())).thenReturn(
                executableComposite);

        // When
        stepCollector.collectScenarioSteps(steps, createScenario(compositeAsText), parameters);

        // Then
        verify(compositeCandidate, times(1)).
                addComposedSteps(new ArrayList<Step>(), compositeAsText, parameters,
                        asList(compositeCandidate, composedCandidate1, composedCandidate2));
    }

    @Test
    public void shouldCreatePendingStepsWhenCandidatesAreNotFound() {
        // Given
        String givenPendingStep = "Given a pending step";
        String andGivenPendingStep = "And a given pending step";
        String whenPendingStep = "When yet another pending step";
        String andWhenPendingStep = "And a when pending step";
        List<CandidateSteps> steps = mockCandidateSteps();

        // When
        List<Step> executableSteps = stepCollector.collectScenarioSteps(steps,
                createScenario(givenPendingStep, andGivenPendingStep, whenPendingStep, andWhenPendingStep),
                parameters);
        // Then
        assertThat(executableSteps.size(), equalTo(4));
        assertIsPending(executableSteps.get(0), givenPendingStep, null);
        assertIsPending(executableSteps.get(1), andGivenPendingStep, givenPendingStep);
        assertIsPending(executableSteps.get(2), whenPendingStep, givenPendingStep);
        assertIsPending(executableSteps.get(3), andWhenPendingStep, whenPendingStep);
    }

    @Test
    public void shouldCreatePendingStepsWhenCandidatesAreNotMatched() {
        // Given
        String givenPendingStep = "Given a pending step";
        String andGivenPendingStep = "And a given pending step";
        String whenPendingStep = "When yet another pending step";
        String andWhenPendingStep = "And a when pending step";
        StepCandidate firstCandidate = mockStepCandidate(StepType.WHEN, "firstCandidate");
        when(firstCandidate.matches(givenPendingStep)).thenReturn(false);
        StepCandidate secondCandidate = mockStepCandidate(StepType.WHEN, "secondCandidate");
        when(secondCandidate.matches(andGivenPendingStep)).thenReturn(false);
        StepCandidate thirdCandidate = mockStepCandidate(StepType.WHEN, "thirdCandidate");
        when(thirdCandidate.matches(whenPendingStep)).thenReturn(false);
        StepCandidate fourthCandidate = mockStepCandidate(StepType.WHEN, "fourthCandidate");
        when(fourthCandidate.matches(andWhenPendingStep)).thenReturn(false);
        List<CandidateSteps> steps = mockCandidateSteps(firstCandidate, secondCandidate, thirdCandidate, fourthCandidate);

        // When
        List<Step> executableSteps = stepCollector.collectScenarioSteps(steps,
                createScenario(givenPendingStep, andGivenPendingStep, whenPendingStep, andWhenPendingStep),
                parameters);
        // Then
        assertThat(executableSteps.size(), equalTo(4));
        assertIsPending(executableSteps.get(0), givenPendingStep, null);
        assertIsPending(executableSteps.get(1), andGivenPendingStep, givenPendingStep);
        assertIsPending(executableSteps.get(2), whenPendingStep, givenPendingStep);
        assertIsPending(executableSteps.get(3), andWhenPendingStep, whenPendingStep);
    }

    private StepCandidate mockStepCandidate(StepType stepType, String candidateName)
    {
        StepCandidate stepCandidate = mock(StepCandidate.class, candidateName);
        when(stepCandidate.getPatternAsString()).thenReturn(candidateName);
        when(stepCandidate.getStepType()).thenReturn(stepType);
        when(stepCandidate.getName()).thenReturn(candidateName);
        @SuppressWarnings("unchecked")
        Method method = ReflectionUtils.getMethods(ClassWithSimpleMethod.class, m -> m.getName().equals("simpleMethod"))
                .iterator().next();
        when(stepCandidate.getMethod()).thenReturn(method);
        return stepCandidate;
    }

    private void assertIsPending(Step step, String stepAsString, String previousNonAndStep) {
        assertThat(step, instanceOf(PendingStep.class));
        PendingStep pendingStep = (PendingStep) step;
        assertThat(pendingStep.stepAsString(), equalTo(stepAsString));
        assertThat(pendingStep.previousNonAndStepAsString(), equalTo(previousNonAndStep));
        Throwable throwable = step.perform(null, null).getFailure();
        assertThat(throwable, instanceOf(PendingStepFound.class));
        assertThat(throwable.getMessage(), equalTo(stepAsString));

    }

    @Test
    public void shouldCreateIgnorableSteps() {
        // Given
        StepCandidate candidate = mock(StepCandidate.class);

        String stepAsString = "my ignorable step";
        when(candidate.ignore(stepAsString)).thenReturn(true);
        when(candidate.getName()).thenReturn(stepAsString);
        List<CandidateSteps> steps = mockCandidateSteps(candidate);

        // When
        List<Step> executableSteps = stepCollector.collectScenarioSteps(steps, createScenario(stepAsString), parameters);
        // Then
        assertThat(executableSteps.size(), equalTo(1));
        StepResult result = executableSteps.get(0).perform(null, null);
        assertThat(result, Matchers.instanceOf(Ignorable.class));
    }

    @Test
    public void shouldCreateComment() {
        // Given
        StepCandidate candidate = mock(StepCandidate.class);

        String stepAsString = "comment";
        when(candidate.comment(stepAsString)).thenReturn(true);
        when(candidate.getName()).thenReturn(stepAsString);
        List<CandidateSteps> steps = mockCandidateSteps(candidate);

        // When
        List<Step> executableSteps = stepCollector.collectScenarioSteps(steps, createScenario(stepAsString), parameters);
        // Then
        assertThat(executableSteps.size(), equalTo(1));
        StepResult result = executableSteps.get(0).perform(null, null);
        assertThat(result, Matchers.instanceOf(Comment.class));
    }

    @Test
    public void shouldCollectBeforeAndAfterScenarioAnnotatedSteps() {
        assertThatBeforeAndAfterScenarioAnnotatedStepsCanBeCollectedForGivenType(ScenarioType.NORMAL);
        assertThatBeforeAndAfterScenarioAnnotatedStepsCanBeCollectedForGivenType(ScenarioType.EXAMPLE);
        assertThatBeforeAndAfterScenarioAnnotatedStepsCanBeCollectedForGivenType(ScenarioType.ANY);
    }

    private void assertThatBeforeAndAfterScenarioAnnotatedStepsCanBeCollectedForGivenType(ScenarioType scenarioType) {
        // Given some candidate steps classes with before and after scenario
        // methods
        Meta storyAndScenarioMeta = mock(Meta.class);
        CandidateSteps steps1 = mock(Steps.class);
        CandidateSteps steps2 = mock(Steps.class);
        BeforeOrAfterStep bafStep11 = mock(BeforeOrAfterStep.class);
        BeforeOrAfterStep bafStep12 = mock(BeforeOrAfterStep.class);
        BeforeOrAfterStep bafStep21 = mock(BeforeOrAfterStep.class);
        BeforeOrAfterStep bafStep22 = mock(BeforeOrAfterStep.class);
        Step stepBefore1 = mock(Step.class);
        Step stepBefore2 = mock(Step.class);
        Step stepAfter1 = mock(Step.class);
        Step stepAfter2 = mock(Step.class);
        when(bafStep11.getStage()).thenReturn(Stage.BEFORE);
        when(bafStep11.createStepWith(storyAndScenarioMeta)).thenReturn(stepBefore1);
        when(bafStep12.getStage()).thenReturn(Stage.BEFORE);
        when(bafStep12.createStepWith(storyAndScenarioMeta)).thenReturn(stepBefore2);
        when(bafStep21.getStage()).thenReturn(Stage.AFTER);
        when(bafStep21.createStepUponOutcome(storyAndScenarioMeta)).thenReturn(stepAfter1);
        when(bafStep22.getStage()).thenReturn(Stage.AFTER);
        when(bafStep22.createStepUponOutcome(storyAndScenarioMeta)).thenReturn(stepAfter2);
        when(steps1.listBeforeOrAfterScenario(scenarioType)).thenReturn(asList(bafStep11, bafStep12));
        when(steps2.listBeforeOrAfterScenario(scenarioType)).thenReturn(asList(bafStep21, bafStep22));

        // When we collect the list of steps
        List<Step> beforeSteps = stepCollector.collectBeforeOrAfterScenarioSteps(asList(steps1, steps2),
                storyAndScenarioMeta, Stage.BEFORE, scenarioType);
        List<Step> afterSteps = stepCollector.collectBeforeOrAfterScenarioSteps(asList(steps1, steps2),
                storyAndScenarioMeta, Stage.AFTER, scenarioType);

        // Then all before and after steps should be added
        assertThat(beforeSteps, equalTo(asList(stepBefore1, stepBefore2)));
        assertThat(afterSteps, equalTo(asList(stepAfter1, stepAfter2)));
    }


    @Test
    public void shouldCollectBeforeAndAfterStoryAnnotatedSteps() {
        // Given some candidate steps classes with before and after story
        // methods
        Story story = new Story();
        Meta storyMeta = story.getMeta();

        CandidateSteps steps1 = mock(Steps.class);
        CandidateSteps steps2 = mock(Steps.class);
        BeforeOrAfterStep bafStep11 = mock(BeforeOrAfterStep.class);
        BeforeOrAfterStep bafStep21 = mock(BeforeOrAfterStep.class);
        BeforeOrAfterStep bafStep12 = mock(BeforeOrAfterStep.class);
        BeforeOrAfterStep bafStep22 = mock(BeforeOrAfterStep.class);
        Step stepBefore1 = mock(Step.class);
        Step stepBefore2 = mock(Step.class);
        Step stepAfter1 = mock(Step.class);
        Step stepAfter2 = mock(Step.class);

        boolean givenStory = false;
        when(bafStep11.getStage()).thenReturn(Stage.BEFORE);
        when(bafStep11.createStepWith(storyMeta)).thenReturn(stepBefore1);
        when(bafStep21.getStage()).thenReturn(Stage.BEFORE);
        when(bafStep21.createStepWith(storyMeta)).thenReturn(stepBefore2);
        when(bafStep12.getStage()).thenReturn(Stage.AFTER);
        when(bafStep12.createStepWith(storyMeta)).thenReturn(stepAfter1);
        when(bafStep22.getStage()).thenReturn(Stage.AFTER);
        when(bafStep22.createStepWith(storyMeta)).thenReturn(stepAfter2);
        when(steps1.listBeforeOrAfterStory(givenStory)).thenReturn(asList(bafStep11, bafStep12));
        when(steps2.listBeforeOrAfterStory(givenStory)).thenReturn(asList(bafStep21, bafStep22));

        // When we collect the list of steps
        List<Step> beforeSteps = stepCollector.collectBeforeOrAfterStorySteps(asList(steps1, steps2), story,
                Stage.BEFORE, givenStory);
        List<Step> afterSteps = stepCollector.collectBeforeOrAfterStorySteps(asList(steps1, steps2), story,
                Stage.AFTER, givenStory);

        // Then all before and after steps should be added
        assertThat(beforeSteps, equalTo(asList(stepBefore1, stepBefore2)));
        assertThat(afterSteps, equalTo(asList(stepAfter1, stepAfter2)));
    }

    @Test
    public void shouldCollectBeforeAndAfterStoriesAnnotatedSteps() {
        // Given some candidate steps classes with before and after stories
        // methods
        CandidateSteps steps1 = mock(Steps.class);
        CandidateSteps steps2 = mock(Steps.class);
        BeforeOrAfterStep bafStep11 = mock(BeforeOrAfterStep.class);
        BeforeOrAfterStep bafStep21 = mock(BeforeOrAfterStep.class);
        BeforeOrAfterStep bafStep12 = mock(BeforeOrAfterStep.class);
        BeforeOrAfterStep bafStep22 = mock(BeforeOrAfterStep.class);
        Step stepBefore1 = mock(Step.class);
        Step stepBefore2 = mock(Step.class);
        Step stepAfter1 = mock(Step.class);
        Step stepAfter2 = mock(Step.class);

        when(bafStep11.getStage()).thenReturn(Stage.BEFORE);
        when(bafStep11.createStep()).thenReturn(stepBefore1);
        when(bafStep21.getStage()).thenReturn(Stage.BEFORE);
        when(bafStep21.createStep()).thenReturn(stepBefore2);
        when(bafStep12.getStage()).thenReturn(Stage.AFTER);
        when(bafStep12.createStep()).thenReturn(stepAfter1);
        when(bafStep22.getStage()).thenReturn(Stage.AFTER);
        when(bafStep22.createStep()).thenReturn(stepAfter2);
        when(steps1.listBeforeOrAfterStories()).thenReturn(asList(bafStep11, bafStep12));
        when(steps2.listBeforeOrAfterStories()).thenReturn(asList(bafStep21, bafStep22));

        // When we collect the list of steps
        List<Step> beforeSteps = stepCollector.collectBeforeOrAfterStoriesSteps(asList(steps1, steps2), Stage.BEFORE);
        List<Step> afterSteps = stepCollector.collectBeforeOrAfterStoriesSteps(asList(steps1, steps2), Stage.AFTER);

        // Then all before and after steps should be added
        assertThat(beforeSteps, equalTo(asList(stepBefore1, stepBefore2)));
        assertThat(afterSteps, equalTo(asList(stepAfter1, stepAfter2)));
    }

    @Test
    public void shouldSortCandidateStepsByPriorityByDefault() {
        // Given some candidate steps classes
        // and some methods split across them
        CandidateSteps steps1 = mock(Steps.class);
        CandidateSteps steps2 = mock(Steps.class);
        StepCandidate candidate1 = mockStepCandidate(StepType.WHEN, "");
        StepCandidate candidate2 = mockStepCandidate(StepType.THEN, "");
        StepCandidate candidate3 = mockStepCandidate(StepType.GIVEN, "");
        StepCandidate candidate4 = mockStepCandidate(StepType.AND, "");
        Step step1 = mock(Step.class);
        Step step2 = mock(Step.class);
        Step step3 = mock(Step.class);
        Step step4 = mock(Step.class);
        mockPrefixExtraction(steps1);

        when(steps1.listCandidates()).thenReturn(asList(candidate1, candidate2));
        when(steps2.listCandidates()).thenReturn(asList(candidate3, candidate4));

        // all matching the same step string with different priorities
        String stepAsString = "Given a step";
        when(candidate1.matches(stepAsString, null)).thenReturn(true);
        when(candidate2.matches(stepAsString, null)).thenReturn(true);
        when(candidate3.matches(stepAsString, null)).thenReturn(true);
        when(candidate4.matches(stepAsString, null)).thenReturn(true);
        when(candidate1.getPriority()).thenReturn(1);
        when(candidate2.getPriority()).thenReturn(2);
        when(candidate3.getPriority()).thenReturn(3);
        when(candidate4.getPriority()).thenReturn(4);
        when(candidate1.createMatchedStep(stepAsString, parameters, Collections.<Step>emptyList())).thenReturn(step1);
        when(candidate2.createMatchedStep(stepAsString, parameters, Collections.<Step>emptyList())).thenReturn(step2);
        when(candidate3.createMatchedStep(stepAsString, parameters, Collections.<Step>emptyList())).thenReturn(step3);
        when(candidate4.createMatchedStep(stepAsString, parameters, Collections.<Step>emptyList())).thenReturn(step4);

        // When we collect the list of steps
        List<Step> steps = stepCollector.collectScenarioSteps(asList(steps1, steps2), createScenario(stepAsString), parameters);

        // Then the step with highest priority is returned
        assertThat(step4, equalTo(steps.get(0)));
    }

    @Test
    public void shouldPrioritiseCandidateStepsByInjectableStrategy() {
        // Given some candidate steps classes
        // and some methods split across them
        CandidateSteps steps1 = mock(Steps.class);
        CandidateSteps steps2 = mock(Steps.class);
        StepCandidate candidate1 = mock(StepCandidate.class);
        StepCandidate candidate2 = mock(StepCandidate.class);
        StepCandidate candidate3 = mock(StepCandidate.class);
        StepCandidate candidate4 = mock(StepCandidate.class);
        Step step1 = mock(Step.class);
        Step step2 = mock(Step.class);
        Step step3 = mock(Step.class);
        Step step4 = mock(Step.class);
        mockPrefixExtraction(steps1);

        when(steps1.listCandidates()).thenReturn(asList(candidate1, candidate2));
        when(steps2.listCandidates()).thenReturn(asList(candidate3, candidate4));

        // all matching the same step string with different priorities
        String stepAsString = "Given a step";
        when(candidate1.matches(stepAsString, null)).thenReturn(true);
        when(candidate2.matches(stepAsString, null)).thenReturn(true);
        when(candidate3.matches(stepAsString, null)).thenReturn(true);
        when(candidate4.matches(stepAsString, null)).thenReturn(true);
        String given = "Given I do something";
        when(candidate1.getPatternAsString()).thenReturn(given);
        when(candidate1.getName()).thenReturn(given);
        String when = "When I do something ";
        when(candidate2.getPatternAsString()).thenReturn(when);
        when(candidate2.getName()).thenReturn(when);
        String then = "Then I do something";
        when(candidate3.getPatternAsString()).thenReturn(then);
        when(candidate3.getName()).thenReturn(then);
        String and = "And I do something";
        when(candidate4.getPatternAsString()).thenReturn(and);
        when(candidate4.getName()).thenReturn(and);
        when(candidate1.createMatchedStep(stepAsString, parameters, Collections.<Step>emptyList())).thenReturn(step1);
        when(candidate2.createMatchedStep(stepAsString, parameters, Collections.<Step>emptyList())).thenReturn(step2);
        when(candidate3.createMatchedStep(stepAsString, parameters, Collections.<Step>emptyList())).thenReturn(step3);
        when(candidate4.createMatchedStep(stepAsString, parameters, Collections.<Step>emptyList())).thenReturn(step4);

        StepCollector stepCollector = new MarkUnmatchedStepsAsPending(new StepFinder(new ByLevenshteinDistance()));
        List<Step> steps = stepCollector.collectScenarioSteps(asList(steps1, steps2), createScenario(stepAsString), parameters);

        // Then the step with highest priority is returned
        assertThat(step4, equalTo(steps.get(0)));
    }

    @Test
    public void afterScenarioStepsShouldBeInReverseOrder() {
        List<CandidateSteps> steps = new ArrayList<>();
        steps.add(new ClassWithMethodsAandB());
        steps.add(new ClassWithMethodsCandD());
        String stepsAsString = steps.toString(); // includes object ID numbers
                                                 // from JVM

        XStream xs = new XStream();

        ScenarioType scenarioType = ScenarioType.NORMAL;
        List<Step> subset = stepCollector.collectBeforeOrAfterScenarioSteps(steps, null, Stage.BEFORE, scenarioType);
        String subsetAsXML = xs.toXML(subset);
        assertThat(subsetAsXML.indexOf("<name>a</name>"), greaterThan(-1)); // there
        assertThat(subsetAsXML.indexOf("<name>b</name>"), equalTo(-1)); // not there
        assertThat(subsetAsXML.indexOf("<name>c</name>"), greaterThan(-1)); // there
        assertThat(subsetAsXML.indexOf("<name>d</name>"), equalTo(-1)); // not there
        assertThat(subsetAsXML.indexOf("<name>a</name>"), lessThan(subsetAsXML.indexOf("<name>c</name>"))); // there

        assertThat(stepsAsString, equalTo(steps.toString())); // steps have not been mutated.

        subset = stepCollector.collectBeforeOrAfterScenarioSteps(steps, null, Stage.AFTER, scenarioType);
        subsetAsXML = xs.toXML(subset);
        assertThat(subsetAsXML.indexOf("<name>a</name>"), equalTo(-1)); // not there
        assertThat(subsetAsXML.indexOf("<name>b</name>"), greaterThan(-1)); // there
        assertThat(subsetAsXML.indexOf("<name>c</name>"), equalTo(-1)); // not there
        assertThat(subsetAsXML.indexOf("<name>d</name>"), greaterThan(-1)); // there
        assertThat(subsetAsXML.indexOf("<name>d</name>"), lessThan(subsetAsXML.indexOf("<name>b</name>"))); // reverse order

    }

    @Test
    public void shouldInvokeBeforeOrAfterScenarioWithParameter() {
        BeforeOrAfterScenarioWithParameterSteps steps = new BeforeOrAfterScenarioWithParameterSteps();
        Meta meta = beforeAndAfterMeta();

        ScenarioType scenarioType = ScenarioType.NORMAL;
        List<Step> beforeSteps = stepCollector.collectBeforeOrAfterScenarioSteps(asList((CandidateSteps) steps), meta,
                Stage.BEFORE, scenarioType);
        beforeSteps.get(0).perform(null, null);
        assertThat(steps.value, equalTo("before"));

        List<Step> afterSteps = stepCollector.collectBeforeOrAfterScenarioSteps(asList((CandidateSteps) steps), meta,
                Stage.AFTER, scenarioType);
        afterSteps.get(0).perform(null, null);
        assertThat(steps.value, equalTo("after"));
    }

    @Test
    public void shouldInvokeBeforeOrAfterScenarioWithParameterAndException() {
        BeforeOrAfterScenarioWithParameterAndExceptionSteps steps = new BeforeOrAfterScenarioWithParameterAndExceptionSteps();
        Meta meta = beforeAndAfterMeta();

        UUIDExceptionWrapper failureOccurred = new UUIDExceptionWrapper();

        ScenarioType scenarioType = ScenarioType.NORMAL;
        List<Step> beforeSteps = stepCollector.collectBeforeOrAfterScenarioSteps(asList((CandidateSteps) steps), meta,
                Stage.BEFORE, scenarioType);
        beforeSteps.get(0).doNotPerform(null, failureOccurred);
        assertThat(steps.value, equalTo("before"));
        assertThat(steps.exception, equalTo(failureOccurred));

        List<Step> afterSteps = stepCollector.collectBeforeOrAfterScenarioSteps(asList((CandidateSteps) steps), meta,
                Stage.AFTER, scenarioType);
        failureOccurred = new UUIDExceptionWrapper();
        afterSteps.get(0).doNotPerform(null, failureOccurred);
        assertThat(steps.value, equalTo("after"));
        assertThat(steps.exception, equalTo(failureOccurred));
    }

    @Test
    public void shouldInvokeBeforeOrAfterStoryWithParameter() {
        BeforeOrAfterStoryWithParameter steps = new BeforeOrAfterStoryWithParameter();
        Story story = mock(Story.class);
        when(story.getMeta()).thenReturn(beforeAndAfterMeta());

        List<Step> beforeSteps = stepCollector.collectBeforeOrAfterStorySteps(asList((CandidateSteps) steps), story,
                Stage.BEFORE, false);
        beforeSteps.get(0).perform(null, null);
        assertThat(steps.value, equalTo("before"));

        List<Step> afterSteps = stepCollector.collectBeforeOrAfterStorySteps(asList((CandidateSteps) steps), story,
                Stage.AFTER, false);
        afterSteps.get(0).perform(null, null);
        assertThat(steps.value, equalTo("after"));
    }

    @Test
    public void shouldInvokeBeforeOrAfterStoryWithParameterAndException() {
        BeforeOrAfterStoryWithParameterAndExceptionSteps steps = new BeforeOrAfterStoryWithParameterAndExceptionSteps();
        Story story = mock(Story.class);
        when(story.getMeta()).thenReturn(beforeAndAfterMeta());

        List<Step> beforeSteps = stepCollector.collectBeforeOrAfterStorySteps(asList((CandidateSteps) steps), story,
                Stage.BEFORE, false);
        UUIDExceptionWrapper failureOccurred = new UUIDExceptionWrapper();
        beforeSteps.get(0).doNotPerform(null, failureOccurred);
        assertThat(steps.value, equalTo("before"));
        assertThat(steps.exception, equalTo(failureOccurred));

        List<Step> afterSteps = stepCollector.collectBeforeOrAfterStorySteps(asList((CandidateSteps) steps), story,
                Stage.AFTER, false);
        failureOccurred = new UUIDExceptionWrapper();
        afterSteps.get(0).doNotPerform(null, failureOccurred);
        assertThat(steps.value, equalTo("after"));
        assertThat(steps.exception, equalTo(failureOccurred));
    }

    private List<CandidateSteps> mockCandidateSteps(StepCandidate... candidate) {
        CandidateSteps steps = mock(Steps.class);
        mockPrefixExtraction(steps);
        when(steps.listCandidates()).thenReturn(asList(candidate));
        return Collections.singletonList(steps);
    }

    private void mockPrefixExtraction(CandidateSteps steps)
    {
        Configuration configuration = mock(Configuration.class);
        StepPatternParser stepPatternParser = mock(StepPatternParser.class);
        when(configuration.stepPatternParser()).thenReturn(stepPatternParser);
        when(stepPatternParser.getPrefix()).thenReturn("$");
        when(steps.configuration()).thenReturn(configuration);
    }


    private Scenario createScenario(String... stepsAsStrings) {
        return new Scenario(asList(stepsAsStrings));
    }

    private Meta beforeAndAfterMeta() {
        Properties properties = new Properties();
        properties.put("before", "before");
        properties.put("after", "after");
        return new Meta(properties);
    }

    public static class ClassWithSimpleMethod {
        public void simpleMethod() { }
    }

    public static class ClassWithMethodsAandB extends Steps {
        @BeforeScenario
        public void a() {
        }

        @AfterScenario
        public void b() {
        }
    }

    public static class ClassWithMethodsCandD extends Steps {
        @BeforeScenario
        public void c() {
        }

        @AfterScenario
        public void d() {
        }
    }

    public static class BeforeOrAfterScenarioWithParameterSteps extends Steps {
        private String value;

        @BeforeScenario
        public void beforeScenario(@Named("before") String before) {
            this.value = before;
        }

        @AfterScenario
        public void afterScenario(@Named("after") String after) {
            this.value = after;
        }
    }

    public static class BeforeOrAfterScenarioWithParameterAndExceptionSteps extends Steps {
        private String value;
        private UUIDExceptionWrapper exception;

        @BeforeScenario
        public void beforeScenario(@Named("before") String before, UUIDExceptionWrapper exception) {
            this.value = before;
            this.exception = exception;
        }

        @AfterScenario
        public void afterScenario(@Named("after") String after, UUIDExceptionWrapper exception) {
            this.value = after;
            this.exception = exception;
        }
    }

    public static class BeforeOrAfterStoryWithParameter extends Steps {
        private String value;

        @BeforeStory
        public void beforeStory(@Named("before") String before) {
            this.value = before;
        }

        @AfterStory
        public void afterStory(@Named("after") String after) {
            this.value = after;
        }
    }

    public static class BeforeOrAfterStoryWithParameterAndExceptionSteps extends Steps {
        private String value;
        private UUIDExceptionWrapper exception;

        @BeforeStory
        public void beforeStory(@Named("before") String before, UUIDExceptionWrapper exception) {
            this.value = before;
            this.exception = exception;
        }

        @AfterStory
        public void afterStory(@Named("after") String after, UUIDExceptionWrapper exception) {
            this.value = after;
            this.exception = exception;
        }
    }
}
