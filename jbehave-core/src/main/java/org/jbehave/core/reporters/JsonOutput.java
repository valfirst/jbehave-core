package org.jbehave.core.reporters;

import static org.jbehave.core.model.ExamplesTableProperties.getDefaultHeaderSeparator;
import static org.jbehave.core.model.ExamplesTableProperties.getDefaultValueSeparator;
import static org.jbehave.core.model.ExamplesTableProperties.getHeaderSeparatorKey;
import static org.jbehave.core.model.ExamplesTableProperties.getValueSeparatorKey;
import static org.jbehave.core.reporters.PrintStreamOutput.Format.JSON;
import static org.jbehave.core.steps.StepCreator.PARAMETER_TABLE_END;
import static org.jbehave.core.steps.StepCreator.PARAMETER_TABLE_START;
import static org.jbehave.core.steps.StepCreator.PARAMETER_VALUE_END;
import static org.jbehave.core.steps.StepCreator.PARAMETER_VALUE_NEWLINE;
import static org.jbehave.core.steps.StepCreator.PARAMETER_VALUE_START;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jbehave.core.configuration.Keywords;
import org.jbehave.core.model.OutcomesTable;

/**
 * <p>
 * Story reporter that outputs to a PrintStream, as JSON. It extends
 * {@link PrintStreamOutput}, providing JSON-based default output
 * patterns, which can be overridden via the {@link
 * JsonOutput (PrintStream,Properties)} constructor.
 * </p>
 *
 * @author Valery Yatsynovich
 */
public class JsonOutput extends PrintStreamOutput {

    private static final String STEP_EXAMPLES_TABLE_REGEX = String.format(".*%s\\{(.*(?<=%s|%s).*)\\}\\n(.+)%s$",
            PARAMETER_TABLE_START, getHeaderSeparatorKey(), getValueSeparatorKey(), PARAMETER_TABLE_END);
    private static final Pattern STEP_EXAMPLES_TABLE_PATTERN = Pattern.compile(STEP_EXAMPLES_TABLE_REGEX,
            Pattern.DOTALL);
    private static final int INLINE_PROPERTIES_GROUP = 1;
    private static final int EXAMPLES_TABLE_GROUP = 2;

    private static final char JSON_DOCUMENT_START = 0;
    private static final char JSON_OBJECT_START = '{';
    private static final char JSON_ARRAY_START = '[';
    private static final char[] JSON_START_CHARS = { JSON_DOCUMENT_START, JSON_OBJECT_START, JSON_ARRAY_START };

    private static final String[] STEP_KEYS = { "successful", "ignorable", "comment", "pending", "notPerformed",
            "failed", "restarted" };

    private static final String[] PARAMETER_KEYS = { PARAMETER_TABLE_START, PARAMETER_TABLE_END, PARAMETER_VALUE_START,
            PARAMETER_VALUE_END, PARAMETER_VALUE_NEWLINE, "parameterValueStart", "parameterValueEnd",
            "parameterValueNewline" };

    private char lastChar = JSON_DOCUMENT_START;
    private char newLine = '\n';

    private int givenStoriesLevel = 0;
    private int storyPublishingLevel = 0;
    private AtomicInteger subStepsLevel = new AtomicInteger();
    private final Map<Integer, Boolean> scenarioPublishingPerLevels = new HashMap<>();
    private boolean scenarioCompleted = false;
    private boolean stepPublishing = false;

    public JsonOutput(PrintStream output, Keywords keywords) {
        this(output, new Properties(), keywords);
    }

    public JsonOutput(PrintStream output, Properties outputPatterns, Keywords keywords) {
        super(JSON, output, defaultXmlPatterns(), outputPatterns, keywords);
    }

    @Override
    protected void print(PrintStream output, String text) {
        if (!text.isEmpty()) {
            boolean doNotAddComma =
                    ArrayUtils.contains(JSON_START_CHARS, lastChar) || StringUtils.startsWithAny(text, "}", "]");
            super.print(output, doNotAddComma ? text : "," + text);
            lastChar = text.charAt(text.length() - 1);
        }
    }

