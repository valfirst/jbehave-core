package org.jbehave.core.reporters;

import org.jbehave.core.model.ExamplesTable;
import org.jbehave.core.model.GivenStories;
import org.jbehave.core.model.Meta;
import org.jbehave.core.model.OutcomesTable;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.failures.UUIDExceptionWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class SilentSuccessFilterBehaviour {

    @Mock
    private StoryReporter delegate;

    @InjectMocks
    private SilentSuccessFilter filter;

    @Test
    public void shouldPassSilentlyOutputFromSuccessfulScenariosWithDeprecatedBeforeScenario() {
        // Given
        ExamplesTable examplesTable = new ExamplesTable("|one|two|\n|1|2|\n");
        UUIDExceptionWrapper anException = new UUIDExceptionWrapper(new IllegalArgumentException());
        Story story = new Story();
        GivenStories givenStories = new GivenStories("path/to/story1,path/to/story2");
        List<String> givenStoryPaths = asList("path/to/story1","path/to/story2");

        // When
        filter.dryRun();
        filter.beforeStory(story, false);
        filter.beforeScenario("My scenario 1");
        filter.scenarioMeta(Meta.EMPTY);
        filter.successful("Given step 1.1");
        filter.ignorable("!-- Then ignore me");
        filter.comment("!-- A comment");
        filter.successful("When step 1.2");
        filter.successful("Then step 1.3");
        filter.afterScenario();

        filter.beforeScenario("My scenario 2");
        filter.givenStories(givenStories);
        filter.givenStories(givenStoryPaths);        
        filter.successful("Given step 2.1");
        filter.pending("When step 2.2");
        filter.notPerformed("Then step 2.3");
        filter.afterScenario();

        filter.beforeScenario("My scenario 3");
        filter.beforeExamples(asList("Given step <one>", "Then step <two>"), examplesTable);
        Map<String, String> tableRow = new HashMap<>();
        filter.example(tableRow, 0);
        filter.successful("Given step 3.1");
        filter.successful("When step 3.2");
        filter.ignorable("!-- Then ignore me too");
        filter.comment("!-- One more comment");
        filter.failed("Then step 3.3", anException);
        OutcomesTable outcomesTable = new OutcomesTable();
        filter.failedOutcomes("When failed outcomes", outcomesTable);
        filter.afterExamples();
        filter.afterScenario();

        filter.beforeScenario("My scenario 4");
        filter.successful("Given step 4.1");
        filter.successful("When step 4.2");
        filter.successful("Then step 4.3");
        filter.afterScenario();
        filter.afterStory(false);

        // Then
        InOrder inOrder = inOrder(delegate);

        // Scenarios 1 and 4 are successful
        verify(delegate, never()).beforeScenario("My scenario 1");
        verify(delegate, never()).successful("Given step 1.1");
        verify(delegate, never()).ignorable("!-- Then ignore me");
        verify(delegate, never()).comment("!-- A comment");
        verify(delegate, never()).successful("When step 1.2");
        verify(delegate, never()).successful("Then step 1.3");

        verify(delegate, never()).beforeScenario("My scenario 4");
        verify(delegate, never()).successful("Given step 4.1");
        verify(delegate, never()).successful("When step 4.2");
        verify(delegate, never()).successful("Then step 4.3");

        // Scenarios 2 and 3 have pending or failed steps
        inOrder.verify(delegate).dryRun();
        inOrder.verify(delegate).beforeStory(story, false);
        inOrder.verify(delegate).beforeScenario("My scenario 2");
        inOrder.verify(delegate).givenStories(givenStories);
        inOrder.verify(delegate).givenStories(givenStoryPaths);
        inOrder.verify(delegate).successful("Given step 2.1");
        inOrder.verify(delegate).pending("When step 2.2");
        inOrder.verify(delegate).notPerformed("Then step 2.3");
        inOrder.verify(delegate).afterScenario();

        inOrder.verify(delegate).beforeScenario("My scenario 3");
        inOrder.verify(delegate).beforeExamples(asList("Given step <one>", "Then step <two>"), examplesTable);
        inOrder.verify(delegate).example(tableRow, 0);
        inOrder.verify(delegate).successful("Given step 3.1");
        inOrder.verify(delegate).successful("When step 3.2");
        inOrder.verify(delegate).ignorable("!-- Then ignore me too");
        inOrder.verify(delegate).comment("!-- One more comment");
        inOrder.verify(delegate).failed("Then step 3.3", anException);
        inOrder.verify(delegate).failedOutcomes("When failed outcomes", outcomesTable);
        inOrder.verify(delegate).afterExamples();

        inOrder.verify(delegate).afterScenario();
        inOrder.verify(delegate).afterStory(false);
    }

    @Test
    public void shouldPassSilentlyOutputFromSuccessfulScenarios() {
        // Given
        ExamplesTable examplesTable = new ExamplesTable("|one|two|\n|1|2|\n");
        UUIDExceptionWrapper anException = new UUIDExceptionWrapper(new IllegalArgumentException());
        Story story = new Story();
        GivenStories givenStories = new GivenStories("path/to/story1,path/to/story2");
        List<String> givenStoryPaths = asList("path/to/story1","path/to/story2");

        // When
        Scenario scenario1 = new Scenario("My scenario 1", Meta.EMPTY);
        filter.dryRun();
        filter.beforeStory(story, false);
        filter.beforeScenario(scenario1);
        filter.scenarioMeta(Meta.EMPTY);
        filter.successful("Given step 1.1");
        filter.ignorable("!-- Then ignore me");
        filter.comment("!-- A comment");
        filter.successful("When step 1.2");
        filter.successful("Then step 1.3");
        filter.afterScenario();

        Scenario scenario2 = new Scenario("My scenario 2", Meta.EMPTY);
        filter.beforeScenario(scenario2);
        filter.givenStories(givenStories);
        filter.givenStories(givenStoryPaths);
        filter.successful("Given step 2.1");
        filter.pending("When step 2.2");
        filter.notPerformed("Then step 2.3");
        filter.afterScenario();

        Scenario scenario3 = new Scenario("My scenario 3", Meta.EMPTY);
        filter.beforeScenario(scenario3);
        filter.beforeExamples(asList("Given step <one>", "Then step <two>"), examplesTable);
        Map<String, String> tableRow = new HashMap<>();
        filter.example(tableRow, 0);
        filter.successful("Given step 3.1");
        filter.successful("When step 3.2");
        filter.ignorable("!-- Then ignore me too");
        filter.comment("!-- One more comment");
        filter.failed("Then step 3.3", anException);
        OutcomesTable outcomesTable = new OutcomesTable();
        filter.failedOutcomes("When failed outcomes", outcomesTable);
        filter.afterExamples();
        filter.afterScenario();

        Scenario scenario4 = new Scenario("My scenario 4", Meta.EMPTY);
        filter.beforeScenario(scenario4);
        filter.successful("Given step 4.1");
        filter.successful("When step 4.2");
        filter.successful("Then step 4.3");
        filter.afterScenario();
        filter.afterStory(false);

        // Then
        InOrder inOrder = inOrder(delegate);

        // Scenarios 1 and 4 are successful
        verify(delegate, never()).beforeScenario(scenario1);
        verify(delegate, never()).successful("Given step 1.1");
        verify(delegate, never()).ignorable("!-- Then ignore me");
        verify(delegate, never()).comment("!-- A comment");
        verify(delegate, never()).successful("When step 1.2");
        verify(delegate, never()).successful("Then step 1.3");

        verify(delegate, never()).beforeScenario(scenario4);
        verify(delegate, never()).successful("Given step 4.1");
        verify(delegate, never()).successful("When step 4.2");
        verify(delegate, never()).successful("Then step 4.3");

        // Scenarios 2 and 3 have pending or failed steps
        inOrder.verify(delegate).dryRun();
        inOrder.verify(delegate).beforeStory(story, false);
        inOrder.verify(delegate).beforeScenario(scenario2);
        inOrder.verify(delegate).givenStories(givenStories);
        inOrder.verify(delegate).givenStories(givenStoryPaths);
        inOrder.verify(delegate).successful("Given step 2.1");
        inOrder.verify(delegate).pending("When step 2.2");
        inOrder.verify(delegate).notPerformed("Then step 2.3");
        inOrder.verify(delegate).afterScenario();

        inOrder.verify(delegate).beforeScenario(scenario3);
        inOrder.verify(delegate).beforeExamples(asList("Given step <one>", "Then step <two>"), examplesTable);
        inOrder.verify(delegate).example(tableRow, 0);
        inOrder.verify(delegate).successful("Given step 3.1");
        inOrder.verify(delegate).successful("When step 3.2");
        inOrder.verify(delegate).ignorable("!-- Then ignore me too");
        inOrder.verify(delegate).comment("!-- One more comment");
        inOrder.verify(delegate).failed("Then step 3.3", anException);
        inOrder.verify(delegate).failedOutcomes("When failed outcomes", outcomesTable);
        inOrder.verify(delegate).afterExamples();

        inOrder.verify(delegate).afterScenario();
        inOrder.verify(delegate).afterStory(false);
    }

    @Test
    public void shouldNotPassSilentlyOutputNotAllowedByMetaFilter() {
        // Given
        Story story = new Story();
        Scenario scenario = new Scenario();

        String metaFilter = "";
        // When
        filter.storyNotAllowed(story, metaFilter);
        filter.scenarioNotAllowed(scenario, metaFilter);

        // Then
        InOrder inOrder = inOrder(delegate);

        inOrder.verify(delegate).storyNotAllowed(story, metaFilter);
        inOrder.verify(delegate).scenarioNotAllowed(scenario, metaFilter);
    }
}
