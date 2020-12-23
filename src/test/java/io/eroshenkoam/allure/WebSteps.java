package io.eroshenkoam.allure;

import gherkin.pickles.PickleTable;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.qameta.allure.Attachment;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.fail;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
public class WebSteps {

    @When("^I open labels page$")
    public void openLabelsPage() {
        attachPageSource();
        maybeThrowElementNotFoundException();
    }


    @When("I open milestones page")
    public void openMilestonesPage() {

    }

    @When("I open secret page")
    public void openSecretPage(final DataTable table) {

    }

    @And("I create label with title {string}")
    public void createLabelWithTitle(final String title) {
        maybeThrowElementNotFoundException();
    }

    @And("I create milestone with title {string}")
    public void createMilestoneWithTitle(String title) {

    }

    @And("I delete label with title {string}")
    public void deleteLabelWithTitle(final String title) {
        maybeThrowAssertionException(title);
    }

    @Then("I should see issue with label title {string}")
    public void labelsShouldContainsNoteWithText(final String title) {
        maybeThrowAssertionException(title);
    }

    @Then("I should not see note with content {string}")
    public void notesShouldNotContainsNoteWithText(final String text) {
        maybeThrowAssertionException(text);

    }

    @And("I delete milestone with title {string}")
    public void deleteMilestoneWithTitle(String title) {
    }

    @Then("I should see milestone with title {string}")
    public void shouldSeeMilestoneWithTitle(String title) {

    }

    @Then("I should not see milestone with content {string}")
    public void shouldNotSeeMilestoneWithContent(String title) {

    }

    @When("I open issue with id {int}")
    public void openIssuePage(final int id) {
        maybeThrowElementNotFoundException();
    }

    @And("I add label with title {string} to issue")
    public void addLabelToIssue(final String text) {
        maybeThrowElementNotFoundException();
    }

    @And("I filter issue by label title {string}")
    public void filterIssueByLabel(final String title) {
        attachPageSource();
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
            fail(elementNotFoundMessage("//div[@class='something']"));

            throw new RuntimeException("Element not found for xpath ");
        }
    }

    private void maybeThrowAssertionException(String text) {
        try {
            Thread.sleep(1000);
            if (isTimeToThrowException()) {
                fail(textEqual(text, "new text"));
            }
        } catch (InterruptedException e) {
            // do nothing test is dummy
        }
    }

    private boolean isTimeToThrowException() {
        return new Random().nextBoolean()
                && new Random().nextBoolean()
                && new Random().nextBoolean();
    }

    private String textEqual(final String expected, final String actual) {
        return String.format("Element should text '%s' {By.xpath: //a[@href='/eroshenkoam/allure-example']}\n" +
                "Element: '<a class=\"v-align-middle\">%s</a>'\n" +
                "Screenshot: file:/Users/eroshenkoam/Developer/eroshenkoam/webdriver-coverage-example/build/reports/tests/1603973703632.0.png\n" +
                "Page source: file:/Users/eroshenkoam/Developer/eroshenkoam/webdriver-coverage-example/build/reports/tests/1603973703632.0.html\n" +
                "Timeout: 4 s.\n", expected, actual);
    }

    private String elementNotFoundMessage(String selector) {
        return String.format("Element not found {By.xpath: %s}\n" +
                "Expected: visible or transparent: visible or have css value opacity=0\n" +
                "Screenshot: file:/Users/eroshenkoam/Developer/eroshenkoam/webdriver-coverage-example/build/reports/tests/1603973516437.0.png\n" +
                "Page source: file:/Users/eroshenkoam/Developer/eroshenkoam/webdriver-coverage-example/build/reports/tests/1603973516437.0.html\n" +
                "Timeout: 4 s.\n", selector);
    }
}
