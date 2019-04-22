package org.jbehave.core.embedder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMapOf;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
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
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.steps.CandidateSteps;
import org.jbehave.core.steps.ParameterControls;
import org.jbehave.core.steps.ParameterConverters;
import org.jbehave.core.steps.StepCollector;
import org.junit.Test;
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
        ordered.verify(stepCollector).collectBeforeOrAfterStoriesSteps(candidateSteps, StepCollector.Stage.BEFORE);
        ordered.verify(stepCollector).collectBeforeOrAfterStorySteps(candidateSteps, story, StepCollector.Stage.BEFORE,
                false);
        ordered.verify(stepCollector).collectLifecycleSteps(eq(candidateSteps), eq(story.getLifecycle()),
                any(Meta.class), eq(StepCollector.Stage.BEFORE), eq(Scope.STORY));
        ordered.verify(stepCollector).collectLifecycleSteps(eq(candidateSteps), eq(story.getLifecycle()),
                any(Meta.class), eq(StepCollector.Stage.AFTER), eq(Scope.STORY));
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
    public void shouldReplaceParameters() {
        PerformableTree performableTree = new PerformableTree();
        PerformableTree.RunContext context = mock(PerformableTree.RunContext.class);
        Configuration configuration = mock(Configuration.class);
        Story story = mock(Story.class);
        when(context.configuration()).thenReturn(configuration);
        FilteredStory filteredStory = mock(FilteredStory.class);
        when(context.filter(story)).thenReturn(filteredStory);
        when(filteredStory.allowed()).thenReturn(true);

        Lifecycle lifecycle = mock(Lifecycle.class);
        when(story.getLifecycle()).thenReturn(lifecycle);
        ExamplesTable storyExamplesTable = mock(ExamplesTable.class);
        when(lifecycle.getExamplesTable()).thenReturn(storyExamplesTable);

        HashMap<String,String> storyExampleFirstRow = new HashMap<String, String>();
        storyExampleFirstRow.put("var1","a");
        storyExampleFirstRow.put("var2","b");
        HashMap<String,String> storyExampleSecondRow = new HashMap<String, String>();
        storyExampleSecondRow.put("var1","c");
        storyExampleSecondRow.put("var2","d");

        when(storyExamplesTable.getRows())
                .thenReturn(Arrays.<Map<String, String>> asList(storyExampleFirstRow, storyExampleSecondRow));
        StoryControls storyControls = mock(StoryControls.class);
        when(configuration.storyControls()).thenReturn(storyControls);
        when(storyControls.skipBeforeAndAfterScenarioStepsIfGivenStory()).thenReturn(false);
        when(configuration.parameterConverters()).thenReturn(new DefaultParameterConverters());
        ParameterControls parameterControls = new ParameterControls();
        when(configuration.parameterControls()).thenReturn(parameterControls);

        Scenario scenario = mock(Scenario.class);
        when(story.getScenarios()).thenReturn(Collections.singletonList(scenario));
        when(context.failureOccurred()).thenReturn(false);
        when(filteredStory.allowed(scenario)).thenReturn(true);
        Meta meta = mock(Meta.class);
        when(scenario.getMeta()).thenReturn(meta);
        when(story.getMeta()).thenReturn(meta);
        when(meta.inheritFrom(meta)).thenReturn(meta);

        HashMap<String,String> scenarioExample = new HashMap<String, String>();
        scenarioExample.put("var1","E");
        scenarioExample.put("var3","<var2>F");
        HashMap<String,String> scenarioExampleSecond = new HashMap<String, String>();
        scenarioExampleSecond.put("var1","G<var2>");
        scenarioExampleSecond.put("var3","<var2>");

        ExamplesTable scenarioExamplesTable = mock(ExamplesTable.class);
        when(scenario.getExamplesTable()).thenReturn(scenarioExamplesTable);
        when(scenarioExamplesTable.isEmpty()).thenReturn(false);
        when(scenarioExamplesTable.getRows()).
                thenReturn(Arrays.<Map<String, String>> asList(scenarioExample, scenarioExampleSecond));
        GivenStories givenStories = mock(GivenStories.class);
        when(scenario.getGivenStories()).thenReturn(givenStories);
        when(givenStories.requireParameters()).thenReturn(false);
        Keywords keywords = mock(Keywords.class);
        when(configuration.keywords()).thenReturn(keywords);
        MetaFilter metaFilter = mock(MetaFilter.class);
        when(context.filter()).thenReturn(metaFilter);
        when(metaFilter.allow(Mockito.<Meta>anyObject())).thenReturn(true);

        when(context.beforeOrAfterScenarioSteps(meta, StepCollector.Stage.BEFORE, ScenarioType.EXAMPLE))
                .thenReturn(new PerformableTree.PerformableSteps());
        when(context.beforeOrAfterScenarioSteps(meta, StepCollector.Stage.AFTER, ScenarioType.EXAMPLE))
                .thenReturn(new PerformableTree.PerformableSteps());
        when(context.beforeOrAfterScenarioSteps(meta, StepCollector.Stage.BEFORE, ScenarioType.ANY))
                .thenReturn(new PerformableTree.PerformableSteps());
        when(context.beforeOrAfterScenarioSteps(meta, StepCollector.Stage.AFTER, ScenarioType.ANY))
                .thenReturn(new PerformableTree.PerformableSteps());
        when(context.lifecycleSteps(lifecycle, meta, StepCollector.Stage.BEFORE))
                .thenReturn(new PerformableTree.PerformableSteps());
        when(context.lifecycleSteps(lifecycle, meta, StepCollector.Stage.AFTER))
                .thenReturn(new PerformableTree.PerformableSteps());

        when(context.scenarioSteps(any(Scenario.class), anyMapOf(String.class, String.class)))
                .thenReturn(new PerformableTree.PerformableSteps());
        when(scenario.getGivenStories()).thenReturn(givenStories);
        when(givenStories.getPaths()).thenReturn(Collections.<String>emptyList());
        when(story.getGivenStories()).thenReturn(givenStories);
        String scenarioTitle = "scenarioTitle";
        when(scenario.getTitle()).thenReturn(scenarioTitle);
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

    private class DefaultParameterConverters extends ParameterConverters {
        public Object convert(String value, Type type) {
            return value;
        }
    }
}
