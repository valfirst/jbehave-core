package org.jbehave.core.steps;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.jbehave.core.steps.StepType.GIVEN;
import static org.jbehave.core.steps.StepType.THEN;
import static org.jbehave.core.steps.StepType.WHEN;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.jbehave.core.annotations.Conditional;
import org.jbehave.core.annotations.Given;
import org.jbehave.core.annotations.Then;
import org.jbehave.core.annotations.When;
import org.jbehave.core.configuration.MostUsefulConfiguration;
import org.jbehave.core.steps.AbstractCandidateSteps.DuplicateCandidateFound;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class StepFinderBehaviour {

    private StepFinder finder = new StepFinder();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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
        assertMulticlassCandidates(finder.collectCandidates(Arrays.asList(stepsOne, stepsTwo)));
    }

    @Test
    public void shouldCollectConditionalCandidatesIfMethodsAndClassesAreMixed() {
        OneClassWithConditionalGivenAtClass stepsOne = new OneClassWithConditionalGivenAtClass();
        TwoClassWithConditionalGivenAtMethod stepsTwo = new TwoClassWithConditionalGivenAtMethod();
        assertMulticlassCandidates(finder.collectCandidates(Arrays.asList(stepsOne, stepsTwo)));
    }

    @Test
    public void shouldCollectConditionalCandidates() {
        DuplicateAnnotatedSteps steps = new DuplicateAnnotatedSteps();
        assertCandidates(finder.collectCandidates(Arrays.asList(steps)));
    }

    @Test
    public void shouldCollectClassLevelConditionalCandidates() {
        ClassLevelConditionSteps steps = new ClassLevelConditionSteps();
        assertCandidates(finder.collectCandidates(Arrays.asList(steps)));
    }

    @Test
    public void shouldFailIfSimilarAnnotatedAndNotAnnotatedStepsWereFound() {
        expectedException.expect(DuplicateCandidateFound.class);
        expectedException.expectMessage("GIVEN a given");
        DuplicateNotOnlyAnnotatedSteps steps = new DuplicateNotOnlyAnnotatedSteps();
        finder.collectCandidates(Arrays.asList(steps));
    }

    @Test
    public void shouldFailIfDuplicateStepsAreEncountered() {
        expectedException.expect(DuplicateCandidateFound.class);
        expectedException.expectMessage("GIVEN a given");
        DuplicateSteps steps = new DuplicateSteps();
        finder.collectCandidates(Arrays.asList(steps));
    }

    @Test
    public void shouldFailIfDuplicateConditionalAndNotConditionalStepsAreEncountered() {
        expectedException.expect(DuplicateCandidateFound.class);
        expectedException.expectMessage("GIVEN a given");
        GivenSteps givens = new GivenSteps();
        DuplicateAnnotatedSteps annotated = new DuplicateAnnotatedSteps();
        assertMulticlassCandidates(finder.collectCandidates(Arrays.asList(givens, annotated)));
    }

    @Test
    public void shouldFailIfDuplicateConditionalAndNotConditionalStepsAreEncounteredClassLevel() {
        expectedException.expect(DuplicateCandidateFound.class);
        expectedException.expectMessage("GIVEN a given");
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

    private void assertCandidates(List<StepCandidate> candidates) {
        Collections.sort(candidates, Comparator.comparing(StepCandidate::getPatternAsString));
        assertThat(candidates.size(), equalTo(3));
        assertStep(candidates.get(0), ConditionalStepCandidate.class, StepType.GIVEN, "a given");
        assertStep(candidates.get(1), StepCandidate.class, StepType.THEN, "a then");
        assertStep(candidates.get(2), StepCandidate.class, StepType.WHEN, "a when");
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
