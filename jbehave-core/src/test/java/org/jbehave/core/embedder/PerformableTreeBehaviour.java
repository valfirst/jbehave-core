package org.jbehave.core.embedder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.argThat;
import static org.junit.Assert.assertNull;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.jbehave.core.annotations.ScenarioType;
import org.jbehave.core.annotations.Scope;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.Keywords;
import org.jbehave.core.configuration.MostUsefulConfiguration;
import org.jbehave.core.embedder.PerformableTree.RunContext;
import org.jbehave.core.exception.DependsOnFailedException;
import org.jbehave.core.exception.DependsOnNotFoundException;
import org.jbehave.core.failures.BatchFailures;
import org.jbehave.core.failures.UUIDExceptionWrapper;
import org.jbehave.core.io.AbsolutePathCalculator;
import org.jbehave.core.io.StoryLoader;
import org.jbehave.core.model.ExamplesTable;
import org.jbehave.core.model.GivenStories;
import org.jbehave.core.model.GivenStory;
import org.jbehave.core.model.Meta;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.parsers.StoryParser;
import org.jbehave.core.reporters.StoryReporter;
import org.jbehave.core.steps.CandidateSteps;
import org.jbehave.core.steps.MarkUnmatchedStepsAsPending;
import org.jbehave.core.steps.Step;
import org.jbehave.core.steps.StepCollector;
import org.jbehave.core.steps.context.StepsContext;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;

/**
 * @author Valery Yatsynovich
 */
public class PerformableTreeBehaviour {

    private static final String GIVEN_SCENARIO_FAIL = "Scenario: given scenario title\nWhen I fail";
    private static final String STORY_PATH = "path";
    private static final Story EMPTY_STORY = new Story(STORY_PATH, null, null, null, null, Collections.emptyList());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String GIVEN_STORY = "given story";
    private static final String GIVEN_WHEN_STEP = "Given when step";
    private static final String BASE_SCENARIO_TITLE = "base scenario title";
    private static final String WHEN_STEP = "When step";
    private static final String DEPENDS_ON_ID = "@dependsOnId id";
    private static final String BEFORE_STEP = "Before step";
    private static final String AFTER_STEP = "After step";

    @Test
    public void shouldAddNotAllowedPerformableScenariosToPerformableStory() {
        Scenario scenario = new Scenario("scenario title", Meta.createMeta("@skip", new Keywords()));
        Story story = new Story(STORY_PATH, Collections.singletonList(scenario));
        List<Story> stories = Collections.singletonList(story);

        StepCollector stepCollector = mock(StepCollector.class);
        Configuration configuration = mock(Configuration.class);
        when(configuration.stepCollector()).thenReturn(stepCollector);
        when(configuration.storyControls()).thenReturn(new StoryControls());
        List<CandidateSteps> candidateSteps = Collections.emptyList();
        EmbedderMonitor embedderMonitor = mock(EmbedderMonitor.class);
        MetaFilter filter = new MetaFilter("-skip", embedderMonitor);
        BatchFailures failures = mock(BatchFailures.class);

        PerformableTree performableTree = new PerformableTree();
        PerformableTree.RunContext runContext = performableTree.newRunContext(configuration, candidateSteps,
                embedderMonitor, filter, failures);
        performableTree.addStories(runContext, stories);

        assertThat(performableTree.getRoot().getStories().get(0).getScenarios().size(), equalTo(1));

        InOrder ordered = inOrder(stepCollector);
        ordered.verify(stepCollector).collectBeforeOrAfterStoriesSteps(candidateSteps, StepCollector.Stage.BEFORE);
        ordered.verify(stepCollector).collectLifecycleSteps(eq(candidateSteps), eq(story.getLifecycle()),
                any(Meta.class), eq(Scope.STORY), any(MatchingStepMonitor.class));
        ordered.verify(stepCollector).collectBeforeOrAfterStorySteps(candidateSteps, story, StepCollector.Stage.BEFORE,
                false);
        ordered.verify(stepCollector).collectBeforeOrAfterStorySteps(candidateSteps, story, StepCollector.Stage.AFTER,
                false);
        ordered.verify(stepCollector).collectBeforeOrAfterStoriesSteps(candidateSteps, StepCollector.Stage.AFTER);
        verifyNoMoreInteractions(stepCollector);
    }

    @Test
    public void shouldNotSkipStoryWhenGivenStoryIsFailed() {
        RunContext context = performStoryRun(false, GIVEN_SCENARIO_FAIL);
        assertThat(context.failureOccurred(), is(false));
        assertThat(context.configuration().storyControls().skipStoryIfGivenStoryFailed(), is(false));
    }

