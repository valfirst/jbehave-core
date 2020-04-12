package org.jbehave.core.embedder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jbehave.core.annotations.ScenarioType;
import org.jbehave.core.annotations.Scope;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.Keywords;
import org.jbehave.core.embedder.MatchingStepMonitor.StepMatch;
import org.jbehave.core.failures.BatchFailures;
import org.jbehave.core.failures.FailingUponPendingStep;
import org.jbehave.core.failures.IgnoringStepsFailure;
import org.jbehave.core.failures.PendingStepsFound;
import org.jbehave.core.failures.RestartingScenarioFailure;
import org.jbehave.core.failures.RestartingStoryFailure;
import org.jbehave.core.failures.UUIDExceptionWrapper;
import org.jbehave.core.model.ExamplesTable;
import org.jbehave.core.model.GivenStories;
import org.jbehave.core.model.GivenStory;
import org.jbehave.core.model.Lifecycle;
import org.jbehave.core.model.Meta;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.model.StoryDuration;
import org.jbehave.core.reporters.ConcurrentStoryReporter;
import org.jbehave.core.reporters.DelegatingStoryReporter;
import org.jbehave.core.reporters.StoryReporter;
import org.jbehave.core.steps.*;
import org.jbehave.core.steps.context.StepsContext;
import org.jbehave.core.steps.StepCollector.Stage;
import org.jbehave.core.steps.StepCreator.PendingStep;

/**
 * Creates a tree of {@link Performable} objects for a set of stories, grouping
 * sets of performable steps for each story and scenario, and adding before and
 * after stories steps. The process has two phases:
 * <ol>
 * <li>The tree is populated with groups of performable steps when the stories
 * are added via the {@link #addStories(RunContext, List)} method.</li>
 * <li>The performable steps are then populated with the results when the
 * {@link #performBeforeOrAfterStories(RunContext, Stage)} and
 * {@link #perform(RunContext, Story)} methods are executed.</li>
 * </ol>
 * The tree is created per {@link RunContext} for the set of stories being run
 * but the individual stories can be performed concurrently.
 */
public class PerformableTree {

    private static final Map<String, String> NO_PARAMETERS = Collections.emptyMap();

    private PerformableRoot root = new PerformableRoot();

    public PerformableRoot getRoot() {
        return root;
    }

    public void addStories(RunContext context, List<Story> stories) {
        root.addBeforeSteps(context.beforeOrAfterStoriesSteps(Stage.BEFORE));
        for (Story story : stories) {
            root.add(performableStory(context, story, NO_PARAMETERS));
        }
        root.addAfterSteps(context.beforeOrAfterStoriesSteps(Stage.AFTER));
    }

    private PerformableStory performableStory(RunContext context, Story story, Map<String, String> storyParameters) {
        PerformableStory performableStory = new PerformableStory(story, context.configuration().keywords(),
                context.givenStory());

        FilteredStory filteredStory = context.filter(story);
        Meta storyMeta = story.getMeta();
        boolean storyAllowed = filteredStory.allowed();

        performableStory.allowed(storyAllowed);

        if (storyAllowed) {

            Map<Stage, PerformableSteps> lifecycleSteps = context.lifecycleSteps(story.getLifecycle(), storyMeta,
                    Scope.STORY);

            performableStory.addBeforeSteps(context.beforeOrAfterStorySteps(story, Stage.BEFORE));
            performableStory.addBeforeSteps(lifecycleSteps.get(Stage.BEFORE));
            performableStory.addAll(performableScenarios(context, story, storyParameters, filteredStory));

            // Add Given stories only if story contains allowed scenarios
            if (performableStory.hasAllowedScenarios()) {
                Map<String, String> givenStoryParameters = new HashMap<>(storyParameters);
                addMetaParameters(givenStoryParameters, storyMeta);
                if ( story.hasGivenStories() ) {
                    performableStory.addGivenStories(performableGivenStories(context, story.getGivenStories(),
                        givenStoryParameters, storyMeta));
                }
            }

            performableStory.addAfterSteps(lifecycleSteps.get(Stage.AFTER));
            performableStory.addAfterSteps(context.beforeOrAfterStorySteps(story, Stage.AFTER));
        }

        return performableStory;
    }

    private List<PerformableScenario> performableScenarios(RunContext context, Story story,
            Map<String, String> storyParameters, FilteredStory filterContext) {
        List<PerformableScenario> performableScenarios = new ArrayList<PerformableScenario>();

        ExamplesTable storyExamplesTable = story.getLifecycle().getExamplesTable();
        List<Map<String, String>> storyExamplesTableRows;
        if (storyExamplesTable.isEmpty()) {
            storyExamplesTableRows = new ArrayList<Map<String, String>>();
            storyExamplesTableRows.add(new HashMap<String, String>());
        }
        else {
            storyExamplesTableRows = storyExamplesTable.getRows();
        }

        // determine if before and after scenario steps should be run
        boolean runBeforeAndAfterScenarioSteps = shouldRunBeforeOrAfterScenarioSteps(context);
        for (Map<String, String> storyExamplesTableRow : storyExamplesTableRows) {
            for (Map.Entry<String, String> entry : storyExamplesTableRow.entrySet()) {
                entry.setValue((String)
                        context.configuration().parameterConverters().convert(entry.getValue(), String.class, story));
            }
        }
        for (Map<String, String> storyExamplesTableRow : storyExamplesTableRows) {
            for (Scenario scenario : story.getScenarios()) {
                Map<String, String> scenarioParameters = new HashMap<String, String>(storyParameters);
                PerformableScenario performableScenario = performableScenario(context, story, scenarioParameters,
                        filterContext, runBeforeAndAfterScenarioSteps, scenario, storyExamplesTableRow);
                if (performableScenario.isPerformable()) {
                    performableScenarios.add(performableScenario);
                }
            }
        }
        return performableScenarios;
    }

