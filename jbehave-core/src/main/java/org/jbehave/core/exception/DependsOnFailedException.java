package org.jbehave.core.exception;

public class DependsOnFailedException extends Exception {
    public DependsOnFailedException(String dependsOnId) {
        super(dependsOnId);
    }
}
