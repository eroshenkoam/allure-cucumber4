#language: en
@allure.label.layer:api
@allure.label.owner:eroshenkoam
Feature: Notes

  @smoke
  Scenario: Creating note via api
    When I create note with content "hello" via api
    Then I should see note with content "hello" via api

  @regress
  @allure.label.jira:AE-1
  Scenario: Deleting note via api
    When I create note with content "hello"
    And I delete note with content "hello" via api
    Then I should not see note with content "hello" via api