    @Override
    public void beforeStep(String step) {
        if (isTopLevelStep(false)) {
            printSubSteps();
        } else {
            subStepsLevel.incrementAndGet();
        }
        super.beforeStep(step);
    }

    @Override
    public void successful(String step) {
        printSubStepsBeforeStepOutcome();
        if (isTopLevelStep(true)) {
            super.successful(processStepExamplesTable(step));
            return;
        }
        subStepsLevel.decrementAndGet();
    }

    @Override
    public void pending(String step) {
        boolean topLevelStep = isTopLevelStep(false);
        printSubStepsBeforeStepOutcome();
        if (topLevelStep) {
            super.pending(step);
        }
    }

    @Override
    public void failed(String step, Throwable storyFailure) {
        printSubStepsBeforeStepOutcome();
        if (isTopLevelStep(true)) {
            super.failed(step, storyFailure);
            return;
        }
        subStepsLevel.decrementAndGet();
    }

    @Override
    public void failedOutcomes(String step, OutcomesTable table) {
        printSubStepsBeforeStepOutcome();
        if (isTopLevelStep(true)) {
            super.failedOutcomes(step, table);
            return;
        }
        subStepsLevel.decrementAndGet();
    }

    @Override
    public void ignorable(String step) {
        boolean topLevelStep = isTopLevelStep(false);
        printSubStepsBeforeStepOutcome();
        if (topLevelStep) {
            super.ignorable(step);
        }
    }

    @Override
    public void comment(String step) {
        boolean topLevelStep = isTopLevelStep(false);
        printSubStepsBeforeStepOutcome();
        if (topLevelStep) {
            super.comment(step);
        }
    }

    @Override
    public void notPerformed(String step) {
        boolean topLevelStep = isTopLevelStep(false);
        printSubStepsBeforeStepOutcome();
        if (topLevelStep) {
            super.notPerformed(step);
        }
    }

    @Override
    public void restarted(String step, Throwable cause) {
        printSubStepsBeforeStepOutcome();
        if (isTopLevelStep(true)) {
            super.restarted(step, cause);
            return;
        }
        subStepsLevel.decrementAndGet();
    }

    @Override
    protected String format(String key, String defaultPattern, Object... args) {
        if ("beforeGivenStories".equals(key)) {
            givenStoriesLevel++;
            scenarioCompleted = false;
        } else if ("afterGivenStories".equals(key)) {
            if (storyPublishingLevel == givenStoriesLevel) {
                // Closing given "stories"
                print("]");
                storyPublishingLevel--;
            }
            givenStoriesLevel--;
            return super.format(key, defaultPattern, args);
        } else if ("beforeStory".equals(key) && storyPublishingLevel < givenStoriesLevel) {
            // Starting given "stories"
            print("\"stories\": [");
            storyPublishingLevel ++;
        }
        //Closing "examples" if "steps" are empty
        if ("afterExamples".equals(key) && !stepPublishing) {
            print("}");
        }
        if (stepPublishing) {
            if ("example".equals(key) || "afterExamples".equals(key)) {
                // Closing previous "example"
                print("]}");
                stepPublishing = false;
            }
            if ("pendingMethodsStart".equals(key)){
                // Closing "steps" for scenario
                print("]");
                stepPublishing = false;
            }
            if ( "afterScenario".equals(key) || "afterScenarioWithFailure".equals(key)) {
                // Closing "steps" for scenario
                print("]");
                stepPublishing = false;
                scenarioCompleted = true;
            }
            if ("subSteps".equals(key)) {
                subStepsLevel.incrementAndGet();
            }
            if (ArrayUtils.contains(STEP_KEYS, key)) {
                // Closing "steps" for step
                print("]");
                subStepsLevel.decrementAndGet();
            }
        }
        else if ("subSteps".equals(key)) {
            // Starting "steps"
            print("\"steps\": [");
            subStepsLevel.incrementAndGet();
            stepPublishing = true;
        }
        else if ("beforeScenario".equals(key)) {
            scenarioCompleted = false;
            if (scenarioPublishingPerLevels.get(storyPublishingLevel) != Boolean.TRUE) {
                // Starting "scenarios"
                print("\"scenarios\": [");
                scenarioPublishingPerLevels.put(storyPublishingLevel, Boolean.TRUE);
            }
        } else if ("afterScenario".equals(key) || "afterScenarioWithFailure".equals(key)) {
            // Need to complete scenario with examples
            scenarioCompleted = true;
        } else if (scenarioPublishingPerLevels.get(storyPublishingLevel) == Boolean.TRUE && scenarioCompleted && !ArrayUtils.contains(PARAMETER_KEYS, key)) {
            // Closing "scenarios"
            scenarioPublishingPerLevels.put(storyPublishingLevel, Boolean.FALSE);
            print("]");
        }
        return super.format(key, defaultPattern, args);
    }

