package org.jbehave.core.parsers;

import static java.util.Arrays.asList;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.lang3.StringUtils.removeStart;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jbehave.core.annotations.AfterScenario.Outcome;
import org.jbehave.core.annotations.Scope;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.Keywords;
import org.jbehave.core.i18n.LocalizedKeywords;
import org.jbehave.core.io.LoadFromClasspath;
import org.jbehave.core.io.ResourceLoader;
import org.jbehave.core.model.Description;
import org.jbehave.core.model.ExamplesTable;
import org.jbehave.core.model.ExamplesTableFactory;
import org.jbehave.core.model.FailedStory;
import org.jbehave.core.model.GivenStories;
import org.jbehave.core.model.Lifecycle;
import org.jbehave.core.model.Lifecycle.Steps;
import org.jbehave.core.model.Meta;
import org.jbehave.core.model.Narrative;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.model.TableTransformers;
import org.jbehave.core.steps.ParameterControls;
import org.jbehave.core.steps.ParameterConverters;

/**
 * Pattern-based story parser, which uses the keywords provided to parse the
 * textual story into a {@link Story}.
 */
public class RegexStoryParser extends AbstractRegexParser implements StoryParser {

    public static final ResourceLoader DEFAULT_RESOURCE_LOADER = new LoadFromClasspath();
    public static final TableTransformers DEFAULT_TABLE_TRANSFORMERS = new TableTransformers();
    private final ExamplesTableFactory tableFactory;
    private final ParameterControls parameterControls = new ParameterControls();
    private Meta skipMeta;
    private String skippedExample;

    public RegexStoryParser() {
        this(new LocalizedKeywords());
    }

    public RegexStoryParser(Keywords keywords) {
        this(keywords, DEFAULT_RESOURCE_LOADER, DEFAULT_TABLE_TRANSFORMERS);
    }

    public RegexStoryParser(ResourceLoader resourceLoader, TableTransformers tableTransformers) {
        this(new LocalizedKeywords(), resourceLoader, tableTransformers);
    }

    public RegexStoryParser(Keywords keywords, ResourceLoader resourceLoader, TableTransformers tableTransformers) {
        this(keywords, new ExamplesTableFactory(keywords, resourceLoader, tableTransformers));
    }

    public RegexStoryParser(ExamplesTableFactory tableFactory) {
        this(tableFactory.keywords(), tableFactory);
    }

    public RegexStoryParser(Keywords keywords, ExamplesTableFactory tableFactory) {
        super(keywords);
        this.tableFactory = tableFactory;
        // must ensure that both are using same keywords
        this.tableFactory.useKeywords(keywords);
    }

    public RegexStoryParser(Configuration configuration) {
        super(configuration.keywords());
        this.tableFactory = new ExamplesTableFactory(configuration);
    }

    @Override
    public Story parseStory(String storyAsText) {
        return parseStory(storyAsText, null);
    }

    @Override
    public Story parseStory(String storyAsText, String storyPath) {
        Description description = parseDescriptionFrom(storyAsText);
        Meta meta = parseStoryMetaFrom(storyAsText);
        Narrative narrative = parseNarrativeFrom(storyAsText);
        GivenStories givenStories = parseGivenStories(storyAsText);
        try {
            Lifecycle lifecycle = parseLifecycle(storyAsText);
            ExamplesTable storyExamplesTable = null;
            if (lifecycle == null) {
                meta = meta.inheritFrom(skipMeta);
                lifecycle = Lifecycle.EMPTY;
            } else {
                storyExamplesTable = lifecycle.getExamplesTable();
                if (!storyExamplesTable.isEmpty()) {
                    useExamplesTableForGivenStories(givenStories, storyExamplesTable);
                }
            }
            List<Scenario> scenarios = parseScenariosFrom(storyAsText, storyExamplesTable);
            Story story = new Story(storyPath, description, meta, narrative, givenStories, lifecycle, scenarios);
            return nameStory(story, storyPath);
        } catch (ExamplesCutException ex) {
            return nameStory(new FailedStory(storyPath, meta, "Exception at story parsing", "Exception at building " +
                    "Examples Table", ex), storyPath);
        }
    }

    private Story nameStory(Story story, String storyPath) {
        if (storyPath != null) {
            story.namedAs(new File(storyPath).getName());
        }
        return story;
    }

