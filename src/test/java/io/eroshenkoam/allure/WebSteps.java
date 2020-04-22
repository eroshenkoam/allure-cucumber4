package io.eroshenkoam.allure;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.qameta.allure.Attachment;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
public class WebSteps {

    @When("^I open notes page$")
    public void openNotesPage() {
        attachPageSource();
        maybeThrowElementNotFoundException();
    }

    @And("I create note with content {string}")
    public void createNoteWithText(final String text) {
        maybeThrowElementNotFoundException();
    }

    @And("I delete note with content {string}")
    public void deleteNoteWithText(final String text) {
        maybeThrowAssertionException(text);
    }

    @Then("I should see note with content {string}")
    public void notesShouldContainsNoteWithText(final String text) {
        maybeThrowAssertionException(text);
    }

    @Then("I should not see note with content {string}")
    public void notesShouldNotContainsNoteWithText(final String text) {
        maybeThrowAssertionException(text);

    }

    @When("I open advertisement page {int}")
    public void openAdPage(final int id) {
        maybeThrowElementNotFoundException();
    }

    @And("I add note with content {string} to advertisement")
    public void addNoteToAdd(final String text) {
        maybeThrowElementNotFoundException();
    }

    @Attachment(value = "Page", type = "text/html", fileExtension = "html")
    public byte[] attachPageSource() {
        try {
            final InputStream stream = ClassLoader.getSystemResourceAsStream("index.html");
            return IOUtils.toString(stream, StandardCharsets.UTF_8).getBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void maybeThrowElementNotFoundException() {
        if (isTimeToThrowException()) {
            throw new RuntimeException("Element not found for xpath [//div[@class='something']]");
        }
    }

    private void maybeThrowAssertionException(String text) {
        if (isTimeToThrowException()) {
            assertEquals(text, "another text");
        }
    }

    private boolean isTimeToThrowException() {
        return new Random().nextBoolean()
                && new Random().nextBoolean()
                && new Random().nextBoolean()
                && new Random().nextBoolean();
    }
}
