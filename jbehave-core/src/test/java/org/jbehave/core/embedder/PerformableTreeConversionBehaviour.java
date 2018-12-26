package org.jbehave.core.embedder;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbehave.core.annotations.Scope;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.Keywords;
import org.jbehave.core.failures.BatchFailures;
import org.jbehave.core.model.ExamplesTable;
import org.jbehave.core.model.GivenStories;
import org.jbehave.core.model.Lifecycle;
import org.jbehave.core.model.Meta;
import org.jbehave.core.model.Narrative;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.steps.CandidateSteps;
import org.jbehave.core.steps.ParameterControls;
import org.jbehave.core.steps.ParameterConverters;
import org.jbehave.core.steps.Step;
import org.jbehave.core.steps.StepCollector;
import org.jbehave.core.steps.StepCollector.Stage;
import org.jbehave.core.steps.StepMonitor;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

/**
 * @author Aliaksandr_Tsymbal.
 */
public class PerformableTreeConversionBehaviour {

    @Test
    public void shouldConvertParameters(){
        SharpParameterConverters sharpParameterConverters = new SharpParameterConverters();
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
        when(configuration.parameterConverters()).thenReturn(sharpParameterConverters);
        when(configuration.parameterControls()).thenReturn(parameterControls);

        GivenStories givenStories = new GivenStories("");

        Map<String,String> scenarioExample = new HashMap<>();
        scenarioExample.put("var1","#E");
        scenarioExample.put("var3","<var2>#F");
        Map<String,String> scenarioExampleSecond = new HashMap<>();
        scenarioExampleSecond.put("var1","#G<var2>");
        scenarioExampleSecond.put("var3","#H");
        ExamplesTable scenarioExamplesTable = new ExamplesTable("").withRows(
                Arrays.asList(scenarioExample, scenarioExampleSecond));

        Scenario scenario = new Scenario("scenario title", new Meta(), givenStories, scenarioExamplesTable, null);

        Narrative narrative = mock(Narrative.class);
        Lifecycle lifecycle = mock(Lifecycle.class);
        Story story = new Story(null, null, new Meta(), narrative, givenStories, lifecycle,
                Collections.singletonList(scenario));

        ExamplesTable storyExamplesTable = mock(ExamplesTable.class);
        when(lifecycle.getExamplesTable()).thenReturn(storyExamplesTable);
        Map<String,String> storyExampleFirstRow = new HashMap<>();
        storyExampleFirstRow.put("var1","#A");
        storyExampleFirstRow.put("var2","#B");
        Map<String,String> storyExampleSecondRow = new HashMap<>();
        storyExampleSecondRow.put("var1","#C");
        storyExampleSecondRow.put("var2","#D");

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
        List<PerformableTree.ExamplePerformableScenario> examplePerformableScenarios = performableScenarios.get(0)
                .getExamples();
        assertEquals(scenarioExample.size(), examplePerformableScenarios.size());
        assertEquals("eE", examplePerformableScenarios.get(0).getParameters().get("var1"));
        assertEquals("bB", examplePerformableScenarios.get(0).getParameters().get("var2"));
        assertEquals("bBb#fF", examplePerformableScenarios.get(0).getParameters().get("var3"));

        assertEquals("gbbGbB", examplePerformableScenarios.get(1).getParameters().get("var1"));
        assertEquals("bB", examplePerformableScenarios.get(1).getParameters().get("var2"));
        assertEquals("hH", examplePerformableScenarios.get(1).getParameters().get("var3"));

        examplePerformableScenarios = performableScenarios.get(1).getExamples();
        assertEquals(scenarioExample.size(), examplePerformableScenarios.size());
        assertEquals("eE", examplePerformableScenarios.get(0).getParameters().get("var1"));
        assertEquals("dD", examplePerformableScenarios.get(0).getParameters().get("var2"));
        assertEquals("dDd#fF", examplePerformableScenarios.get(0).getParameters().get("var3"));

        assertEquals("gddGdD", examplePerformableScenarios.get(1).getParameters().get("var1"));
        assertEquals("dD", examplePerformableScenarios.get(1).getParameters().get("var2"));
        assertEquals("hH", examplePerformableScenarios.get(1).getParameters().get("var3"));
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

    private class SharpParameterConverters extends ParameterConverters{

        public Object convert(String value, Type type, Story story) {
            if(type == String.class){

                return value.replace("#", value.substring(1).toLowerCase());
            }
            return null;
        }
    }
}