    private PerformableScenario performableScenario(RunContext context, Story story,
            Map<String, String> storyParameters, FilteredStory filterContext, boolean runBeforeAndAfterScenarioSteps,
            Scenario scenario, Map<String, String> storyExamplesTableRow) {
        PerformableScenario performableScenario = new PerformableScenario(scenario, story.getPath());
        if (context.failureOccurred() && context.configuration().storyControls().skipScenariosAfterFailure()) {
            return performableScenario;
        }

        boolean scenarioAllowed = filterContext.allowed(scenario);

        performableScenario.allowed(scenarioAllowed);

        if (scenarioAllowed) {
            Lifecycle lifecycle = story.getLifecycle();

            Meta storyAndScenarioMeta = scenario.getMeta().inheritFrom(story.getMeta());

            if (isParameterisedByExamples(scenario)) {
                ExamplesTable table = scenario.getExamplesTable();
                List<Map<String, String>> tableRows = table.getRows();
                for (int exampleIndex = 0; exampleIndex < tableRows.size(); exampleIndex++) {
                    Map<String, String> scenarioParameters = tableRows.get(exampleIndex);
                    Map<String, String> scenarioParametersCopy = new HashMap<String, String>(storyParameters);
                    scenarioParametersCopy.putAll(storyExamplesTableRow);
                    scenarioParametersCopy.putAll(scenarioParameters);
                    for (Map.Entry<String, String> scenarioParameterEntry : scenarioParametersCopy.entrySet()) {
                        String value = scenarioParameterEntry.getValue();
                        for (Map.Entry<String, String> storyParameterEntry: storyExamplesTableRow.entrySet())
                        {
                            value = context.configuration().parameterControls().replaceAllDelimitedNames(value,
                                    storyParameterEntry.getKey(), storyParameterEntry.getValue());
                        }
                        scenarioParameterEntry.setValue((String) context.configuration().parameterConverters()
                                 .convert(value, String.class, story));
                    }
                    Map<String, String> parameters = new LinkedHashMap<String, String>(scenarioParametersCopy);
                    for(Map.Entry<String, String> storyExamplesTableRowEntry: storyExamplesTableRow.entrySet()) {
                        String key = storyExamplesTableRowEntry.getKey();
                        if(!parameters.containsKey(key)) {
                            parameters.put(key, storyExamplesTableRowEntry.getValue());
                        }
                    }
                    addExampleScenario(context, scenario, performableScenario, lifecycle, storyAndScenarioMeta,
                            parameters, exampleIndex);
                }
            }
            else if (!storyExamplesTableRow.isEmpty()){
                    addExampleScenario(context, scenario, performableScenario, lifecycle, storyAndScenarioMeta,
                            new HashMap<String, String>(storyExamplesTableRow), -1);
            }else { // plain old scenario
                NormalPerformableScenario normalScenario = normalScenario(context, lifecycle, scenario,
                        storyAndScenarioMeta, storyParameters);

                // run before scenario steps, if allowed
                if (runBeforeAndAfterScenarioSteps) {
                    normalScenario.addBeforeSteps(context.beforeOrAfterScenarioSteps(storyAndScenarioMeta, Stage.BEFORE,
                            ScenarioType.NORMAL));
                }
                performableScenario.useNormalScenario(normalScenario);
                // after scenario steps, if allowed
                if (runBeforeAndAfterScenarioSteps) {
                    normalScenario.addAfterSteps(context.beforeOrAfterScenarioSteps(storyAndScenarioMeta, Stage.AFTER,
                            ScenarioType.NORMAL));
                }
            }
        }
        return performableScenario;
    }

    private void addExampleScenario(RunContext context, Scenario scenario, PerformableScenario performableScenario,
            Lifecycle lifecycle, Meta storyAndScenarioMeta, Map<String, String> parameters, int exampleIndex)
    {
        Meta exampleScenarioMeta = parameterMeta(context, parameters).inheritFrom(storyAndScenarioMeta);
        boolean exampleScenarioAllowed = context.filter().allow(exampleScenarioMeta);

        if (exampleScenarioAllowed) {
            ExamplePerformableScenario exampleScenario = exampleScenario(context, lifecycle, scenario,
                    storyAndScenarioMeta, parameters, exampleIndex);
            performableScenario.addExampleScenario(exampleScenario);
        }
    }

	private NormalPerformableScenario normalScenario(RunContext context,
			Lifecycle lifecycle, Scenario scenario, Meta storyAndScenarioMeta,
			Map<String, String> storyParameters) {
		NormalPerformableScenario normalScenario = new NormalPerformableScenario(scenario);
		normalScenario.setStoryAndScenarioMeta(storyAndScenarioMeta);
		addStepsWithLifecycle(normalScenario, context, lifecycle, storyParameters,
				scenario, storyAndScenarioMeta);
		return normalScenario;
	}

    private ExamplePerformableScenario exampleScenario(RunContext context,
            Lifecycle lifecycle, Scenario scenario, Meta storyAndScenarioMeta,
            Map<String, String> parameters, int exampleIndex) {
	    ExamplePerformableScenario exampleScenario = new ExamplePerformableScenario(scenario, parameters, exampleIndex);
	    exampleScenario.setStoryAndScenarioMeta(storyAndScenarioMeta);
        exampleScenario.addBeforeSteps(context.beforeOrAfterScenarioSteps(storyAndScenarioMeta, Stage.BEFORE,
                ScenarioType.EXAMPLE));
        addStepsWithLifecycle(exampleScenario, context, lifecycle, parameters, scenario, storyAndScenarioMeta);
        exampleScenario.addAfterSteps(context.beforeOrAfterScenarioSteps(storyAndScenarioMeta, Stage.AFTER,
                ScenarioType.EXAMPLE));
        return exampleScenario;
    }