    private Description parseDescriptionFrom(String storyAsText) {
        Matcher findingDescription = findingDescription().matcher(storyAsText);
        if (findingDescription.matches()) {
            return new Description(findingDescription.group(1).trim());
        }
        return Description.EMPTY;
    }

    private Meta parseStoryMetaFrom(String storyAsText) {
        Matcher findingMeta = findingStoryMeta().matcher(preScenarioText(storyAsText));
        if (findingMeta.matches()) {
            String meta = findingMeta.group(1).trim();
            return Meta.createMeta(meta, keywords());
        }
        return Meta.EMPTY;
    }

    private String preScenarioText(String storyAsText) {
        String[] split = storyAsText.split(keywords().scenario());
        return split.length > 0 ? split[0] : storyAsText;
    }

    private Narrative parseNarrativeFrom(String storyAsText) {
        Matcher findingNarrative = findingNarrative().matcher(storyAsText);
        if (findingNarrative.matches()) {
            String narrative = findingNarrative.group(1).trim();
            return createNarrative(narrative);
        }
        return Narrative.EMPTY;
    }

    private Narrative createNarrative(String narrative) {
        Matcher findingElements = findingNarrativeElements().matcher(narrative);
        if (findingElements.matches()) {
            String inOrderTo = findingElements.group(1).trim();
            String asA = findingElements.group(2).trim();
            String iWantTo = findingElements.group(3).trim();
            return new Narrative(inOrderTo, asA, iWantTo);
        }
        Matcher findingAlternativeElements = findingAlternativeNarrativeElements().matcher(narrative);
        if (findingAlternativeElements.matches()) {
            String asA = findingAlternativeElements.group(1).trim();
            String iWantTo = findingAlternativeElements.group(2).trim();
            String soThat = findingAlternativeElements.group(3).trim();
            return new Narrative("", asA, iWantTo, soThat);
        }
        return Narrative.EMPTY;
    }

    private GivenStories parseGivenStories(String storyAsText) {
        String scenarioKeyword = keywords().scenario();
        // use text before scenario keyword, if found
        String beforeScenario = "";
        if (StringUtils.contains(storyAsText, scenarioKeyword)) {
            beforeScenario = StringUtils.substringBefore(storyAsText, scenarioKeyword);
        }
        Matcher findingGivenStories = findingStoryGivenStories().matcher(beforeScenario);
        String givenStories = findingGivenStories.find() ? findingGivenStories.group(1).trim() : NONE;
        return new GivenStories(givenStories);
    }

    private Lifecycle parseLifecycle(String storyAsText) {
        String scenarioKeyword = keywords().scenario();
        // use text before scenario keyword, if found
        String beforeScenario = "";
        if (StringUtils.contains(storyAsText, scenarioKeyword)) {
            beforeScenario = StringUtils.substringBefore(storyAsText, scenarioKeyword);
        }
        Matcher findingLifecycle = findingLifecycle().matcher(beforeScenario);
        String lifecycle;
        ExamplesTable examplesTable;
        if (findingLifecycle.find()) {
            lifecycle = findingLifecycle.group(1).trim();
            String examplesTableAsString = findExamplesTable(findingLifecycle.group(0));
            examplesTable = parseExamplesTable(examplesTableAsString, null);
            if (examplesTable == null) {
                return null;
            }
        }
        else {
            lifecycle = NONE;
            examplesTable = ExamplesTable.EMPTY;
        }
        Matcher findingBeforeAndAfter = compile(".*" + keywords().before() + "(.*)\\s*" + keywords().after() + "(.*)\\s*", DOTALL).matcher(lifecycle);
        if (findingBeforeAndAfter.matches()) {
            String beforeLifecycle = findingBeforeAndAfter.group(1).trim();
            List<Steps> beforeSteps = parseBeforeLifecycle(beforeLifecycle);
            String afterLifecycle = findingBeforeAndAfter.group(2).trim();
            List<Steps> afterSteps = parseAfterLifecycle(afterLifecycle);
            return new Lifecycle(examplesTable, beforeSteps, afterSteps);
        }
        Matcher findingBefore = compile(".*" + keywords().before() + "(.*)\\s*", DOTALL).matcher(lifecycle);
        if (findingBefore.matches()) {
            String beforeLifecycle = findingBefore.group(1).trim();
            List<Steps> beforeSteps = parseBeforeLifecycle(beforeLifecycle);
            return new Lifecycle(examplesTable, beforeSteps, Arrays.<Steps>asList());
        }
        Matcher findingAfter = compile(".*" + keywords().after() + "(.*)\\s*", DOTALL).matcher(lifecycle);
        if (findingAfter.matches()) {
            List<Steps> beforeSteps = asList();
            String afterLifecycle = findingAfter.group(1).trim();
            List<Steps> afterSteps = parseAfterLifecycle(afterLifecycle);
            return new Lifecycle(examplesTable, beforeSteps, afterSteps);
        }
        return new Lifecycle(examplesTable);
    }

