package org.jbehave.core.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.equalTo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Arrays;
import java.util.List;

public class StoryBehaviour {

    @Test
    public void shouldCloneWithScenarios() {
        Scenario scenario = mock(Scenario.class);
        Meta meta = mock(Meta.class);
        Description description = mock(Description.class);
        Narrative narrative = mock(Narrative.class);
        GivenStories givenStories = mock(GivenStories.class);
        Lifecycle lifecycle = mock(Lifecycle.class);

        Story story = new Story("path", description, meta, narrative, givenStories,
                lifecycle, Arrays.asList(scenario));
        story.namedAs("name");

        Scenario cloneScenario = mock(Scenario.class);
        Story clone = story.cloneWithScenarios(Arrays.asList(cloneScenario));
        assertCloneStory(clone, meta, description, narrative, givenStories, lifecycle, Arrays.asList(cloneScenario));
        verifyZeroInteractions(scenario, meta, description, narrative, givenStories, lifecycle, cloneScenario);
    }

    @Test
    public void cloneWithMetaAndScenarios() {
        Scenario scenario = mock(Scenario.class);
        Meta meta = mock(Meta.class);
        Description description = mock(Description.class);
        Narrative narrative = mock(Narrative.class);
        GivenStories givenStories = mock(GivenStories.class);
        Lifecycle lifecycle = mock(Lifecycle.class);

        Story story = new Story("path", description, meta, narrative, givenStories,
                lifecycle, Arrays.asList(scenario));
        story.namedAs("name");

        Meta cloneMeta = mock(Meta.class);
        Scenario cloneScenario = mock(Scenario.class);
        Story clone = story.cloneWithMetaAndScenarios(cloneMeta, Arrays.asList(cloneScenario));
        assertCloneStory(clone, cloneMeta, description, narrative, givenStories, lifecycle,
                Arrays.asList(cloneScenario));
        verifyZeroInteractions(scenario, meta, description, narrative, givenStories, lifecycle, cloneScenario,
                cloneMeta);
    }

    private void assertCloneStory(Story clone, Meta meta, Description description, Narrative narrative,
            GivenStories givenStories, Lifecycle lifecycle, List<Scenario> scenario) {
        assertThat(clone.getMeta(), equalTo(meta));
        assertThat(clone.getDescription(), equalTo(description));
        assertThat(clone.getNarrative(), equalTo(narrative));
        assertThat(clone.getGivenStories(), equalTo(givenStories));
        assertThat(clone.getLifecycle(), equalTo(lifecycle));
        assertThat(clone.getScenarios(), equalTo(scenario));
        assertThat(clone.getPath(), equalTo("path"));
        assertThat(clone.getName(), equalTo("name"));
    }
}