    @Override
    protected String transformPrintingTable(String text, String tableStart, String tableEnd) {
        return text;
    }

    private static Properties defaultXmlPatterns() {
        Properties patterns = new Properties();
        patterns.setProperty("dryRun", "\"dryRun\": \"{0}\"");
        patterns.setProperty("beforeStory", "'{'\"path\": \"{1}\", \"title\": \"{0}\"");
        patterns.setProperty("storyCancelled", "'{'\"cancelled\": '{'\"keyword\": \"{0}\", \"durationKeyword\": \"{1}\", \"durationInSecs\": \"{2}\"}}");
        patterns.setProperty("afterStory", "}");
        patterns.setProperty("pendingMethodsStart", "\"pendingMethods\": [");
        patterns.setProperty("pendingMethod", "\"{0}\"");
        patterns.setProperty("pendingMethodsEnd", "]");
        patterns.setProperty("metaStart", "\"meta\": '['");
        patterns.setProperty("metaProperty", "'{'\"keyword\": \"{0}\", \"name\": \"{1}\", \"value\": \"{2}\"}");
        patterns.setProperty("metaEnd", "']'");
        patterns.setProperty("filter", "\"filter\": \"{0}\"");
        patterns.setProperty("narrative", "\"narrative\": '{'\"keyword\": \"{0}\",  \"inOrderTo\": '{'\"keyword\": \"{1}\", \"value\": \"{2}\"}, \"asA\": '{'\"keyword\": \"{3}\", \"value\": \"{4}\"}, \"iWantTo\": '{'\"keyword\": \"{5}\", \"value\": \"{6}\"}}");
        patterns.setProperty("lifecycleStart", "\"lifecycle\": '{'\"keyword\": \"{0}\"");
        patterns.setProperty("lifecycleEnd", "}");
        patterns.setProperty("lifecycleBeforeStart", "\"before\": '{'\"keyword\": \"{0}\"");
        patterns.setProperty("lifecycleBeforeEnd", "}");
        patterns.setProperty("lifecycleAfterStart", "\"after\": '{'\"keyword\": \"{0}\"");
        patterns.setProperty("lifecycleAfterEnd", "}");
        patterns.setProperty("lifecycleScopeStart", "\"scope\": '{'\"keyword\": \"{0}\", \"value\": \"{1}\"");
        patterns.setProperty("lifecycleScopeEnd", "}");
        patterns.setProperty("lifecycleOutcomeStart", "\"outcome\": '{'\"keyword\": \"{0}\", \"value\": \"{1}\"");
        patterns.setProperty("lifecycleOutcomeEnd", "}");
        patterns.setProperty("lifecycleMetaFilter", "\"metaFilter\": \"{0} {1}\"");
        patterns.setProperty("lifecycleStep", "\"step\": \"{0}\"");
        patterns.setProperty("beforeScenario","'{'\"keyword\": \"{0}\", \"title\": \"{1}\"");
        patterns.setProperty("scenarioNotAllowed", "\"notAllowed\": '{'\"pattern\": \"{0}\"}");
        patterns.setProperty("afterScenario", "}");
        patterns.setProperty("afterScenarioWithFailure", "\"failure\": \"{0}\" }");
        patterns.setProperty("givenStories", "\"givenStories\": '{'\"keyword\": \"{0}\", \"paths\": \"{1}\"}");
        patterns.setProperty("beforeGivenStories", "\"givenStories\": '{'");
        patterns.setProperty("givenStoriesStart", "\"keyword\": \"{0}\", \"givenStories\":[");
        patterns.setProperty("givenStory", "'{'\"parameters\": \"{1}\", \"path\": \"{0}\"}");
        patterns.setProperty("givenStoriesEnd", "]");
        patterns.setProperty("afterGivenStories", "}");
        patterns.setProperty("subSteps", "'{'\"steps\": [");
        patterns.setProperty("successful", "\"outcome\": \"successful\", \"value\": \"{0}\"}");
        patterns.setProperty("ignorable", "\"outcome\": \"ignorable\", \"value\": \"{0}\"}");
        patterns.setProperty("comment", "\"comment\": \"ignorable\", \"value\": \"{0}\"}");
        patterns.setProperty("pending", "\"outcome\": \"pending\", \"keyword\": \"{1}\", \"value\": \"{0}\"}");
        patterns.setProperty("notPerformed", "\"outcome\": \"notPerformed\", \"keyword\": \"{1}\", \"value\": \"{0}\"}");
        patterns.setProperty("failed", "\"outcome\": \"failed\", \"keyword\": \"{1}\", \"value\": \"{0}\", \"failure\": \"{2}\"}");
        patterns.setProperty("restarted", "\"outcome\": \"restarted\", \"value\": \"{0}\", \"reason\": \"{1}\"}");
        patterns.setProperty("restartedStory", "'{'\"story\": '{'\"outcome\": \"restartedStory\", \"value\": \"{0}\", \"reason\": \"{1}\"}}");
        patterns.setProperty("outcomesTableStart", "'{'\"outcomes\": '{'");
        patterns.setProperty("outcomesTableHeadStart", "\"fields\": [");
        patterns.setProperty("outcomesTableHeadCell", "\"{0}\"");
        patterns.setProperty("outcomesTableHeadEnd", "]");
        patterns.setProperty("outcomesTableBodyStart", "\"values\": [");
        patterns.setProperty("outcomesTableRowStart", "[");
        patterns.setProperty("outcomesTableCell", "\"{0}\"");
        patterns.setProperty("outcomesTableRowEnd", "]");
        patterns.setProperty("outcomesTableBodyEnd", "]");
        patterns.setProperty("outcomesTableEnd", "}}");
        patterns.setProperty("beforeExamples", "\"examples\": '{'\"keyword\": \"{0}\"");
        patterns.setProperty("examplesStepsStart", "\"steps\": [");
        patterns.setProperty("examplesStep", "\"{0}\"");
        patterns.setProperty("examplesStepsEnd", "]");
        patterns.setProperty("afterExamples", "]}");
        patterns.setProperty("examplesTableStart", "\"parameters\": '{'");
        patterns.setProperty("examplesTableHeadStart", "\"names\": [");
        patterns.setProperty("examplesTableHeadCell", "\"{0}\"");
        patterns.setProperty("examplesTableHeadEnd", "]");
        patterns.setProperty("examplesTableBodyStart", "\"values\": [");
        patterns.setProperty("examplesTableRowStart", "[");
        patterns.setProperty("examplesTableCell", "\"{0}\"");
        patterns.setProperty("examplesTableRowEnd", "]");
        patterns.setProperty("examplesTableBodyEnd", "]");
        patterns.setProperty("examplesTableEnd", "}, \"examples\": [");
        patterns.setProperty("example", "'{'\"keyword\": \"{0}\", \"value\": \"{1}\"");
        patterns.setProperty("parameterValueStart", "((");
        patterns.setProperty("parameterValueEnd", "))");
        patterns.setProperty("parameterValueNewline", "\\n");
        return patterns;
    }