    @Test
    public void shouldSkipStoryWhenGivenStoryIsFailed() {
        RunContext context = performStoryRun(true, GIVEN_SCENARIO_FAIL);
        assertThat(context.failureOccurred(), is(true));
        assertThat(context.configuration().storyControls().skipStoryIfGivenStoryFailed(), is(true));
    }

    @Test
    public void shouldNotSkipStoryWhenGivenStoryIsPassed() {
        RunContext context = performStoryRun(true, "Scenario: given scenario title");
        assertThat(context.failureOccurred(), is(false));
        assertThat(context.configuration().storyControls().skipStoryIfGivenStoryFailed(), is(true));
    }

    @Test
    public void shouldSkipScenarioWhenDependsOnIdIsEmpty() {
        Scenario scenario = new Scenario(BASE_SCENARIO_TITLE, Meta.createMeta(DEPENDS_ON_ID,
                new Keywords()), null, null, Collections.singletonList(WHEN_STEP));
        Story story = new Story(STORY_PATH, null, null, null, null,
                Collections.singletonList(scenario));
        Configuration configuration = mock(Configuration.class);
        when(configuration.stepCollector()).thenReturn(new MarkUnmatchedStepsAsPending());
        when(configuration.storyControls()).thenReturn(new StoryControls());
        when(configuration.stepsContext()).thenReturn(new StepsContext());
        List<CandidateSteps> candidateSteps = Collections.emptyList();
        EmbedderMonitor embedderMonitor = mock(EmbedderMonitor.class);
        BatchFailures failures = mock(BatchFailures.class);
        StoryReporter storyReporter = mock(StoryReporter.class);
        when(configuration.storyReporter(STORY_PATH)).thenReturn(storyReporter);
        when(configuration.keywords()).thenReturn(new Keywords());
        PerformableTree performableTree = new PerformableTree();
        RunContext runContext = performableTree.newRunContext(configuration, candidateSteps,
                embedderMonitor, new MetaFilter(), failures);
        performableTree.addStories(runContext, Collections.singletonList(story));
        performableTree.perform(runContext, story);
        verify(storyReporter).failed(eq(WHEN_STEP), argThat(new UUIDExcWrapperMatcher(DependsOnNotFoundException.class)));
    }

    @Test
    public void shouldSkipBeforeAndAfterStepsWhenDependsOnIdIsEmpty() {
        Meta meta = Meta.createMeta(DEPENDS_ON_ID, new Keywords());
        Scenario scenario = new Scenario(BASE_SCENARIO_TITLE, meta, null, null, Collections.emptyList());
        Story story = new Story(STORY_PATH, null, null, null, null,
                Collections.singletonList(scenario));
        Configuration configuration = mock(Configuration.class);
        Step beforeStep = mock(Step.class);
        when(beforeStep.asString(any())).thenReturn(BEFORE_STEP);
        Step afterStep = mock(Step.class);
        when(afterStep.asString(any())).thenReturn(AFTER_STEP);
        StepCollector stepCollector = spy(MarkUnmatchedStepsAsPending.class);
        doReturn(Collections.singletonList(beforeStep)).when(stepCollector).collectBeforeOrAfterScenarioSteps(
                eq(Collections.emptyList()), any(Meta.class), eq(StepCollector.Stage.BEFORE), eq(ScenarioType.NORMAL));
        doReturn(Collections.singletonList(afterStep)).when(stepCollector).collectBeforeOrAfterScenarioSteps(
                eq(Collections.emptyList()), any(Meta.class), eq(StepCollector.Stage.AFTER), eq(ScenarioType.NORMAL));
        when(configuration.stepCollector()).thenReturn(stepCollector);
        when(configuration.storyControls()).thenReturn(new StoryControls());
        when(configuration.stepsContext()).thenReturn(new StepsContext());
        List<CandidateSteps> candidateSteps = Collections.emptyList();
        EmbedderMonitor embedderMonitor = mock(EmbedderMonitor.class);
        BatchFailures failures = mock(BatchFailures.class);
        StoryReporter storyReporter = mock(StoryReporter.class);
        when(configuration.storyReporter(STORY_PATH)).thenReturn(storyReporter);
        when(configuration.keywords()).thenReturn(new Keywords());
        PerformableTree performableTree = new PerformableTree();
        RunContext runContext = performableTree.newRunContext(configuration, candidateSteps,
                embedderMonitor, new MetaFilter(), failures);
        performableTree.addStories(runContext, Collections.singletonList(story));
        performableTree.perform(runContext, story);
        verify(storyReporter).failed(eq(BEFORE_STEP), argThat(new UUIDExcWrapperMatcher(DependsOnNotFoundException.class)));
        verify(storyReporter).failed(eq(AFTER_STEP), argThat(new UUIDExcWrapperMatcher(DependsOnNotFoundException.class)));
    }