	private Meta parameterMeta(RunContext context, Map<String, String> parameters) {
	    Meta meta = Meta.EMPTY;
        Keywords keywords = context.configuration().keywords();
        String metaText = keywords.meta();
        if (parameters.containsKey(metaText)) {
            meta = Meta.createMeta(parameters.get(metaText), keywords);
        }
        return meta;
	}

	private void addStepsWithLifecycle(AbstractPerformableScenario performableScenario, RunContext context,
			Lifecycle lifecycle, Map<String, String> parameters, Scenario scenario, Meta storyAndScenarioMeta) {
        Map<Stage, PerformableSteps> lifecycleSteps = context.lifecycleSteps(lifecycle, storyAndScenarioMeta,
                Scope.SCENARIO);

        performableScenario.addBeforeSteps(context.beforeOrAfterScenarioSteps(storyAndScenarioMeta, Stage.BEFORE,
                ScenarioType.ANY));
        performableScenario.addBeforeSteps(lifecycleSteps.get(Stage.BEFORE));
		addMetaParameters(parameters, storyAndScenarioMeta);
	performableScenario.addGivenStories(performableGivenStories(context, scenario.getGivenStories(),
		        parameters, storyAndScenarioMeta));
        performableScenario.addSteps(context.scenarioSteps(lifecycle, storyAndScenarioMeta, scenario, parameters));
        performableScenario.addAfterSteps(lifecycleSteps.get(Stage.AFTER));
		performableScenario.addAfterSteps(context.beforeOrAfterScenarioSteps(storyAndScenarioMeta, Stage.AFTER,
                ScenarioType.ANY));
	}

    private List<PerformableStory> performableGivenStories(RunContext context, GivenStories givenStories, Map<String,
            String> parameters, Meta meta) {
        List<PerformableStory> stories = new ArrayList<>();
        if (givenStories.getPaths().size() > 0) {
            for (GivenStory givenStory : givenStories.getStories()) {
                RunContext childContext = context.childContextFor(givenStory);
                // run given story, using any parameters provided
                Story story = storyOfPath(context.configuration(), childContext.path());
                if (!meta.isEmpty()) {
                    story = story.cloneWithMetaAndScenarios(story.getMeta().inheritFrom(meta), story.getScenarios());
                }
                if ( givenStory.hasAnchorParameters() ){
                    story = storyWithMatchingScenarios(story, givenStory.getAnchorParameters());
                }
                Map<String, String> storyParameters = new HashMap<String, String>(parameters);
                storyParameters.putAll(givenStory.getParameters());
                stories.add(performableStory(childContext, story, storyParameters));
            }
        }
        return stories;
    }

    private Story storyWithMatchingScenarios(Story story, Map<String,String> parameters) {
        if ( parameters.isEmpty() ) return story;
        List<Scenario> scenarios = new ArrayList<>();
        for ( Scenario scenario : story.getScenarios() ){
            if ( matchesParameters(scenario, parameters) ){
                scenarios.add(scenario);
            }
        }
        return story.cloneWithMetaAndScenarios(story.getMeta(), scenarios);
    }

    private boolean matchesParameters(Scenario scenario, Map<String, String> parameters) {
        Meta meta = scenario.getMeta();
        for ( String name : parameters.keySet() ){
            if ( meta.hasProperty(name) ){
                return meta.getProperty(name).equals(parameters.get(name));
            }
        }
        return false;
    }

    /**
     * Returns the parsed story from the given path
     *
     * @param configuration the Configuration used to run story
     * @param storyPath the story path
     * @return The parsed Story
     */
    public Story storyOfPath(Configuration configuration, String storyPath) {
        String storyAsText = configuration.storyLoader().loadStoryAsText(storyPath);
        return configuration.storyParser().parseStory(storyAsText, storyPath);
    }

    /**
     * Returns the parsed story from the given text
     *
     * @param configuration the Configuration used to run story
     * @param storyAsText the story text
     * @param storyId the story Id, which will be returned as story path
     * @return The parsed Story
     */
    public Story storyOfText(Configuration configuration, String storyAsText, String storyId) {
        return configuration.storyParser().parseStory(storyAsText, storyId);
    }

    private void addMetaParameters(Map<String, String> storyParameters, Meta meta) {
        for (String name : meta.getPropertyNames()) {
            if (!storyParameters.containsKey(name)) {
                storyParameters.put(name, meta.getProperty(name));
            }
        }
    }

    private boolean shouldRunBeforeOrAfterScenarioSteps(RunContext context) {
        return !context.configuration().storyControls().skipBeforeAndAfterScenarioStepsIfGivenStory()
                || !context.givenStory();
    }

    private boolean isParameterisedByExamples(Scenario scenario) {
        return !scenario.getExamplesTable().isEmpty() && !scenario.getGivenStories().requireParameters();
    }

    static void generatePendingStepMethods(RunContext context, List<Step> steps) {
        List<PendingStep> pendingSteps = new ArrayList<>();
        for (Step step : steps) {
            if (step instanceof PendingStep) {
                pendingSteps.add((PendingStep) step);
            }
        }
        if (!pendingSteps.isEmpty()) {
            PendingStepMethodGenerator generator = new PendingStepMethodGenerator(context.configuration().keywords());
            List<String> methods = new ArrayList<>();
            for (PendingStep pendingStep : pendingSteps) {
                if (!pendingStep.annotated()) {
                    methods.add(generator.generateMethod(pendingStep));
                }
            }
        }
    }

    public interface State {

