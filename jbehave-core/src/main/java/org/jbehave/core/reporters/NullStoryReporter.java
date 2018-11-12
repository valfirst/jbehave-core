package org.jbehave.core.reporters;

import java.util.List;
import java.util.Map;

import org.jbehave.core.model.ExamplesTable;
import org.jbehave.core.model.GivenStories;
import org.jbehave.core.model.Lifecycle;
import org.jbehave.core.model.Meta;
import org.jbehave.core.model.Narrative;
import org.jbehave.core.model.OutcomesTable;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.model.StoryDuration;

/**
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Null_Object_pattern">Null-object</a> implementation of
 * {@link StoryReporter}. Users can subclass it and can override only the method that they
 * are interested in.
 * </p>
 */
public class NullStoryReporter implements StoryReporter {

    @Override
    public void beforeStep(String step) {
    }

    @Override
    public void successful(String step) {
    }

    @Override
    public void ignorable(String step) {
    }

    @Override
    public void comment(String step) {
    }

    @Override
    public void pending(String step) {
    }

    @Override
    public void notPerformed(String step) {
    }

    @Override
    public void failed(String step, Throwable cause) {
    }

    @Override
    public void failedOutcomes(String step, OutcomesTable table) {
    }

    @Override
    public void storyNotAllowed(Story story, String filter) {
    }

    @Override
    public void beforeStory(Story story, boolean givenStory) {
    }

    @Override
    public void storyCancelled(Story story, StoryDuration storyDuration) {
    }

    @Override
    public void afterStory(boolean givenStory) {
    }

    @Override
    public void narrative(final Narrative narrative) {
    }

    @Override
    public void lifecyle(Lifecycle lifecycle) {
    }

    @Override
    public void beforeBeforeStorySteps() {
    }

    @Override
    public void afterBeforeStorySteps() {
    }

    @Override
    public void beforeAfterStorySteps() {
    }

    @Override
    public void afterAfterStorySteps() {
    }

    @Override
    public void beforeGivenStories(){
    }

    @Override
    public void givenStories(GivenStories givenStories) {
    }

    @Override
    public void givenStories(List<String> storyPaths) {
    }

    @Override
    public void afterGivenStories(){
    }

    @Override
    public void beforeScenario(String title) {
    }

    @Override
    public void beforeScenario(Scenario scenario) {
    }

    @Override
    public void scenarioNotAllowed(Scenario scenario, String filter) {
    }

    @Override
    public void scenarioMeta(Meta meta) {
    }

    @Override
    public void afterScenario() {
    }

    @Override
    public void beforeExamples(List<String> steps, ExamplesTable table) {
    }

    @Override
    public void example(Map<String, String> tableRow) {
    }

    @Override
    public void afterExamples() {
    }

    @Override
    public void dryRun() {
    }

    @Override
    public void pendingMethods(List<String> methods) {
    }

    @Override
    public void restarted(String step, Throwable cause) {
    }

    @Override
    public void restartedStory(Story story, Throwable cause) {
    }
}