    @Test
    public void shouldSkipExamplesWhenDependsOnIdIsEmpty() {
        ExamplesTable examplesTable = new ExamplesTable("|examples|\n|row_1|\n|row_2|");
        Scenario scenario = new Scenario(BASE_SCENARIO_TITLE, Meta.createMeta(DEPENDS_ON_ID,
                new Keywords()), null, examplesTable, Collections.singletonList(WHEN_STEP));
        Story story = new Story(STORY_PATH, null, null, null, null,
                Collections.singletonList(scenario));
        Configuration configuration = mock(Configuration.class);
        when(configuration.stepCollector()).thenReturn(new MarkUnmatchedStepsAsPending());
        when(configuration.storyControls()).thenReturn(new StoryControls());
        when(configuration.stepsContext()).thenReturn(new StepsContext());
        List<CandidateSteps> candidateSteps = Collections.emptyList();
        EmbedderMonitor embedderMonitor = mock(EmbedderMonitor.class);
        BatchFailures failures = mock(BatchFailures.class);
        StoryReporter storyReporter = mock(StoryReporter.class);
        when(configuration.storyReporter(STORY_PATH)).thenReturn(storyReporter);
        when(configuration.keywords()).thenReturn(new Keywords());
        PerformableTree performableTree = new PerformableTree();
        RunContext runContext = performableTree.newRunContext(configuration, candidateSteps,
                embedderMonitor, new MetaFilter(), failures);
        performableTree.addStories(runContext, Collections.singletonList(story));
        performableTree.perform(runContext, story);
        verify(storyReporter, times(examplesTable.getRowCount())).failed(eq(WHEN_STEP),
                argThat(new UUIDExcWrapperMatcher(DependsOnNotFoundException.class)));
    }

    @Test
    public void shouldSkipGivenWhenDependsOnIdIsEmpty() {
        GivenStories givenStories = mock(GivenStories.class);
        GivenStory givenStory = mock(GivenStory.class);
        when(givenStory.getPath()).thenReturn(STORY_PATH);
        when(givenStory.asString()).thenReturn(GIVEN_STORY);
        when(givenStories.asString()).thenReturn("given stories");
        when(givenStories.getPaths()).thenReturn(Collections.singletonList(STORY_PATH));
        when(givenStories.getStories()).thenReturn(Collections.singletonList(givenStory));
        Scenario scenario = new Scenario(BASE_SCENARIO_TITLE, Meta.createMeta(DEPENDS_ON_ID,
                new Keywords()), givenStories, null, Collections.singletonList(WHEN_STEP));
        Story story = new Story(STORY_PATH, null, null, null, null,
                Collections.singletonList(scenario));
        Scenario givenStoryScenario = new Scenario("given scenario title", Meta.EMPTY,
                null, null, Collections.singletonList(GIVEN_WHEN_STEP));
        Story givenStoryAsStory = new Story(STORY_PATH, null, null, null, null,
                Collections.singletonList(givenStoryScenario));
        Configuration configuration = mock(Configuration.class);
        when(configuration.stepCollector()).thenReturn(new MarkUnmatchedStepsAsPending());
        when(configuration.storyControls()).thenReturn(new StoryControls());
        when(configuration.stepsContext()).thenReturn(new StepsContext());
        when(configuration.pathCalculator()).thenReturn(new AbsolutePathCalculator());
        StoryLoader storyLoader = mock(StoryLoader.class);
        when(configuration.storyLoader()).thenReturn(storyLoader);
        when(storyLoader.loadStoryAsText(STORY_PATH)).thenReturn(GIVEN_STORY);
        StoryParser storyParser = mock(StoryParser.class);
        when(configuration.storyParser()).thenReturn(storyParser);
        when(storyParser.parseStory(GIVEN_STORY, STORY_PATH)).thenReturn(givenStoryAsStory);
        List<CandidateSteps> candidateSteps = Collections.emptyList();
        EmbedderMonitor embedderMonitor = mock(EmbedderMonitor.class);
        BatchFailures failures = mock(BatchFailures.class);
        StoryReporter storyReporter = mock(StoryReporter.class);
        when(configuration.storyReporter(STORY_PATH)).thenReturn(storyReporter);
        when(configuration.keywords()).thenReturn(new Keywords());
        PerformableTree performableTree = new PerformableTree();
        RunContext runContext = performableTree.newRunContext(configuration, candidateSteps,
                embedderMonitor, new MetaFilter(), failures);
        performableTree.addStories(runContext, Collections.singletonList(story));
        performableTree.perform(runContext, story);
        verify(storyReporter).failed(eq(GIVEN_WHEN_STEP), argThat(new UUIDExcWrapperMatcher(DependsOnNotFoundException.class)));
    }

