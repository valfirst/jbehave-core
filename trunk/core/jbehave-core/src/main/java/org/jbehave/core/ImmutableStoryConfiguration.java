package org.jbehave.core;

import org.jbehave.core.errors.ErrorStrategy;
import org.jbehave.core.errors.PendingErrorStrategy;
import org.jbehave.core.model.KeyWords;
import org.jbehave.core.parser.StoryDefiner;
import org.jbehave.core.reporters.StepdocReporter;
import org.jbehave.core.reporters.StoryReporter;
import org.jbehave.core.steps.StepCreator;
import org.jbehave.core.steps.StepdocGenerator;

/**
 * Decorator of StoryConfiguration that disables mutability of configuration elements.
 */
public class ImmutableStoryConfiguration extends StoryConfiguration {

    private final StoryConfiguration delegate;

    public ImmutableStoryConfiguration() {
        this(new MostUsefulStoryConfiguration());
    }

    public ImmutableStoryConfiguration(StoryConfiguration delegate) {
        this.delegate = delegate;
    }

    public StoryReporter storyReporter() {
       return delegate.storyReporter();
    }

    public StoryDefiner storyDefiner() {
        return delegate.storyDefiner();
    }

    public PendingErrorStrategy pendingErrorStrategy() {
        return delegate.pendingErrorStrategy();
    }

    public StepCreator stepCreator() {
        return delegate.stepCreator();
    }

    public ErrorStrategy errorStrategy() {
        return delegate.errorStrategy();
    }

    public KeyWords keywords() {
        return delegate.keywords();
    }

	public StepdocGenerator stepdocGenerator() {
		return delegate.stepdocGenerator();
	}

	public StepdocReporter stepdocReporter() {
		return delegate.stepdocReporter();
	}

    @Override
    public void useKeywords(KeyWords keywords) {
        notAllowed();
    }

    @Override
    public void useStepCreator(StepCreator stepCreator) {
        notAllowed();
    }

    @Override
    public void usePendingErrorStrategy(PendingErrorStrategy pendingErrorStrategy) {
        notAllowed();
    }

    @Override
    public void useErrorStrategy(ErrorStrategy errorStrategy) {
        notAllowed();
    }

    @Override
    public void useStoryDefiner(StoryDefiner storyDefiner) {
        notAllowed();
    }

    @Override
    public void useStoryReporter(StoryReporter storyReporter) {
        notAllowed();
    }

    @Override
    public void useStepdocReporter(StepdocReporter stepdocReporter) {
        notAllowed();
    }

    @Override
    public void useStepdocGenerator(StepdocGenerator stepdocGenerator) {
        notAllowed();
    }
     
    private void notAllowed() {
        throw new RuntimeException("Configuration elements are immutable");
    }
}