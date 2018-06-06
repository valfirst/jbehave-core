package org.jbehave.core.reporters;

import static org.jbehave.core.reporters.PrintStreamOutput.Format.JSON;
import static org.jbehave.core.steps.StepCreator.PARAMETER_TABLE_END;
import static org.jbehave.core.steps.StepCreator.PARAMETER_TABLE_START;
import static org.jbehave.core.steps.StepCreator.PARAMETER_VALUE_END;
import static org.jbehave.core.steps.StepCreator.PARAMETER_VALUE_NEWLINE;
import static org.jbehave.core.steps.StepCreator.PARAMETER_VALUE_START;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jbehave.core.configuration.Keywords;
import org.jbehave.core.model.StepWithOutcome;

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

    private int givenStoriesLevel = 0;
    private int storyPublishingLevel = 0;
    private final Map<Integer, Boolean> scenarioPublishingPerLevels = new HashMap<>();
    private boolean scenarioCompleted = false;
    private boolean stepPublishing = false;
    private String mainStepUuid;

    private final Map<String, StepWithOutcome> storage = new ConcurrentHashMap<>();


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
        final String uuid = UUID.randomUUID().toString();
        StepWithOutcome currentStepWithOutcome = new StepWithOutcome();
        currentStepWithOutcome.setUuid(uuid);
        currentStepWithOutcome.setTitle(step);
        currentStepWithOutcome.setSteps(new ArrayList<StepWithOutcome>());
        if (storage.isEmpty())
        {
            mainStepUuid = UUID.randomUUID().toString();
        }
        else
        {
            currentStepWithOutcome.setParentUuid(mainStepUuid);
            getMainStep().addStep(currentStepWithOutcome);
        }
        storage.put(step, currentStepWithOutcome);
    }

    @Override
    public void successful(String step)
    {
        StepWithOutcome stepWithOutcome = getCurrentStep(step, "successful");

        // for parametrized steps
        stepWithOutcome.setTitle(step);
        printSteps(stepWithOutcome);
    }

    @Override
    public void pending(String step) {
        printSteps(getCurrentStep(step, "pending"));
    }

    @Override
    public void failed(String step, Throwable storyFailure) {
        StepWithOutcome stepWithOutcome = getCurrentStep(step, "failed");
        stepWithOutcome.setStoryFailure(storyFailure);

        // for parametrized steps
        stepWithOutcome.setTitle(step);
        printSteps(stepWithOutcome);
    }

    @Override
    public void ignorable(String step) {
        printSteps(getCurrentStep(step, "ignorable"));
    }

    @Override
    public void comment(String step) {
        printSteps(getCurrentStep(step, "comment"));
    }

    @Override
    public void notPerformed(String step) {
        printSteps(getCurrentStep(step, "notPerformed"));
    }

    @Override
    public void restarted(String step, Throwable cause) {
        StepWithOutcome stepWithOutcome = getCurrentStep(step, "restarted");
        stepWithOutcome.setCause(cause);
        printSteps(stepWithOutcome);
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
            if (ArrayUtils.contains(STEP_KEYS, key)) {
                // Closing "steps" for step
                print("]");
            }
        }
        else if ("subSteps".equals(key)) {
            // Starting "steps"
            print("\"steps\": [");
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

    private void printSubSteps() {
        print(format("subSteps", ""));
    }

    private StepWithOutcome getMainStep() {
        for (StepWithOutcome stepWithOutcome : storage.values()) {
            if (stepWithOutcome.getParentUuid() == null) {
                return stepWithOutcome;
            }
        }
        return null;
    }

    private StepWithOutcome getCurrentStep(String step, String outcome) {
        StepWithOutcome stepWithOutcome = getStepFromStorage(step);
        if (stepWithOutcome == null) {
            stepWithOutcome = createNewStepWithOutCome(step, outcome);
            addToMainSteps(stepWithOutcome);
        }
        stepWithOutcome.setOutcome(outcome);
        return stepWithOutcome;
    }

    private StepWithOutcome getStepFromStorage(String stepTitle){
        for(StepWithOutcome step: storage.values()) {
            if(areStepsEqual(step.getTitle(), getStepWithParameters(stepTitle))) {
                return step;
            }
        }
        return null;
    }

    private StepWithOutcome createNewStepWithOutCome(String step, String outcome) {
        StepWithOutcome stepWithOutcome = new StepWithOutcome();
        final String uuid = UUID.randomUUID().toString();
        stepWithOutcome.setParentUuid(mainStepUuid);
        stepWithOutcome.setUuid(uuid);
        stepWithOutcome.setTitle(step);
        stepWithOutcome.setOutcome(outcome);
        stepWithOutcome.setSteps(new ArrayList<StepWithOutcome>());

        return stepWithOutcome;
    }

    private void printOutcome(StepWithOutcome stepWithOutcome) {
        switch (stepWithOutcome.getOutcome()) {
            case "successful":
                super.successful(stepWithOutcome.getTitle());
                break;
            case "pending":
                super.pending(stepWithOutcome.getTitle());
                break;
            case "failed":
                super.failed(stepWithOutcome.getTitle(), stepWithOutcome.getStoryFailure());
                break;
            case "ignorable":
                super.ignorable(stepWithOutcome.getTitle());
                break;
            case "comment":
                super.comment(stepWithOutcome.getTitle());
                break;
            case "notPerformed":
                super.notPerformed(stepWithOutcome.getTitle());
                break;
            case "restarted":
                super.restarted(stepWithOutcome.getTitle(), stepWithOutcome.getCause());
                break;
            default:
                break;
        }
    }

    private void printSteps(StepWithOutcome stepWithOutcome)
    {
        if (stepWithOutcome.getParentUuid() == null)
        {
            printSubSteps();
            for (StepWithOutcome step : stepWithOutcome.getSteps())
            {
                if (step.getOutcome() != null)
                {
                    printSubSteps();
                    printOutcome(step);
                }
            }
            printOutcome(stepWithOutcome);
            storage.clear();
            mainStepUuid = null;
        }
    }

    private String getStepWithParameters(String step)
    {
        String finalStep = step;
        for (String PARAMETER_KEY : PARAMETER_KEYS) {
            if(!PARAMETER_KEY.equals(PARAMETER_VALUE_START) && !PARAMETER_KEY.equals(PARAMETER_VALUE_END)) {
                finalStep = finalStep.replaceAll(PARAMETER_KEY, StringUtils.EMPTY);
            }
        }
        return finalStep;
    }

    private boolean areStepsEqual(String originalStep, String parametrizedStep) {
        return originalStep.replaceAll("[<'\uFF5F].*?['>\uFF60]", StringUtils.EMPTY)
                .equals(parametrizedStep.replaceAll("[<'\uFF5F].*?['>\uFF60]", StringUtils.EMPTY));
    }

    private void addToMainSteps(StepWithOutcome stepWithOutcome){
        StepWithOutcome mainStep = getMainStep();
        if (mainStep != null)
        {
            mainStep.addStep(stepWithOutcome);
        }
    }
}