    @Test
    public void shouldSkipScenarioWhenDependsOnIdIsFailed() {
        Scenario scenario1 = new Scenario(BASE_SCENARIO_TITLE, Meta.createMeta("@scenarioId id @dependsOnId id2",
                new Keywords()), null, null, Collections.emptyList());
        Scenario scenario2 = new Scenario(BASE_SCENARIO_TITLE, Meta.createMeta(DEPENDS_ON_ID,
                new Keywords()), null, null, Collections.singletonList(WHEN_STEP));
        List<Scenario> scenarios = new LinkedList<>();
        scenarios.add(scenario1);
        scenarios.add(scenario2);
        Story story = new Story(STORY_PATH, null, null, null, null, scenarios);
        Configuration configuration = mock(Configuration.class);
        when(configuration.stepCollector()).thenReturn(new MarkUnmatchedStepsAsPending());
        when(configuration.storyControls()).thenReturn(new StoryControls());
        when(configuration.stepsContext()).thenReturn(new StepsContext());
        List<CandidateSteps> candidateSteps = Collections.emptyList();
        EmbedderMonitor embedderMonitor = mock(EmbedderMonitor.class);
        BatchFailures failures = mock(BatchFailures.class);
        StoryReporter storyReporter = mock(StoryReporter.class);
        when(configuration.storyReporter(STORY_PATH)).thenReturn(storyReporter);
        when(configuration.keywords()).thenReturn(new Keywords());
        PerformableTree performableTree = new PerformableTree();
        RunContext runContext = performableTree.newRunContext(configuration, candidateSteps,
                embedderMonitor, new MetaFilter(), failures);
        performableTree.addStories(runContext, Collections.singletonList(story));
        performableTree.perform(runContext, story);
        verify(storyReporter).failed(eq(WHEN_STEP), argThat(new UUIDExcWrapperMatcher(DependsOnFailedException.class)));
    }

    @Test
    public void shouldPerformScenarioWhenDependsOnIdSuccessful() {

        Scenario scenario1 = new Scenario(BASE_SCENARIO_TITLE, Meta.createMeta("@scenarioId id",
                new Keywords()), null, null, Collections.emptyList());
        Scenario scenario2 = new Scenario(BASE_SCENARIO_TITLE, Meta.createMeta(DEPENDS_ON_ID,
                new Keywords()), null, null, Collections.emptyList());
        List<Scenario> scenarios = new LinkedList<>();
        scenarios.add(scenario1);
        scenarios.add(scenario2);
        Story story = new Story(STORY_PATH, null, null, null, null,
                scenarios);
        Configuration configuration = mock(Configuration.class);
        when(configuration.stepCollector()).thenReturn(new MarkUnmatchedStepsAsPending());
        when(configuration.storyControls()).thenReturn(new StoryControls());
        when(configuration.stepsContext()).thenReturn(new StepsContext());
        List<CandidateSteps> candidateSteps = Collections.emptyList();
        EmbedderMonitor embedderMonitor = mock(EmbedderMonitor.class);
        BatchFailures failures = mock(BatchFailures.class);
        StoryReporter storyReporter = mock(StoryReporter.class);
        when(configuration.storyReporter(STORY_PATH)).thenReturn(storyReporter);
        PerformableTree performableTree = new PerformableTree();
        RunContext runContext = performableTree.newRunContext(configuration, candidateSteps,
                embedderMonitor, new MetaFilter(), failures);
        performableTree.addStories(runContext, Collections.singletonList(story));
        performableTree.perform(runContext, story);
        verify(storyReporter, never()).failed(any(), any());
    }

