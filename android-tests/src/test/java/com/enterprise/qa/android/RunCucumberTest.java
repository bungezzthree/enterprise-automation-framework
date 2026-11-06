package com.enterprise.qa.android;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Entry point for {@code mvn test} and for running from the IDE (right-click, Run). Cucumber options live in
 * junit-platform.properties. In the IDE choose where to run with VM options, e.g. {@code -Dmobile.mode=headless}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
public class RunCucumberTest {
}
