package org.jbehave.core.embedder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.jbehave.core.steps.StepCollector.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMapOf;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbehave.core.annotations.ScenarioType;
import org.jbehave.core.annotations.Scope;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.Keywords;
import org.jbehave.core.configuration.MostUsefulConfiguration;
import org.jbehave.core.embedder.PerformableTree.RunContext;
import org.jbehave.core.failures.BatchFailures;
import org.jbehave.core.io.StoryLoader;
import org.jbehave.core.model.ExamplesTable;
import org.jbehave.core.model.GivenStories;
import org.jbehave.core.model.Lifecycle;
import org.jbehave.core.model.Meta;
import org.jbehave.core.model.Narrative;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.reporters.StoryReporter;
import org.jbehave.core.steps.CandidateSteps;
import org.jbehave.core.steps.MarkUnmatchedStepsAsPending;
import org.jbehave.core.steps.ParameterControls;
import org.jbehave.core.steps.ParameterConverters;
import org.jbehave.core.steps.Step;
import org.jbehave.core.steps.StepCollector;
import org.jbehave.core.steps.StepMonitor;
import org.jbehave.core.steps.context.StepsContext;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mockito;

/**
 * @author Valery Yatsynovich
 */
public class PerformableTreeBehaviour {

    private static final String GIVEN_SCENARIO_FAIL = "Scenario: given scenario title\nWhen I fail";
    private static final String STORY_PATH = "path";

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
        ordered.verify(stepCollector).collectBeforeOrAfterStoriesSteps(candidateSteps, Stage.BEFORE);
        ordered.verify(stepCollector).collectLifecycleSteps(eq(candidateSteps), eq(story.getLifecycle()),
                any(Meta.class), eq(Scope.STORY));
        ordered.verify(stepCollector).collectBeforeOrAfterStorySteps(candidateSteps, story, Stage.BEFORE,
                false);
        ordered.verify(stepCollector).collectBeforeOrAfterStorySteps(candidateSteps, story, Stage.AFTER,
                false);
        ordered.verify(stepCollector).collectBeforeOrAfterStoriesSteps(candidateSteps, Stage.AFTER);
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
    public void shouldReplaceParameters() {
        ParameterControls parameterControls = new ParameterControls();
        PerformableTree performableTree = new PerformableTree();
        Configuration configuration = mock(Configuration.class);
        when(configuration.storyControls()).thenReturn(new StoryControls());
        List<CandidateSteps> candidateSteps = Collections.emptyList();
        EmbedderMonitor embedderMonitor = mock(EmbedderMonitor.class);
        BatchFailures failures = mock(BatchFailures.class);
        PerformableTree.RunContext context = spy(performableTree.newRunContext(configuration, candidateSteps,
                embedderMonitor, new MetaFilter(), failures));

        StoryControls storyControls = mock(StoryControls.class);
        when(configuration.storyControls()).thenReturn(storyControls);
        when(storyControls.skipBeforeAndAfterScenarioStepsIfGivenStory()).thenReturn(false);
        when(configuration.parameterConverters()).thenReturn(new DefaultParameterConverters());
        when(configuration.parameterControls()).thenReturn(parameterControls);

        GivenStories givenStories = new GivenStories("");

        Map<String,String> scenarioExample = new HashMap<>();
        scenarioExample.put("var1","E");
        scenarioExample.put("var3","<var2>F");

        Map<String,String> scenarioExampleSecond = new HashMap<>();
        scenarioExampleSecond.put("var1","G<var2>");
        scenarioExampleSecond.put("var3","<var2>");
        ExamplesTable scenarioExamplesTable = new ExamplesTable("").withRows(
                Arrays.asList(scenarioExample, scenarioExampleSecond));

        String scenarioTitle = "scenario title";
        Scenario scenario = new Scenario(scenarioTitle, new Meta(), givenStories, scenarioExamplesTable, Collections.<String>emptyList());

        Narrative narrative = mock(Narrative.class);
        Lifecycle lifecycle = mock(Lifecycle.class);
        Story story = new Story(null, null, new Meta(), narrative, givenStories, lifecycle,
                Collections.singletonList(scenario));

        ExamplesTable storyExamplesTable = mock(ExamplesTable.class);
        when(lifecycle.getExamplesTable()).thenReturn(storyExamplesTable);
        Map<String,String> storyExampleFirstRow = new HashMap<>();
        storyExampleFirstRow.put("var1","a");
        storyExampleFirstRow.put("var2","b");
        Map<String,String> storyExampleSecondRow = new HashMap<>();
        storyExampleSecondRow.put("var1","c");
        storyExampleSecondRow.put("var2","d");

        when(storyExamplesTable.getRows()).thenReturn(Arrays.asList(storyExampleFirstRow, storyExampleSecondRow));

        Keywords keywords = mock(Keywords.class);
        when(configuration.keywords()).thenReturn(keywords);

        StepMonitor stepMonitor = mock(StepMonitor.class);
        when(configuration.stepMonitor()).thenReturn(stepMonitor);
        StepCollector stepCollector = mock(StepCollector.class);
        when(configuration.stepCollector()).thenReturn(stepCollector);
        Map<Stage, List<Step>> lifecycleSteps = new EnumMap<>(Stage.class);
        lifecycleSteps.put(Stage.BEFORE, Collections.<Step>emptyList());
        lifecycleSteps.put(Stage.AFTER, Collections.<Step>emptyList());
        when(stepCollector.collectLifecycleSteps(eq(candidateSteps), eq(lifecycle), isEmptyMeta(), eq(Scope.STORY)))
                .thenReturn(lifecycleSteps);
        when(stepCollector.collectLifecycleSteps(eq(candidateSteps), eq(lifecycle), isEmptyMeta(), eq(Scope.SCENARIO)))
                .thenReturn(lifecycleSteps);

        performableTree.addStories(context, Collections.singletonList(story));
        List<PerformableTree.PerformableScenario> performableScenarios = performableTree.getRoot().getStories().get(0)
                .getScenarios();

        assertEquals(scenarioExample.size(), performableScenarios.size());
        assertEquals(scenarioTitle + " [1]", performableScenarios.get(0).getScenario().getTitle());
        List<PerformableTree.ExamplePerformableScenario> examplePerformableScenarios = performableScenarios.get(0)
                .getExamples();
        assertEquals(scenarioExample.size(), examplePerformableScenarios.size());
        assertEquals("E", examplePerformableScenarios.get(0).getParameters().get("var1"));
        assertEquals("b", examplePerformableScenarios.get(0).getParameters().get("var2"));
        assertEquals("bF", examplePerformableScenarios.get(0).getParameters().get("var3"));

        assertEquals("Gb", examplePerformableScenarios.get(1).getParameters().get("var1"));
        assertEquals("b", examplePerformableScenarios.get(1).getParameters().get("var2"));
        assertEquals("b", examplePerformableScenarios.get(1).getParameters().get("var3"));

        assertEquals(scenarioTitle + " [2]", performableScenarios.get(1).getScenario().getTitle());
        examplePerformableScenarios = performableScenarios.get(1).getExamples();
        assertEquals(scenarioExample.size(), examplePerformableScenarios.size());
        assertEquals("E", examplePerformableScenarios.get(0).getParameters().get("var1"));
        assertEquals("d", examplePerformableScenarios.get(0).getParameters().get("var2"));
        assertEquals("dF", examplePerformableScenarios.get(0).getParameters().get("var3"));

        assertEquals("Gd", examplePerformableScenarios.get(1).getParameters().get("var1"));
        assertEquals("d", examplePerformableScenarios.get(1).getParameters().get("var2"));
        assertEquals("d", examplePerformableScenarios.get(1).getParameters().get("var3"));
    }

    private RunContext performStoryRun(boolean skipScenariosAfterGivenStoriesFailure, String givenStoryAsString) {
        String givenStoryPath = "given/path";
        Scenario scenario = new Scenario("base scenario title", Meta.EMPTY);
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

    private Meta isEmptyMeta() {
        return argThat(new ArgumentMatcher<Meta>() {
            @Override
            public boolean matches(Object argument) {
                Meta meta = (Meta) argument;
                return meta.getPropertyNames().isEmpty();
            }
        });
    }

    private class DefaultParameterConverters extends ParameterConverters {
        public Object convert(String value, Type type) {
            return value;
        }
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
}