    private RunContext performStoryRun(boolean skipScenariosAfterGivenStoriesFailure, String givenStoryAsString) {
        String givenStoryPath = "given/path";
        Scenario scenario = new Scenario(BASE_SCENARIO_TITLE, Meta.EMPTY);
        Story story = new Story(STORY_PATH, null, null, null, new GivenStories(givenStoryPath),
                Collections.singletonList(scenario));

        Configuration configuration = new MostUsefulConfiguration()
                .useStoryControls(new StoryControls().doSkipStoryIfGivenStoryFailed(skipScenariosAfterGivenStoriesFailure));
        StoryLoader storyLoader = mock(StoryLoader.class);
        configuration.useStoryLoader(storyLoader);
        when(storyLoader.loadStoryAsText(givenStoryPath)).thenReturn(givenStoryAsString);
        List<CandidateSteps> candidateSteps = Collections.emptyList();
        EmbedderMonitor embedderMonitor = mock(EmbedderMonitor.class);
        BatchFailures failures = mock(BatchFailures.class);

        PerformableTree performableTree = new PerformableTree();
        RunContext runContext = performableTree.newRunContext(configuration, candidateSteps,
                embedderMonitor, new MetaFilter(), failures);
        performableTree.addStories(runContext, Collections.singletonList(story));
        performableTree.perform(runContext, story);
        return runContext;
    }

    @Test
    public void performStoryIfDryRunTrue() {
        Scenario scenario = new Scenario("scenario title", Meta.EMPTY);
        Story story = new Story(STORY_PATH, null, null, null, null,
                Collections.singletonList(scenario));
        Configuration configuration = mock(Configuration.class);
        when(configuration.stepCollector()).thenReturn(new MarkUnmatchedStepsAsPending());
        when(configuration.storyControls()).thenReturn(new StoryControls());
        when(configuration.stepsContext()).thenReturn(new StepsContext());
        when(configuration.dryRun()).thenReturn(true);
        StoryLoader storyLoader = mock(StoryLoader.class);
        configuration.useStoryLoader(storyLoader);
        StoryReporter storyReporter = mock(StoryReporter.class);
        when(configuration.storyReporter(STORY_PATH)).thenReturn(storyReporter);
        List<CandidateSteps> candidateSteps = Collections.emptyList();
        EmbedderMonitor embedderMonitor = mock(EmbedderMonitor.class);
        BatchFailures failures = mock(BatchFailures.class);

        PerformableTree performableTree = new PerformableTree();
        RunContext runContext = performableTree.newRunContext(configuration, candidateSteps,
                embedderMonitor, new MetaFilter(), failures);
        performableTree.addStories(runContext, Collections.singletonList(story));
        performableTree.perform(runContext, story);

        verify(storyReporter).dryRun();
    }

    @Test
    public void shouldNotShareStoryStateBetweenThreads() throws Throwable {
        RunContext context = runStoryInContext(EMPTY_STORY);
        assertThat(context.state().getClass().getSimpleName(), is("FineSoFar"));
        assertReturnsNullInAnotherThread(context::state);
    }

    @Test
    public void shouldNotShareStoryPathBetweenThreads() throws Throwable {
        RunContext context = runStoryInContext(EMPTY_STORY);
        assertThat(context.path(), is(STORY_PATH));
        assertReturnsNullInAnotherThread(context::path);
    }

    @Test
    public void shouldNotShareStoryReporterBetweenThreads() throws Throwable {
        RunContext context = runStoryInContext(EMPTY_STORY);
        assertThat(context.reporter(), instanceOf(StoryReporter.class));
        assertReturnsNullInAnotherThread(context::reporter);
    }

    private RunContext runStoryInContext(Story story) {
        Configuration configuration = new MostUsefulConfiguration();
        configuration.useStoryLoader(mock(StoryLoader.class));
        PerformableTree performableTree = new PerformableTree();
        RunContext runContext = performableTree.newRunContext(configuration, Collections.emptyList(),
                mock(EmbedderMonitor.class), new MetaFilter(), mock(BatchFailures.class));
        performableTree.addStories(runContext, Arrays.asList(story));
        performableTree.perform(runContext, story);
        return runContext;
    }

    private void assertReturnsNullInAnotherThread(Supplier<Object> supplier) throws Throwable {
        Callable<Void> task = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                assertNull(supplier.get());
                return null;
            }
        };

        try {
            EXECUTOR.submit(task).get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    private static final class UUIDExcWrapperMatcher extends ArgumentMatcher<Throwable> {
        Class throwableClass;

        UUIDExcWrapperMatcher(Class throwableClass) {
            this.throwableClass = throwableClass;
        }
        
        @Override
        public boolean matches(Object argument) {
            return argument instanceof UUIDExceptionWrapper &&
                    ((UUIDExceptionWrapper) argument).getCause().getClass() == throwableClass;
        }
    }
}
