package com.enterprise.qa.core.testdesk;

import java.nio.file.Path;
import java.util.List;

/**
 * Everything the TestDesk reporter needs to know about one finished scenario.
 * Plain data (no Cucumber types) so payload building can be unit tested.
 *
 * @param key        the {@code @tc:<ID>} value; also the TestDesk {@code automation_ref}
 * @param feature    readable feature name taken from the file name, used as the suite of newly created test cases
 * @param status     Cucumber status name: PASSED, FAILED, AMBIGUOUS, UNDEFINED, PENDING, SKIPPED, UNUSED
 * @param tags       all scenario tags including the leading {@code @}
 * @param message    short error message, or null
 * @param output     stack trace and console output, or null
 */
public record ScenarioRecord(String key, String name, String feature, List<String> tags, List<StepText> steps, String status,
                             long durationMs, String message, String output, List<Evidence> evidence) {

    /** One Gherkin step: keyword such as {@code "Given "} and the step text. */
    public record StepText(String keyword, String text) {
    }

    /** A file attached to the scenario (screenshot, API exchange, ...), stored on disk until it is uploaded. */
    public record Evidence(String fileName, String mediaType, Path file) {
    }
}