    private Pattern findingBeforeAndAfterSteps() {
        String initialStartingWords = concatenateWithOr("\\n", "", keywords().before(), keywords().after());
        String followingStartingWords = concatenateFollowingStartingWords();
        return compile(
                "((" + initialStartingWords + ")\\s(.)*?)\\s*(\\Z|" + followingStartingWords + "|\\n" + keywords().examplesTable() + ")", DOTALL);
    }

    private List<Steps> parseBeforeLifecycle(String lifecycleAsText) {
        List<Steps> list = new ArrayList<>();
        for (String byScope : lifecycleAsText.split(keywords().scope())) {
            byScope = byScope.trim();
            if (byScope.isEmpty())
                continue;
            Scope scope = parseScope(findScope(keywords().scope() + byScope));
            Steps steps = new Steps(scope, findSteps(startingWithNL(byScope)));
            list.add(steps);
        }
        return list;
    }

    private List<Steps> parseAfterLifecycle(String lifecycleAsText) {
        List<Steps> list = new ArrayList<>();
        for (String byScope : lifecycleAsText.split(keywords().scope())) {
            byScope = byScope.trim();
            if (byScope.isEmpty())
                continue;
            Scope scope = parseScope(findScope(keywords().scope() + byScope));
            for (String byOutcome : byScope.split(keywords().outcome())) {
                byOutcome = byOutcome.trim();
                if (byOutcome.isEmpty())
                    continue;
                String outcomeAsText = findOutcome(byOutcome);
                String filtersAsText = findFilters(removeStart(byOutcome, outcomeAsText));
                List<String> steps = findSteps(startingWithNL(removeStart(byOutcome, filtersAsText)));
                list.add(new Steps(scope, parseOutcome(outcomeAsText), parseFilters(filtersAsText), steps));
            }
        }
        return list;
    }

    private String findScope(String lifecycleAsText) {
        Matcher findingScope = findingLifecycleScope().matcher(lifecycleAsText.trim());
        if (findingScope.matches()) {
            return findingScope.group(1).trim();
        }
        return NONE;
    }

    private Scope parseScope(String scopeAsText) {
        if ( scopeAsText.trim().equals(keywords().scopeStep()) ){
            return Scope.STEP;
        } else if ( scopeAsText.trim().equals(keywords().scopeScenario()) ){
            return Scope.SCENARIO;
        }
        else if (scopeAsText.trim().equals(keywords().scopeStory())) {
            return Scope.STORY;
        }
        return Scope.SCENARIO;
    }

    private String findOutcome(String stepsByOutcome) {
        Matcher findingOutcome = findingLifecycleOutcome().matcher(stepsByOutcome);
        if (findingOutcome.matches()) {
            return findingOutcome.group(1).trim();
        }
        return keywords().outcomeAny();
    }

    private Outcome parseOutcome(String outcomeAsText) {
        if (outcomeAsText.equals(keywords().outcomeSuccess())) {
            return Outcome.SUCCESS;
        }
        else if (outcomeAsText.equals(keywords().outcomeFailure())) {
            return Outcome.FAILURE;
        }
        return Outcome.ANY;
    }

    private String findFilters(String stepsByFilters) {
        Matcher findingFilters = findingLifecycleFilters().matcher(stepsByFilters.trim());
        if (findingFilters.matches()) {
            return findingFilters.group(1).trim();
        }
        return NONE;
    }

    private String parseFilters(String filtersAsText) {
        return removeStart(filtersAsText, keywords().metaFilter()).trim();
    }

