package org.jbehave.core.steps;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.jbehave.core.steps.StepType.GIVEN;
import static org.jbehave.core.steps.StepType.THEN;
import static org.jbehave.core.steps.StepType.WHEN;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.jbehave.core.annotations.Conditional;
import org.jbehave.core.annotations.Given;
import org.jbehave.core.annotations.Then;
import org.jbehave.core.annotations.When;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.MostUsefulConfiguration;
import org.jbehave.core.parsers.StepPatternParser;
import org.jbehave.core.steps.AbstractCandidateSteps.DuplicateCandidateFound;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class StepFinderBehaviour {

    private StepFinder finder = new StepFinder();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldNotConsiderCompositesAsConditional() {
        CandidateSteps steps = mock(CandidateSteps.class);
        Configuration configuration = mock(Configuration.class);
        StepCandidate step = mock(StepCandidate.class);
        StepPatternParser stepPatternParser = mock(StepPatternParser.class);

        when(steps.configuration()).thenReturn(configuration);
        when(configuration.stepPatternParser()).thenReturn(stepPatternParser);
        when(stepPatternParser.getPrefix()).thenReturn("$");
        when(steps.listCandidates()).thenReturn(Arrays.asList(step));
        when(step.getMethod()).thenReturn(null);
        when(step.getName()).thenReturn("Then I perform composite");

        List<StepCandidate> candidates = finder.collectCandidates(Arrays.asList(steps));
        assertThat(candidates.size(), equalTo(1));
        assertThat(candidates.get(0), instanceOf(StepCandidate.class));
        verify(steps).configuration();
        verify(steps).listCandidates();
        verify(step).getMethod();
        verify(step).getName();
        verify(configuration).stepPatternParser();
        verify(stepPatternParser).getPrefix();
        verifyNoMoreInteractions(steps, step);
    }

    @Test
    public void shouldCollectConditionalCandidatesAcrossClassesAtMethodLevel() {
        OneClassWithConditionalGivenAtMethod stepsOne = new OneClassWithConditionalGivenAtMethod();
        TwoClassWithConditionalGivenAtMethod stepsTwo = new TwoClassWithConditionalGivenAtMethod();
        assertMulticlassCandidates(finder.collectCandidates(Arrays.asList(stepsOne, stepsTwo)));
    }

    @Test
    public void shouldCollectConditionalCandidatesAcrossClassesAtClassLevel() {
        OneClassWithConditionalGivenAtClass stepsOne = new OneClassWithConditionalGivenAtClass();
        TwoClassWithConditionalGivenAtClass stepsTwo = new TwoClassWithConditionalGivenAtClass();
        assertCandidates(finder.collectCandidates(Arrays.asList(stepsOne, stepsTwo)), Arrays.asList(
                entry(ConditionalStepCandidate.class, StepType.GIVEN, "a given"),
                entry(ConditionalStepCandidate.class, StepType.THEN, "a then one"),
                entry(ConditionalStepCandidate.class, StepType.THEN, "a then one param '$oneParam' and '$twoParam'", c -> {
                    ConditionalStepCandidate candidate = (ConditionalStepCandidate) c;
                    List<Method> methods = candidate.getMethods();
                    assertThat(methods.size(), equalTo(2));
                    assertThat(methods.get(0).getName(), equalTo("thenWithParameters1"));
                    assertThat(methods.get(1).getName(), equalTo("thenWithParameters2"));
                }),
                entry(ConditionalStepCandidate.class, StepType.THEN, "a then one param '$someParam'", c -> {
                    ConditionalStepCandidate candidate = (ConditionalStepCandidate) c;
                    List<Method> methods = candidate.getMethods();
                    assertThat(methods.size(), equalTo(2));
                    assertThat(methods.get(0).getName(), equalTo("thenWithParameter1"));
                    assertThat(methods.get(1).getName(), equalTo("thenWithParameter2"));
                }),
                entry(ConditionalStepCandidate.class, StepType.THEN, "a then two"),
                entry(ConditionalStepCandidate.class, StepType.WHEN, "a when one"),
                entry(ConditionalStepCandidate.class, StepType.WHEN, "a when two")
                ));
    }

    @Test
    public void shouldCollectConditionalCandidatesIfMethodsAndClassesAreMixed() {
        OneClassWithConditionalGivenAtClass stepsOne = new OneClassWithConditionalGivenAtClass();
        TwoClassWithConditionalGivenAtMethod stepsTwo = new TwoClassWithConditionalGivenAtMethod();
        assertCandidates(finder.collectCandidates(Arrays.asList(stepsOne, stepsTwo)), Arrays.asList(
                entry(ConditionalStepCandidate.class, StepType.GIVEN, "a given"),
                entry(ConditionalStepCandidate.class, StepType.THEN, "a then one"),
                entry(ConditionalStepCandidate.class, StepType.THEN, "a then one param '$oneParam' and '$twoParam'"),
                entry(ConditionalStepCandidate.class, StepType.THEN, "a then one param '$someParam'"),
                entry(StepCandidate.class, StepType.THEN, "a then two"),
                entry(ConditionalStepCandidate.class, StepType.WHEN, "a when one"),
                entry(StepCandidate.class, StepType.WHEN, "a when two")
                ));
    }

    @Test
    public void shouldCollectConditionalCandidates() {
        DuplicateAnnotatedSteps steps = new DuplicateAnnotatedSteps();
        assertCandidates(finder.collectCandidates(Arrays.asList(steps)), Arrays.asList(
                entry(ConditionalStepCandidate.class, StepType.GIVEN, "a given"),
                entry(StepCandidate.class, StepType.THEN, "a then"),
                entry(StepCandidate.class, StepType.WHEN, "a when")
                ));
    }

    @Test
    public void shouldCollectClassLevelConditionalCandidates() {
        ClassLevelConditionSteps steps = new ClassLevelConditionSteps();
        assertCandidates(finder.collectCandidates(Arrays.asList(steps)), Arrays.asList(
                entry(ConditionalStepCandidate.class, StepType.GIVEN, "a given"),
                entry(ConditionalStepCandidate.class, StepType.THEN, "a then"),
                entry(ConditionalStepCandidate.class, StepType.WHEN, "a when")
                ));
    }

    @Test
    public void shouldFailIfSimilarAnnotatedAndNotAnnotatedStepsWereFound() {
        expectedException.expect(DuplicateCandidateFound.class);
        expectedException.expectMessage("Given a given");
        DuplicateNotOnlyAnnotatedSteps steps = new DuplicateNotOnlyAnnotatedSteps();
        finder.collectCandidates(Arrays.asList(steps));
    }

    @Test
    public void shouldFailIfDuplicateStepsAreEncountered() {
        expectedException.expect(DuplicateCandidateFound.class);
        expectedException.expectMessage("Given a given");
        DuplicateSteps steps = new DuplicateSteps();
        finder.collectCandidates(Arrays.asList(steps));
    }

    @Test
    public void shouldFailIfDuplicateConditionalAndNotConditionalStepsAreEncountered() {
        expectedException.expect(DuplicateCandidateFound.class);
        expectedException.expectMessage("Given a given");
        GivenSteps givens = new GivenSteps();
        DuplicateAnnotatedSteps annotated = new DuplicateAnnotatedSteps();
        assertMulticlassCandidates(finder.collectCandidates(Arrays.asList(givens, annotated)));
    }

    @Test
    public void shouldFailIfDuplicateConditionalAndNotConditionalStepsAreEncounteredClassLevel() {
        expectedException.expect(DuplicateCandidateFound.class);
        expectedException.expectMessage("Given a given");
        GivenSteps givens = new GivenSteps();
        ClassLevelConditionSteps annotated = new ClassLevelConditionSteps();
        assertMulticlassCandidates(finder.collectCandidates(Arrays.asList(givens, annotated)));
    }

    @Test
    public void shouldFindStepdocs() {
        MySteps mySteps = new MySteps();
        List<Stepdoc> stepdocs = finder.stepdocs(new InstanceStepsFactory(new MostUsefulConfiguration(), mySteps).createCandidateSteps());
        Collections.sort(stepdocs);
        assertThat(stepdocs.size(), equalTo(3));
        assertThatStepdocIs(stepdocs.get(0), "givenFoo", "givenFoo(java.lang.String)", "foo named $name", "Given", GIVEN, mySteps);
        assertThatStepdocIs(stepdocs.get(1), "whenFoo", "whenFoo(java.lang.String)", "foo named $name", "When", WHEN, mySteps);
        assertThatStepdocIs(stepdocs.get(2), "thenFoo", "thenFoo(java.lang.String)", "foo named $name", "Then", THEN, mySteps);        
    }
    
    private void assertStep(StepCandidate candidate, Class<?> candidateClass, StepType stepType, String stepPattern) {
        assertThat(candidate.getClass(), equalTo(candidateClass));
        assertThat(candidate.getStepType(), equalTo(stepType));
        assertThat(candidate.getPatternAsString(), equalTo(stepPattern));
    }
    
    private void assertThatStepdocIs(Stepdoc stepdoc, String methodName, String methodSignature, String pattern, String startingWord, StepType stepType, Object stepsInstance) {
        assertThat(stepdoc.getMethod().getName(), equalTo(methodName));
        assertThat(stepdoc.toString(), containsString(methodName));
        assertThat(stepdoc.getMethodSignature(), containsString(methodName));
        assertThat(stepdoc.getPattern(), equalTo(pattern));
        assertThat(stepdoc.toString(), containsString(pattern));
        assertThat(stepdoc.getStartingWord(), equalTo(startingWord));
        assertThat(stepdoc.toString(), containsString(startingWord));
        assertThat(stepdoc.getStepType(), equalTo(stepType));
        assertThat(stepdoc.toString(), containsString(stepType.toString()));
        assertThat(stepdoc.getStepsInstance(), equalTo(stepsInstance));
        assertThat(stepdoc.toString(), containsString(stepsInstance.getClass().getName()));
    }

    private void assertMulticlassCandidates(List<StepCandidate> candidates) {
        Collections.sort(candidates, Comparator.comparing(StepCandidate::getPatternAsString));
        assertThat(candidates.size(), equalTo(5));
        assertStep(candidates.get(0), ConditionalStepCandidate.class, StepType.GIVEN, "a given");
        assertStep(candidates.get(1), StepCandidate.class, StepType.THEN, "a then one");
        assertStep(candidates.get(2), StepCandidate.class, StepType.THEN, "a then two");
        assertStep(candidates.get(3), StepCandidate.class, StepType.WHEN, "a when one");
        assertStep(candidates.get(4), StepCandidate.class, StepType.WHEN, "a when two");
    }

    private void assertCandidates(List<StepCandidate> candidates, List<AssertCandidateEntry> asserts) {
        Collections.sort(candidates, Comparator.comparing(StepCandidate::getPatternAsString));
        assertThat(candidates.size(), equalTo(asserts.size()));
        IntStream.range(0, candidates.size()).forEach(index ->
        {
            StepCandidate candidate = candidates.get(index);
            AssertCandidateEntry entry = asserts.get(index);
            assertStep(candidate, entry.getType(), entry.getStepType(), entry.getWording());
            entry.getInnerAssertion().accept(candidate);
        });
    }

    private static AssertCandidateEntry entry(Class<?> type, StepType stepType, String wording) {
        return entry(type, stepType, wording, c -> {});
    }

    private static AssertCandidateEntry entry(Class<?> type, StepType stepType, String wording,
            Consumer<StepCandidate> innerAssertion) {
        return new AssertCandidateEntry(type, stepType, wording, innerAssertion);
    }

    public static class AssertCandidateEntry {
        private final Class<?> type;
        private final StepType stepType;
        private final String wording;
        private final Consumer<StepCandidate> innerAssertion;

        private AssertCandidateEntry(Class<?> type, StepType stepType, String wording, Consumer<StepCandidate> innerAssertion) {
            this.type = type;
            this.stepType = stepType;
            this.wording = wording;
            this.innerAssertion = innerAssertion;
        }

        public Class<?> getType() {
            return type;
        }

        public StepType getStepType() {
            return stepType;
        }

        public String getWording() {
            return wording;
        }

        public Consumer<StepCandidate> getInnerAssertion() {
            return innerAssertion;
        }
    }

    static class MySteps  {
        @Given("foo named $name")
        public void givenFoo(String name) {
        }

        @When("foo named $name")
        public void whenFoo(String name) {
        }

        @Then("foo named $name")
        public void thenFoo(String name) {
        }

    }

    static class GivenSteps extends Steps {
        @Given("a given")
        public void given() {
        }
    }

    static class DuplicateAnnotatedSteps extends Steps {
        @Conditional(condition = TestCondition.class)
        @Given("a given")
        public void givenConditional1() {
        }

        @Conditional(condition = TestCondition.class, value = "skip")
        @Given("a given")
        public void givenConditional2() {
        }

        @When("a when")
        public void when() {
        }

        @Then("a then")
        public void then() {
        }
    }

    @Conditional(condition = TestCondition.class)
    static class ClassLevelConditionSteps extends Steps {
        @Given("a given")
        public void givenConditional1() {
        }

        @Given("a given")
        public void givenConditional2() {
        }

        @When("a when")
        public void when() {
        }

        @Then("a then")
        public void then() {
        }
    }

    static class OneClassWithConditionalGivenAtMethod extends Steps {
        @Conditional(condition = TestCondition.class)
        @Given("a given")
        public void givenConditional() {
        }

        @When("a when one")
        public void when() {
        }

        @Then("a then one")
        public void then() {
        }
    }

    static class TwoClassWithConditionalGivenAtMethod extends Steps {
        @Conditional(condition = TestCondition.class)
        @Given("a given")
        public void givenConditional() {
        }

        @When("a when two")
        public void when() {
        }

        @Then("a then two")
        public void then() {
        }
    }

    @Conditional(condition = TestCondition.class)
    static class OneClassWithConditionalGivenAtClass extends Steps {
        @Then("a then one param '$firstParam' and '$secondParam'")
        public void thenWithParameters2(String firstParam, String secondParam) {
        }

        @Given("a given")
        public void givenConditional() {
        }

        @When("a when one")
        public void when() {
        }

        @Then("a then one")
        public void then() {
        }

        @Then("a then one param '$someParam'")
        public void thenWithParameter1(String someParam) {
        }

        @Then("a then one param '$paramOfStep'")
        public void thenWithParameter2(String paramOfStep) {
        }

        @Then("a then one param '$oneParam' and '$twoParam'")
        public void thenWithParameters1(String oneParam, String twoParam) {
        }
    }

    @Conditional(condition = TestCondition.class)
    static class TwoClassWithConditionalGivenAtClass extends Steps {
        @Given("a given")
        public void givenConditional() {
        }

        @When("a when two")
        public void when() {
        }

        @Then("a then two")
        public void then() {
        }
    }

    static class DuplicateSteps extends Steps {
        @Given("a given")
        public void given() {
        }

        @Given("a given")
        public void duplicateGiven() {
        }
    }

    static class DuplicateNotOnlyAnnotatedSteps extends DuplicateAnnotatedSteps {
        @Given("a given")
        public void givenNotAnnotatedStep() {}
    }

    public static class TestCondition implements Predicate<Object> {
        @Override
        public boolean test(Object t)
        {
            return !t.equals("skip");
        }
    }
}
