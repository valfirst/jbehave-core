package org.jbehave.core.parsers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jbehave.core.configuration.Keywords;
import org.jbehave.core.i18n.LocalizedKeywords;

import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;

abstract class AbstractRegexParser {

    protected static final String NONE = "";
    private static final String CRLF = "\\r?\\n";

    private final Keywords keywords;
    private Pattern stepSkipPattern;

    protected AbstractRegexParser() {
        this(new LocalizedKeywords());
    }

    protected AbstractRegexParser(Keywords keywords) {
        this.keywords = keywords;
    }

    protected Keywords keywords() {
        return keywords;
    }

    protected List<String> splitElements(String text, String keyword) {
        List<String> elements = new ArrayList<>();
        StringBuilder element = new StringBuilder();
        String[] elementsAsText = text.split(keyword);
        for (int i = 0; i < elementsAsText.length; i++)
        {
            String elementAsText = elementsAsText[i];
            element.append(elementAsText);
            if (isLastLineNotComment(elementAsText)) {
                addNonEmptyElement(element.toString(), elements, keyword);
                element = new StringBuilder();
            } else {
                if (i == elementsAsText.length - 1) {
                    addNonEmptyElement(element.toString(), elements, keyword);
                } else {
                    element.append(keyword);
                }
            }
        }
        return elements;
    }

    private static void addNonEmptyElement(String elementToAdd, List<String> elements, String keyword) {
        if (elementToAdd.trim().length() > 0) {
            elements.add(keyword + "\n" + elementToAdd);
        }
    }

    private boolean isLastLineNotComment(String elementAsText) {
        String[] elementLines = elementAsText.split(CRLF, -1);
        return !elementLines[elementLines.length - 1].startsWith(keywords.ignorable());
    }

    protected String startingWithNL(String text) {
        if ( !text.startsWith("\n") ){ // always ensure starts with newline
            return "\n" + text;
        }
        return text;
    }

    protected List<String> findSteps(String stepsAsText) {
        Matcher matcher = findingSteps().matcher(stepsAsText);
        List<String> steps = new ArrayList<>();
        int startAt = 0;
        while (matcher.find(startAt)) {
            String step = StringUtils.substringAfter(matcher.group(1), "\n");
            startAt = matcher.start(4);
            if (stepSkipPattern!=null) {
                Matcher stepSkipMatcher = stepSkipPattern.matcher(step);
                if (stepSkipMatcher.find()) {
                    continue;
                }
            }
            steps.add(step);
        }
        return steps;
    }

    // Regex Patterns

    private Pattern findingSteps() {
        String initialStartingWords = concatenateInitialStartingWords();
        String followingStartingWords = concatenateFollowingStartingWords();
        return compile(
                "((" + initialStartingWords + ")\\s(.*?))(\\Z|" + followingStartingWords + "|\\n"
                        + keywords().examplesTable() + ")", DOTALL);
    }

    protected String concatenateInitialStartingWords() {
        return concatenateStartingWords("");
    }

    protected String concatenateFollowingStartingWords() {
        return concatenateStartingWords("\\s");
    }

    private String concatenateStartingWords(String afterKeyword) {
        return concatenateWithOr(CRLF, afterKeyword, keywords().startingWords());
    }

    protected String concatenateWithOr(String... keywords) {
        return concatenateWithOr(null, null, keywords);
    }

    private String concatenateWithOr(String beforeKeyword, String afterKeyword, String[] keywords) {
        String before = beforeKeyword != null ? beforeKeyword : NONE;
        String after = afterKeyword != null ? afterKeyword : NONE;
        StringBuilder builder = new StringBuilder(before).append("(?:");
        for (String keyword : keywords) {
            builder.append(keyword).append('|');
        }
        if (keywords.length > 0) {
            builder.deleteCharAt(builder.length() - 1); // remove last "|"
        }
        return builder.append(')').append(after).toString();
    }

    public void setStepSkipPattern(Pattern stepSkipPattern) {
        this.stepSkipPattern = stepSkipPattern;
    }
}