        State run(Step step, List<StepResult> results, StoryReporter reporter);

        UUIDExceptionWrapper getFailure();
    }

    public final static class FineSoFar implements State {

        @Override
        public State run(Step step, List<StepResult> results, StoryReporter reporter) {
            StepResult result = step.perform(reporter, getFailure());
            results.add(result);

            UUIDExceptionWrapper stepFailure = result.getFailure();
            State state = stepFailure == null ? this : new SomethingHappened(stepFailure);

            for (Step composedStep : step.getComposedSteps()) {
                state = state.run(composedStep, results, reporter);
            }

            result.describeTo(reporter);
            return state;
        }

        @Override
        public UUIDExceptionWrapper getFailure() {
            return null;
        }

    }

    public final static class SomethingHappened implements State {
        private UUIDExceptionWrapper failure;

        public SomethingHappened(UUIDExceptionWrapper failure) {
            this.failure = failure;
        }

        @Override
        public State run(Step step, List<StepResult> results, StoryReporter reporter) {
            StepResult result = step.doNotPerform(reporter, getFailure());
            results.add(result);
            result.describeTo(reporter);
            return this;
        }

        @Override
        public UUIDExceptionWrapper getFailure() {
            return failure;
        }
    }

    public void perform(RunContext context, Story story) {
    	boolean restartingStory = false;

        try {
            performCancellable(context, story);
            if (context.restartStory()){
				context.reporter().restartedStory(story, context.failure(context.state()));
				restartingStory = true;
            	perform(context, story);
            }
        } catch (InterruptedException e) {
            if (context.isCancelled(story)) {
                context.reporter().storyCancelled(story, context.storyDuration(story));
                context.reporter().afterStory(context.givenStory);
            }
	        throw new UUIDExceptionWrapper(e);
        } finally {
            if (!context.givenStory() && !restartingStory) {
                invokeDelayedReporters(context.reporter());
            }
        }
    }

    private void performCancellable(RunContext context, Story story) throws InterruptedException {
        if (context.configuration().storyControls().resetStateBeforeStory()) {
            context.resetState();
            context.resetFailures();
        }

        if (!story.getPath().equals(context.path())) {
            context.currentPath(story.getPath());
        }

        if(context.configuration.dryRun()){
            context.reporter().dryRun();
        }

        root.get(story).perform(context);
        if (context.failureOccurred()) {
            context.addFailure();
        }
    }

    public void performBeforeOrAfterStories(RunContext context, Stage stage) {
        String storyPath = StringUtils.capitalize(stage.name().toLowerCase()) + "Stories";
        context.currentPath(storyPath);
        context.reporter().beforeStory(new Story(storyPath), false);
        try {
            (stage == Stage.BEFORE ? root.beforeSteps : root.afterSteps).perform(context);
        } catch (InterruptedException e) {
            throw new UUIDExceptionWrapper(e);
        } finally {
            invokeDelayedReporters(context.reporter());
        }
        context.reporter().afterStory(false);
        invokeDelayedReporters(context.reporter());
    }