    private boolean isTopLevelStep(boolean stepWithBeforeAction) {
         int expectedLevel = stepWithBeforeAction ? 1 : 0;
         return subStepsLevel.get() <= expectedLevel;
    }

    private void printSubSteps() {
        print(format("subSteps", ""));
    }

    private void printSubStepsBeforeStepOutcome() {
        if (subStepsLevel.get() == 0) {
            printSubSteps();
        }
    }

    private String processStepExamplesTable(String stepAsString) {
        Matcher stepExampleTableMatcher = STEP_EXAMPLES_TABLE_PATTERN.matcher(stepAsString);
        if(stepExampleTableMatcher.matches()) {
            String examplesTableProperties = stepExampleTableMatcher.group(INLINE_PROPERTIES_GROUP);
            String headerSeparator = getPropertyValue(examplesTableProperties, getHeaderSeparatorKey());
            String valueSeparator = getPropertyValue(examplesTableProperties, getValueSeparatorKey());

            String examplesTable = stepExampleTableMatcher.group(EXAMPLES_TABLE_GROUP);

            return stepAsString.substring(0, stepExampleTableMatcher.start(1) - 2)
                    .concat(transformExamplesTable(examplesTable, headerSeparator, valueSeparator));
        }
        return stepAsString;
    }

    private String getPropertyValue(String examplesTableProperties, String key) {
        String [] properties = examplesTableProperties.split(",");
        for(String property : properties) {
            property = property.replaceAll("\\s", "");
            String[] splittedProperty = property.split("=");
            if(splittedProperty[0].equals(key)) {
                return splittedProperty[1].trim();
            }
        }
        return null;
    }