    private List<Scenario> parseScenariosFrom(String storyAsText, ExamplesTable storyExamples) {
        List<Scenario> parsed = new ArrayList<>();
        for (String scenarioAsText : splitScenarios(storyAsText)) {
            parsed.add(parseScenario(scenarioAsText, storyExamples));
        }
        return parsed;
    }

    private List<String> splitScenarios(String storyAsText) {
        String scenarioKeyword = keywords().scenario();

        // use text after scenario keyword, if found
        if (StringUtils.contains(storyAsText, scenarioKeyword)) {
            storyAsText = StringUtils.substringAfter(storyAsText, scenarioKeyword);
        }

        return splitElements(storyAsText, scenarioKeyword);
    }

    private Scenario parseScenario(String scenarioAsText, ExamplesTable storyExamples) {
        String title = findScenarioTitle(scenarioAsText);
        String scenarioWithoutKeyword = removeStart(scenarioAsText, keywords().scenario()).trim();
        String scenarioWithoutTitle = removeStart(scenarioWithoutKeyword, title);
        scenarioWithoutTitle = startingWithNL(scenarioWithoutTitle);
        Meta meta = findScenarioMeta(scenarioWithoutTitle);
        String examplesTableAsString = findExamplesTable(scenarioWithoutTitle);
        ExamplesTable examplesTable = parseExamplesTable(examplesTableAsString, storyExamples);
        if(examplesTable == null) {
            examplesTable = ExamplesTable.EMPTY;
            meta = meta.inheritFrom(skipMeta);
        }
        GivenStories givenStories = findScenarioGivenStories(scenarioWithoutTitle);
        useExamplesTableForGivenStories(givenStories, examplesTable);
        List<String> steps = new ArrayList<>();
        if(examplesTableAsString.trim().isEmpty()) {
            steps.addAll(findSteps(scenarioWithoutTitle));
        } else {
            int afterExampleIndex = scenarioWithoutTitle.indexOf(examplesTableAsString) + examplesTableAsString.length();
            steps.addAll(findSteps(scenarioWithoutTitle.substring(0, afterExampleIndex)));
        }
        return new Scenario(title, meta, givenStories, examplesTable, steps);
    }

    private void useExamplesTableForGivenStories(GivenStories givenStories, ExamplesTable examplesTable) {
        if (givenStories.requireParameters()) {
            givenStories.useExamplesTable(examplesTable);
        }

    }

    private String findScenarioTitle(String scenarioAsText) {
        Matcher findingTitle = findingScenarioTitle().matcher(scenarioAsText);
        return findingTitle.find() ? findingTitle.group(1).trim() : NONE;
    }

    private Meta findScenarioMeta(String scenarioAsText) {
        Matcher findingMeta = findingScenarioMeta().matcher(scenarioAsText);
        if (findingMeta.matches()) {
            String meta = findingMeta.group(1).trim();
            return Meta.createMeta(meta, keywords());
        }
        return Meta.EMPTY;
    }

    private String findExamplesTable(String scenarioAsText) {
        Matcher findingTable = findingExamplesTable().matcher(scenarioAsText);
        return findingTable.find() ? findingTable.group(1).trim() : NONE;
    }

    private ExamplesTable parseExamplesTable(String tableInput, ExamplesTable storyExamples) {
        Matcher findingTableWithParams = compile("table:\\s*(.*)\\nparameters:\\s*(.*)", DOTALL).matcher(tableInput);
        List<String> examplesParameters = new ArrayList<String>();
        if (findingTableWithParams.find()) {
            tableInput = findingTableWithParams.group(1).trim();
            examplesParameters = splitExamplesParameters(findingTableWithParams.group(2).trim());
        }
        ExamplesTable examplesTable = tableFactory.createExamplesTable(tableInput);
        if (examplesTable.isEmpty()) {
            return examplesTable;
        }
        if (!examplesParameters.isEmpty()) {
            examplesTable = cutExamplesTable(examplesTable, examplesParameters);
        }
        if (examplesTable != null && skippedExample != null && storyExamples != null) {
            examplesTable = cutExamplesTableForScenario(examplesTable, storyExamples);
        }
        return examplesTable;
    }