    private void invokeDelayedReporters(StoryReporter reporter) {
        if (reporter instanceof ConcurrentStoryReporter) {
            ((ConcurrentStoryReporter) reporter).invokeDelayed();
        } else if (reporter instanceof DelegatingStoryReporter) {
            for (StoryReporter delegate : ((DelegatingStoryReporter) reporter).getDelegates()) {
                invokeDelayedReporters(delegate);
            }
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    /**
     * The context for running a story.
     */
    public static class RunContext {
        private final Configuration configuration;
        private final List<CandidateSteps> candidateSteps;
		private final EmbedderMonitor embedderMonitor;
        private final MetaFilter filter;
        private final BatchFailures failures;
		private final StepsContext stepsContext;
        private Map<Story, StoryDuration> cancelledStories = new HashMap<>();
        private Map<String, List<PendingStep>> pendingStories = new HashMap<>();
        private boolean givenStory;
        private final ThreadLocal<StoryRunContext> storyRunContext = ThreadLocal.withInitial(StoryRunContext::new);

        public RunContext(Configuration configuration, List<CandidateSteps> candidateSteps, EmbedderMonitor embedderMonitor,
                MetaFilter filter, BatchFailures failures) {
            this.configuration = configuration;
            this.candidateSteps = candidateSteps;
            this.embedderMonitor = embedderMonitor;
            this.filter = filter;
            this.failures = failures;
            this.stepsContext = configuration.stepsContext();
            resetState();
        }

		public StepsContext stepsContext() {
			return stepsContext;
		}

    	public boolean restartScenario() {
    		Throwable cause = failure(state());
    		while (cause != null) {
    			if (cause instanceof RestartingScenarioFailure) {
    				return true;
    			}
    			cause = cause.getCause();
    		}
    		return false;
    	}

    	public boolean restartStory() {
    		Throwable cause = failure(state());
    		while (cause != null) {
    			if (cause instanceof RestartingStoryFailure) {
    				return true;
    			}
    			cause = cause.getCause();
    		}
    		return false;
    	}

		public void currentPath(String path) {
            currentRunContext().pathIs(path);
            currentRunContext().reporterIs(configuration.storyReporter(path));
        }

        public void interruptIfCancelled() throws InterruptedException {
            for (Story story : cancelledStories.keySet()) {
                if (path().equals(story.getPath())) {
                    throw new InterruptedException(path());
                }
            }
        }

        public boolean dryRun() {
            return configuration.storyControls().dryRun();
        }

        public Configuration configuration() {
            return configuration;
        }

        public boolean givenStory() {
            return givenStory;
        }

        public String path() {
            return currentRunContext().path();
        }

        public FilteredStory filter(Story story) {
            return new FilteredStory(filter, story, configuration.storyControls(), givenStory());
        }

        public MetaFilter filter() {
            return filter;
        }

        public PerformableSteps beforeOrAfterStoriesSteps(Stage stage) {
            return new PerformableSteps(configuration.stepCollector().collectBeforeOrAfterStoriesSteps(candidateSteps,
                    stage));
        }

        public PerformableSteps beforeOrAfterStorySteps(Story story, Stage stage) {
            return new PerformableSteps(configuration.stepCollector().collectBeforeOrAfterStorySteps(candidateSteps,
                    story, stage, givenStory));
        }

        public PerformableSteps beforeOrAfterScenarioSteps(Meta storyAndScenarioMeta, Stage stage, ScenarioType type) {
            return new PerformableSteps(configuration.stepCollector().collectBeforeOrAfterScenarioSteps(candidateSteps,
                    storyAndScenarioMeta, stage, type));
        }

        @Deprecated
        public PerformableSteps lifecycleSteps(Lifecycle lifecycle, Meta meta, Stage stage) {
            return lifecycleSteps(lifecycle, meta, stage, Scope.SCENARIO);
        }

        @Deprecated
        public PerformableSteps lifecycleSteps(Lifecycle lifecycle, Meta meta, Stage stage, Scope scope) {
            MatchingStepMonitor monitor = new MatchingStepMonitor(configuration.stepMonitor());
            List<Step> steps = configuration.stepCollector().collectLifecycleSteps(candidateSteps, lifecycle, meta, stage, scope);
            return new PerformableSteps(steps, monitor.matched());
        }

        private Map<Stage, PerformableSteps> lifecycleSteps(Lifecycle lifecycle, Meta meta, Scope scope) {
            MatchingStepMonitor monitor = new MatchingStepMonitor(configuration.stepMonitor());
            Map<Stage, List<Step>> steps = configuration.stepCollector().collectLifecycleSteps(candidateSteps,
                    lifecycle, meta, scope, monitor);
            Map<Stage, PerformableSteps> performableSteps = new EnumMap<>(Stage.class);
            for (Map.Entry<Stage, List<Step>> entry : steps.entrySet()) {
                performableSteps.put(entry.getKey(), new PerformableSteps(entry.getValue(), monitor.matched()));
            }
            return performableSteps;
        }

        private PerformableSteps scenarioSteps(Lifecycle lifecycle, Meta meta, Scenario scenario,
                Map<String, String> parameters) {
            MatchingStepMonitor monitor = new MatchingStepMonitor(configuration.stepMonitor());
            StepCollector stepCollector = configuration.stepCollector();
            Map<Stage, List<Step>> beforeOrAfterStepSteps = stepCollector.collectLifecycleSteps(candidateSteps,
                    lifecycle, meta, Scope.STEP, monitor);
            List<Step> steps = new LinkedList<>();
            for (Step step : stepCollector.collectScenarioSteps(candidateSteps, scenario, parameters, monitor)) {
                steps.addAll(beforeOrAfterStepSteps.get(Stage.BEFORE));
                steps.add(step);
                steps.addAll(beforeOrAfterStepSteps.get(Stage.AFTER));
            }
            return new PerformableSteps(steps, monitor.matched());
        }

        @Deprecated
        public PerformableSteps scenarioSteps(Scenario scenario, Map<String, String> parameters) {
            MatchingStepMonitor monitor = new MatchingStepMonitor(configuration.stepMonitor());
            List<Step> steps = configuration.stepCollector().collectScenarioSteps(candidateSteps, scenario, parameters,
                    monitor);
            return new PerformableSteps(steps, monitor.matched());
        }

        public RunContext childContextFor(GivenStory givenStory) {
            RunContext child = new RunContext(configuration, candidateSteps, embedderMonitor, filter, failures);
            child.currentRunContext().pathIs(configuration.pathCalculator().calculate(path(), givenStory.getPath()));
            child.givenStory = true;
            return child;
        }

        public void cancelStory(Story story, StoryDuration storyDuration) {
            cancelledStories.put(story, storyDuration);
        }

        public boolean isCancelled(Story story) {
            return cancelledStories.containsKey(story);
        }

        public StoryDuration storyDuration(Story story) {
            return cancelledStories.get(story);
        }

        public State state() {
            return currentRunContext().state();
        }

        public void stateIs(State state) {
            currentRunContext().stateIs(state);
        }

        public boolean failureOccurred() {
            return failed(state());
        }

        public void resetState() {
            currentRunContext().resetState();
        }

        public void resetFailures() {
            this.failures.clear();
        }

        public StoryReporter reporter() {
            return currentRunContext().reporter();
        }

        public boolean failed(State state) {
            return !state.getClass().equals(FineSoFar.class);
        }

        public Throwable failure(State state) {
            if (failed(state)) {
                return state.getFailure().getCause();
            }
            return null;
        }

        public void addFailure() {
            Throwable failure = failure(state());
            if (failure != null) {
                failures.put(state().toString(), failure);
            }
        }

        public void addFailure(String path, Throwable cause) {
            if (cause != null) {
                failures.put(path, cause);
            }
        }

        public void pendingSteps(List<PendingStep> pendingSteps) {
            if (!pendingSteps.isEmpty()) {
                pendingStories.put(path(), pendingSteps);
            }
        }

        public boolean hasPendingSteps() {
            return pendingStories.containsKey(path());
        }

        public boolean isStoryPending() {
            return pendingStories.containsKey(path());
        }

        public boolean hasFailed() {
            return failed(state());
        }

        public Status status(State initial) {
            if (isStoryPending()) {
                return Status.PENDING;
            } else if (failed(initial)) {
                return Status.NOT_PERFORMED;
            } else {
                return (hasFailed() ? Status.FAILED : Status.SUCCESSFUL);
            }
        }

        public MetaFilter getFilter() {
            return filter;
        }

        public BatchFailures getFailures() {
            return failures;
        }

        public EmbedderMonitor embedderMonitor(){
        	return embedderMonitor;
        }

        private StoryRunContext currentRunContext() {
            return storyRunContext.get();
        }
    }

    private static class StoryRunContext {
        private State state;
        private String path;
        private StoryReporter reporter;

        private State state() {
            return state;
        }

        private void stateIs(State state) {
            this.state = state;
        }

        private void resetState() {
            this.state = new FineSoFar();
        }

        private String path() {
            return path;
        }

        private void pathIs(String path) {
            this.path = path;
        }

        public StoryReporter reporter() {
            return this.reporter;
        }

        private void reporterIs(StoryReporter reporter) {
            this.reporter = reporter;
        }
    }

    public static class FailureContext {

        List<Throwable> failures = new ArrayList<>();

        public void addFailure(Throwable failure) {
            failures.add(failure);
        }

        public List<Throwable> getFailures(){
            return failures;
        }

    }
    
    public static interface Performable {

        void perform(RunContext context) throws InterruptedException;
        void reportFailures(FailureContext context);

    }

    public static class PerformableRoot {

        private PerformableSteps beforeSteps = new PerformableSteps();
        private Map<String, PerformableStory> stories = new LinkedHashMap<>();
        private PerformableSteps afterSteps = new PerformableSteps();

        public void addBeforeSteps(PerformableSteps beforeSteps) {
            this.beforeSteps = beforeSteps;
        }

        public void add(PerformableStory performableStory) {
            stories.put(performableStory.getStory().getPath(), performableStory);
        }

        public void addAfterSteps(PerformableSteps afterSteps) {
            this.afterSteps = afterSteps;
        }

        public PerformableStory get(Story story) {
            PerformableStory performableStory = stories.get(story.getPath());
            if (performableStory != null) {
                return performableStory;
            }
            throw new RuntimeException("No performable story for path " + story.getPath());
        }

        public List<PerformableStory> getStories() {
            return new ArrayList<>(stories.values());
        }

    }

    public static enum Status {
        SUCCESSFUL, FAILED, PENDING, NOT_PERFORMED, NOT_ALLOWED;
    }

    private static void performGivenStories(RunContext context, List<PerformableStory> performableGivenStories,
            GivenStories givenStories) throws InterruptedException {
        if (performableGivenStories.size() > 0) {
            context.reporter().beforeGivenStories();
            context.reporter().givenStories(givenStories);
            for (PerformableStory story : performableGivenStories) {
                story.perform(context);
            }
            context.reporter().afterGivenStories();
        }
    }

    public static class PerformableStory implements Performable {

        private final Story story;
        private final transient Keywords keywords;
        private final boolean givenStory;
        private boolean allowed;
        private Status status;
        private Timing timing = new Timing();
        private List<PerformableStory> givenStories = new ArrayList<>();
        private List<PerformableSteps> beforeSteps = new ArrayList<>();
        private List<PerformableScenario> scenarios = new ArrayList<>();
        private List<PerformableSteps> afterSteps = new ArrayList<>();

        public PerformableStory(Story story, Keywords keywords, boolean givenStory) {
            this.story = story;
            this.keywords = keywords;
            this.givenStory = givenStory;
        }

        public void allowed(boolean allowed) {
            this.allowed = allowed;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Story getStory() {
            return story;
        }

        public Keywords getKeywords(){
            return keywords;
        }

        public boolean givenStory() {
            return givenStory;
        }

        public Status getStatus() {
            return status;
        }

        public Timing getTiming() {
            return timing;
        }

        public void addGivenStories(List<PerformableStory> performableGivenStories) {
            this.givenStories.addAll(performableGivenStories);
        }

        public void addBeforeSteps(PerformableSteps beforeSteps) {
            this.beforeSteps.add(beforeSteps);
        }

        public void addAfterSteps(PerformableSteps afterSteps) {
            this.afterSteps.add(afterSteps);
        }

        public void add(PerformableScenario performableScenario) {
            scenarios.add(performableScenario);
        }

        public void addAll(List<PerformableScenario> performableScenarios) {
            scenarios.addAll(performableScenarios);
        }

        @Override
        public void perform(RunContext context) throws InterruptedException {
            if (!allowed) {
                context.reporter().storyNotAllowed(story, context.filter.asString());
                this.status = Status.NOT_ALLOWED;
            }
            Timer timer = new Timer().start();
            try {
                context.stepsContext().resetStory();
                context.reporter().beforeStory(story, givenStory);
                context.reporter().narrative(story.getNarrative());
                context.reporter().lifecyle(story.getLifecycle());
                State state = context.state();
                performStorySteps(context, beforeSteps, Stage.BEFORE);
                performGivenStories(context, givenStories, story.getGivenStories());
                if (!context.failureOccurred() || !context.configuration().storyControls().skipStoryIfGivenStoryFailed()) {
                    performScenarios(context);
                }
                performStorySteps(context, afterSteps, Stage.AFTER);
                if (context.restartStory()) {
                    context.reporter().afterStory(true);
                } else {
                    context.reporter().afterStory(givenStory);
                }
                this.status = context.status(state);
            } finally {
                timing.setTimings(timer.stop());
            }
        }

        @Override
        public void reportFailures(FailureContext context) {
            for (PerformableScenario scenario : scenarios) {
                scenario.reportFailures(context);
            }
        }

        private void performStorySteps(RunContext context, List<PerformableSteps> storySteps, Stage stage)
                throws InterruptedException {
            context.reporter().beforeStorySteps(stage);
            for (PerformableSteps steps : storySteps) {
                steps.perform(context);
            }
            context.reporter().afterStorySteps(stage);
        }

        private void performScenarios(RunContext context) throws InterruptedException {
            for (PerformableScenario scenario : scenarios) {
                scenario.perform(context);
            }
        }

        public List<PerformableScenario> getScenarios() {
            return scenarios;
        }

        public boolean hasAllowedScenarios() {
            for (PerformableScenario scenario : getScenarios()) {
                if (scenario.isAllowed()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class PerformableScenario implements Performable {

        private final Scenario scenario;
        private final String storyPath;
        private boolean allowed;
        @SuppressWarnings("unused")
        private Status status;
        private Timing timing = new Timing();
		private NormalPerformableScenario normalScenario;
        private List<ExamplePerformableScenario> exampleScenarios;

        public PerformableScenario(Scenario scenario, String storyPath) {
            this.scenario = scenario;
            this.storyPath = storyPath;
        }

        public void useNormalScenario(NormalPerformableScenario normalScenario) {
            this.normalScenario = normalScenario;
        }

        public void addExampleScenario(ExamplePerformableScenario exampleScenario) {
            if ( exampleScenarios == null ){
                exampleScenarios = new ArrayList<>();
            }
            exampleScenarios.add(exampleScenario);
        }

        public void allowed(boolean allowed) {
            this.allowed = allowed;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public Status getStatus() { return status; }

        public Timing getTiming() { return timing; }

        public Scenario getScenario() {
            return scenario;
        }

        public String getStoryPath() {
            return storyPath;
        }

        public Throwable getFailure() {
            FailureContext context = new FailureContext();
            reportFailures(context);
            List<Throwable> failures = context.getFailures();
            if ( failures.size() > 0 ){
                return failures.get(0);
            }
            return null;
        }

        public boolean hasNormalScenario() {
            return normalScenario != null;
        }

        public boolean hasExamples() {
            return exampleScenarios != null && exampleScenarios.size() > 0;
        }

        public boolean isPerformable() {
            return hasNormalScenario() || hasExamples() || !isAllowed();
        }

        public List<ExamplePerformableScenario> getExamples() {
            return exampleScenarios;
        }

        @Override
        public void perform(RunContext context) throws InterruptedException {
            if (!isAllowed()) {
                context.embedderMonitor().scenarioNotAllowed(scenario, context.filter());
                return;
            }
            Timer timer = new Timer().start();
            try {
                context.stepsContext().resetScenario();
                context.reporter().beforeScenario(scenario);
                context.reporter().beforeScenario(scenario.getTitle());
                context.reporter().scenarioMeta(scenario.getMeta());
                State state = context.state();
                if ( hasExamples() ) {
                    context.reporter().beforeExamples(scenario.getSteps(),
                            scenario.getExamplesTable());
                    for (ExamplePerformableScenario exampleScenario : exampleScenarios) {
                        exampleScenario.perform(context);
                    }
                    context.reporter().afterExamples();
                } else {
                    context.stepsContext().resetExample();
                    normalScenario.perform(context);
                }
                this.status = context.status(state);
                context.reporter().afterScenario();
            } finally {
                timing.setTimings(timer.stop());
            }
        }

        @Override
        public void reportFailures(FailureContext context) {
            if ( hasExamples() ){
                for (ExamplePerformableScenario exampleScenario : exampleScenarios) {
                    exampleScenario.reportFailures(context);
                }
            } else {
                normalScenario.reportFailures(context);
            }
        }

    }

    public static abstract class AbstractPerformableScenario implements Performable {

        protected final Map<String, String> parameters;
        protected final List<PerformableStory> givenStories = new ArrayList<>();
        protected final PerformableSteps beforeSteps = new PerformableSteps();
        protected final PerformableSteps steps = new PerformableSteps();
        protected final PerformableSteps afterSteps = new PerformableSteps();
        private Meta storyAndScenarioMeta = new Meta();

        public AbstractPerformableScenario() {
            this(new HashMap<String, String>());
        }

        public AbstractPerformableScenario(Map<String, String> parameters) {
            this.parameters = parameters;
        }

        public void addGivenStories(List<PerformableStory> givenStories) {
            this.givenStories.addAll(givenStories);
        }

        public void addBeforeSteps(PerformableSteps beforeSteps) {
            this.beforeSteps.add(beforeSteps);
        }

        public void addSteps(PerformableSteps steps) {
            this.steps.add(steps);
        }

        public void addAfterSteps(PerformableSteps afterSteps) {
            this.afterSteps.add(afterSteps);
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

		protected void performRestartableSteps(RunContext context)
				throws InterruptedException {
			boolean restart = true;
			while (restart) {
				restart = false;
				try {
				    perform(steps, context, r -> r.beforeScenarioSteps(null), r -> r.afterScenarioSteps(null));
				} catch (RestartingScenarioFailure e) {
				    restart = true;
				    continue;
				}
			}
		}

        protected void perform(RunContext context, GivenStories stories) throws InterruptedException {
            perform(beforeSteps, context, r -> r.beforeScenarioSteps(Stage.BEFORE),
                r -> r.afterScenarioSteps(Stage.BEFORE));
            performGivenStories(context, givenStories, stories);
            performRestartableSteps(context);
            perform(afterSteps, context, r -> r.beforeScenarioSteps(Stage.AFTER),
                r -> r.afterScenarioSteps(Stage.AFTER));
        }

        protected void perform(PerformableSteps performableSteps, RunContext context, Consumer<StoryReporter> before,
                Consumer<StoryReporter> after) throws InterruptedException {
            StoryReporter reporter = context.reporter();
            before.accept(reporter);
            performableSteps.perform(context);
            after.accept(reporter);
        }

		public Meta getStoryAndScenarioMeta() {
		    return storyAndScenarioMeta;
		}

		public void setStoryAndScenarioMeta(Meta storyAndScenarioMeta) {
		    this.storyAndScenarioMeta = storyAndScenarioMeta;
		}
    }

    public static class NormalPerformableScenario extends AbstractPerformableScenario {

        private transient Scenario scenario;

		public NormalPerformableScenario(Scenario scenario) {
			this.scenario = scenario;
        }

        @Override
        public void perform(RunContext context) throws InterruptedException {
            if (context.configuration().storyControls().resetStateBeforeScenario()) {
                context.resetState();
            }
            perform(context, scenario.getGivenStories());
        }

        @Override
        public void reportFailures(FailureContext context) {
            beforeSteps.reportFailures(context);
            steps.reportFailures(context);
            afterSteps.reportFailures(context);
        }

    }

    public static class ExamplePerformableScenario extends AbstractPerformableScenario {

        private transient Scenario scenario;
        private final int exampleIndex;

		public ExamplePerformableScenario(Scenario scenario, Map<String, String> exampleParameters, int exampleIndex) {
        	super(exampleParameters);
			this.scenario = scenario;
			this.exampleIndex = exampleIndex;
        }

        @Override
        public void perform(RunContext context) throws InterruptedException {
			Meta parameterMeta = parameterMeta(context.configuration().keywords(), parameters).inheritFrom(getStoryAndScenarioMeta());
			if (!parameterMeta.isEmpty() && !context.filter().allow(parameterMeta)) {
				return;
			}
            if (context.configuration().storyControls().resetStateBeforeScenario()) {
                context.resetState();
            }
            context.stepsContext().resetExample();
            context.reporter().example(parameters);
            context.reporter().example(parameters, exampleIndex);
            perform(context, scenario.getGivenStories());
        }

        @Override
        public void reportFailures(FailureContext context) {
            beforeSteps.reportFailures(context);
            steps.reportFailures(context);
            afterSteps.reportFailures(context);
        }

        private Meta parameterMeta(Keywords keywords, Map<String, String> parameters) {
            String meta = keywords.meta();
            if (parameters.containsKey(meta)) {
                return Meta.createMeta(parameters.get(meta), keywords);
            }
            return Meta.EMPTY;
        }

    }

    public static class PerformableSteps implements Performable {

        private transient final List<Step> steps;
        private transient final List<PendingStep> pendingSteps;
        private List<StepMatch> matches;
        private List<StepResult> results;

        public PerformableSteps() {
            this(null);
        }

        public PerformableSteps(List<Step> steps) {
            this(steps, null);
        }

        public PerformableSteps(List<Step> steps, List<StepMatch> stepMatches) {
            this.steps =  ( steps != null ? steps : new ArrayList<Step>() );
            this.pendingSteps = pendingSteps();
            this.matches = stepMatches;
        }

        public void add(PerformableSteps performableSteps){
            this.steps.addAll(performableSteps.steps);
            this.pendingSteps.addAll(performableSteps.pendingSteps);
            if ( performableSteps.matches != null ){
                if ( this.matches == null ){
                    this.matches = new ArrayList<>();
                }
                this.matches.addAll(performableSteps.matches);
            }
        }

        @Override
        public void perform(RunContext context) throws InterruptedException {
            if (steps.size() == 0) {
                return;
            }
		    Keywords keywords = context.configuration().keywords();
            State state = context.state();
            StoryReporter reporter = context.reporter();
            results = new ArrayList<>();
            boolean ignoring = false;
            for (Step step : steps) {
                try {
					context.interruptIfCancelled();
					if (ignoring) {
						reporter.ignorable(step.asString(keywords));
					} else {
					    state = state.run(step, results, reporter);
					}
				} catch (IgnoringStepsFailure e) {
					reporter.ignorable(step.asString(keywords));
				    ignoring = true;
				} catch (RestartingScenarioFailure e) {
	                reporter.restarted(step.asString(keywords), e);
	                throw e;
				}
            }
            context.stateIs(state);
            context.pendingSteps(pendingSteps);
            generatePendingStepMethods(context, pendingSteps);
        }

        @Override
        public void reportFailures(FailureContext context) {
            // Results can be null if the steps are not executed
            if ( results == null ) return;
            for ( StepResult result : results ){
                if ( result instanceof AbstractStepResult.Failed ){
                    context.addFailure(result.getFailure());
                }
            }
        }

        private List<PendingStep> pendingSteps() {
            List<PendingStep> pending = new ArrayList<>();
            for (Step step : steps) {
                if (step instanceof PendingStep) {
                    pending.add((PendingStep) step);
                }
            }
            return pending;
        }

        private void generatePendingStepMethods(RunContext context, List<PendingStep> pendingSteps) {
            if (!pendingSteps.isEmpty()) {
                PendingStepMethodGenerator generator = new PendingStepMethodGenerator(context.configuration()
                        .keywords());
                List<String> methods = new ArrayList<>();
                for (PendingStep pendingStep : pendingSteps) {
                    if (!pendingStep.annotated()) {
                        methods.add(generator.generateMethod(pendingStep));
                    }
                }
                context.reporter().pendingMethods(methods);
                if ( context.configuration().pendingStepStrategy() instanceof FailingUponPendingStep ){
                     throw new PendingStepsFound(pendingSteps);
                }
            }
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
        }

    }

    public RunContext newRunContext(Configuration configuration, List<CandidateSteps> candidateSteps,
            EmbedderMonitor embedderMonitor, MetaFilter filter, BatchFailures failures) {
        return new RunContext(configuration, candidateSteps, embedderMonitor, filter, failures);
    }

}
