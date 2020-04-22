package io.eroshenkoam.allure;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static io.qameta.allure.Allure.step;

public class RestSteps {

    @When("I create note with content {string} via api")
    public void createNoteWithText(final String text) {
        step("POST /api/notes");
    }

    @When("I delete note with content {string} via api")
    public void deleteNoteWithText(final String text) {
        step("GET /api/notes?text=" + text);
        step("DELETE /api/notes/237");
    }

    @Then("I should see note with content {string} via api")
    public void notesShouldContainsNoteWithText(final String text) {
        step("GET /api/notes?text=" + text);
        step("GET /api/notes/834");
    }

    @Then("I should not see note with content {string} via api")
    public void notesShouldNotContainsNoteWithText(final String text) {
        step("GET /api/notes?text=" + text);
        step("GET /api/notes/834");
    }

}