    private ExamplesTable cutExamplesTableForScenario(ExamplesTable examplesTable, ExamplesTable storyExamples) {
        Pattern delimitedNamePattern = delimetedNamePattern();
        List<Map<String, String>> resultRows = new ArrayList<Map<String, String>>();
        for (Map<String, String> scenarioExamplesRow : examplesTable.getRows()) {
            if (!hasSkippedValues(scenarioExamplesRow, delimitedNamePattern, storyExamples)) {
                resultRows.add(scenarioExamplesRow);
            }
        }
        return createExamplesTable(examplesTable, resultRows);
    }

    private ExamplesTable createExamplesTable(ExamplesTable originalExamplesTable, List<Map<String, String>>
            resultRows) {
        TableTransformers tableTransformers = new TableTransformers();
        ParameterConverters parameterConverters = new ParameterConverters(new LoadFromClasspath(), parameterControls,
                tableTransformers, true);
        return !resultRows.isEmpty() ?
                new ExamplesTable("", originalExamplesTable.getHeaderSeparator(),
                        originalExamplesTable.getValueSeparator(), parameterConverters, parameterControls,
                        tableTransformers).withRows(resultRows) :
                null;
    }

    private boolean hasSkippedValues(Map<String, String> scenarioExamplesTableRow, Pattern delimitedNamePattern,
                                     ExamplesTable storyExamples) {
        for (String value : scenarioExamplesTableRow.values()) {
            if (isSkipped(value, delimitedNamePattern, storyExamples)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSkipped(String value, Pattern delimitedNamePattern, ExamplesTable storyExamples) {
        return skippedExample.equals(value) || isSkippedInStoryExamples(value, delimitedNamePattern, storyExamples);
    }

    private boolean isSkippedInStoryExamples(String value, Pattern delimitedNamePattern, ExamplesTable storyExamples) {
        Matcher matcher = delimitedNamePattern.matcher(value);
        if (matcher.find()) {
            String delimitedName = matcher.group(1);
            if (storyExamples.getHeaders().contains(delimitedName) && isStoryExamplesColumnSkipped(storyExamples,
                    delimitedName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStoryExamplesColumnSkipped(ExamplesTable storyExamples, String delimitedName) {
        int skippedRowsCount = 0;
        for (Map<String, String> storyRow : storyExamples.getRows()) {
            String storyValue = storyRow.get(delimitedName);
            if (skippedExample.equals(storyValue)) {
                ++skippedRowsCount;
            }
        }
        if (skippedRowsCount != 0) {
            if (skippedRowsCount != storyExamples.getRowCount()) {
                throw new ExamplesCutException("Scenario's Examples Table values should refer to story's Examples " +
                        "Table columns that contain either only normal parameters or only skipped");
            }
            return true;
        }
        return false;
    }

    private List<String> splitExamplesParameters(String examplesParameters) {
        List<String> splitExamplesParameters = new ArrayList<String>();
        for (String examplesParameter : examplesParameters.split(",")) {
            splitExamplesParameters.add(examplesParameter.trim());
        }
        return splitExamplesParameters;
    }

    private ExamplesTable cutExamplesTable(ExamplesTable examplesTable, List<String> examplesParameters) {
        List<String> headers = examplesTable.getHeaders();
        headers.removeAll(examplesParameters);
        List<Map<String, String>> resultRows = new ArrayList<Map<String, String>>();
        for (Map<String, String> examplesTableRow : examplesTable.getRows()) {
            for (String header : headers) {
                examplesTableRow.remove(header);
            }
            int skippedValuesInRow = 0;
            for (String value : examplesTableRow.values()) {
                if (skippedExample.equals(value)) {
                    ++skippedValuesInRow;
                }
            }
            if (skippedValuesInRow != examplesTableRow.size()) {
                resultRows.add(examplesTableRow);
            }
        }
        return createExamplesTable(examplesTable, resultRows);
    }

    private GivenStories findScenarioGivenStories(String scenarioAsText) {
        Matcher findingGivenStories = findingScenarioGivenStories().matcher(scenarioAsText);
        String givenStories = findingGivenStories.find() ? findingGivenStories.group(1).trim() : NONE;
        return new GivenStories(givenStories);
    }

    // Regex Patterns

    private Pattern findingDescription() {
        String metaOrNarrativeOrLifecycleOrScenario = concatenateWithOr(keywords().meta(), keywords().narrative(),
                keywords().lifecycle(), keywords().scenario());
        return compile("(.*?)(" + metaOrNarrativeOrLifecycleOrScenario + ").*", DOTALL);
    }

    private Pattern findingStoryMeta() {
        String narrativeOrLifecycleOrGivenStories = concatenateWithOr(keywords().narrative(), keywords().lifecycle(),
                keywords().givenStories());
        return compile(".*" + keywords().meta() + "(.*?)\\s*(\\Z|" + narrativeOrLifecycleOrGivenStories + ").*", DOTALL);
    }

    private Pattern findingNarrative() {
        String givenStoriesOrLifecycleOrScenario = concatenateWithOr(keywords().givenStories(), keywords().lifecycle(),
                keywords().scenario());
        return compile(".*" + keywords().narrative() + "(.*?)\\s*(" + givenStoriesOrLifecycleOrScenario + ").*", DOTALL);
    }

    private Pattern findingNarrativeElements() {
        return compile(".*" + keywords().inOrderTo() + "(.*)\\s*" + keywords().asA() + "(.*)\\s*" + keywords().iWantTo()
                + "(.*)", DOTALL);
    }

    private Pattern findingAlternativeNarrativeElements() {
        return compile(".*" + keywords().asA() + "(.*)\\s*" + keywords().iWantTo() + "(.*)\\s*" + keywords().soThat() + "(.*)",
                DOTALL);
    }

    private Pattern findingStoryGivenStories() {
        String lifecycleOrScenario = concatenateWithOr(keywords().lifecycle(), keywords().scenario());
        return compile(".*" + keywords().givenStories() + "(.*?)\\s*(\\Z|" + lifecycleOrScenario + ").*", DOTALL);
    }

    private Pattern findingLifecycle() {
        return compile(".*" + keywords().lifecycle() + "\\s*(.*)", DOTALL);
    }

    private Pattern findingLifecycleScope() {
        String startingWords = concatenateInitialStartingWords();
        return compile(keywords().scope() + "((.)*?)\\s*(" + keywords().outcome() + "|" + keywords().metaFilter() + "|"
                + startingWords + ").*", DOTALL);
    }

    private Pattern findingLifecycleOutcome() {
        String startingWords = concatenateInitialStartingWords();
        String outcomes = concatenateWithOr(keywords().outcomeAny(), keywords().outcomeSuccess(), keywords().outcomeFailure());
        return compile("\\s*(" + outcomes + ")\\s*(" + keywords().metaFilter() + "|" + startingWords + ").*", DOTALL);
    }

    private Pattern findingLifecycleFilters() {
        String startingWords = concatenateInitialStartingWords();
        String filters = concatenateWithOr(keywords().metaFilter());
        return compile("\\s*(" + filters + "[\\w\\+\\-\\_\\s]*)(" + startingWords + ").*", DOTALL);
    }

    private Pattern findingScenarioTitle() {
        String startingWords = concatenateInitialStartingWords();
        return compile(keywords().scenario() + "(.*?)\\s*(" + keywords().meta() + "|" + startingWords + "|$).*", DOTALL);
    }

    private Pattern findingScenarioMeta() {
        String startingWords = concatenateInitialStartingWords();
        return compile(".*" + keywords().meta() + "(.*?)\\s*(" + keywords().givenStories() + "|" + startingWords + "|$).*",
                DOTALL);
    }

    private Pattern findingScenarioGivenStories() {
        String startingWords = concatenateInitialStartingWords();
        return compile("\\n" + keywords().givenStories() + "((.|\\n)*?)\\s*(" + startingWords + ").*", DOTALL);
    }

    private Pattern delimetedNamePattern() {
        return Pattern.compile(parameterControls.nameDelimiterLeft() + "(\\w+?)"
                + parameterControls.nameDelimiterRight(), Pattern.DOTALL);
    }

    private Pattern findingExamplesTable() {
        return compile("\\n" + keywords().examplesTable() + "\\s*(.*?)(?:\\n" + keywords().ignorable() + ".*)?$", DOTALL);
    }

    public void setSkipMeta(Meta skipMeta) {
        this.skipMeta = skipMeta;
    }

    public void setSkippedExample(String skippedExample) {
        this.skippedExample = skippedExample;
    }
}
