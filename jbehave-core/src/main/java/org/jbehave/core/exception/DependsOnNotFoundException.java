package org.jbehave.core.exception;

public class DependsOnNotFoundException extends Exception {
    public DependsOnNotFoundException(String dependsOnId) {
        super(dependsOnId);
    }
}