    private String transformExamplesTable(String examplesTable, String headerSeparator, String valueSeparator) {
        StringBuilder examplesTableBuilder = new StringBuilder();
        String defaultValueSeparator = getDefaultValueSeparator();

        String [] rows = examplesTable.split(createRowSeparatorRegex(headerSeparator, valueSeparator));
        for(int index = 0; index < rows.length; index++) {
            String row = rows[index];
            String rowToAdd;
            if(index == 0) {
                String defaultHeaderSeparator = getDefaultHeaderSeparator();
                rowToAdd = isAdjustingReuired(headerSeparator, defaultHeaderSeparator) ?
                        adjustSeparators(row, headerSeparator, defaultHeaderSeparator) : row;
            } else {
                rowToAdd = isAdjustingReuired(valueSeparator, defaultValueSeparator) ?
                        adjustSeparators(row, valueSeparator, defaultValueSeparator) : row;
            }
            examplesTableBuilder.append(rowToAdd);
            if(index != rows.length - 1) {
                examplesTableBuilder.append(newLine);
            }
        }

        return examplesTableBuilder.toString();
    }

    private boolean isAdjustingReuired(String separator, String defaultSeparator)
    {
        return separator != null && !separator.equals(defaultSeparator);
    }

    private String adjustSeparators(String row, String currentSeparator, String defaultSeparator) {
        return row.replaceAll(escape(defaultSeparator), "\\\\" + defaultSeparator)
                .replaceAll(escape(currentSeparator) , defaultSeparator);
    }

    private String createRowSeparatorRegex(String headerSeparator, String valueSeparator) {
        return new StringBuilder("(?<=")
                .append(escape(headerSeparator != null ? headerSeparator : getDefaultHeaderSeparator()))
                .append("|")
                .append(escape(valueSeparator != null ? valueSeparator : getDefaultValueSeparator()))
                .append(")")
                .append(newLine)
                .toString();
    }

    private String escape(String value) {
        return "\\".concat(value);
    }
}
