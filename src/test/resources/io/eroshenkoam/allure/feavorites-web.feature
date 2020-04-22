#language: en
@allure.label.layer:web
@allure.label.owner:baev
Feature: Favorites

  @allure.id:123
  @smoke @regress
  @allure.label.jira:AE-1,AE-2
  Scenario: Adding to favorites for authorized user
    When I open notes page
    And I create note with content "hello"
    Then I should see note with content "hello"

  @regress
  @allure.label.jira:AE-2
  Scenario: Removing from favorites for authorized user
    When I open notes page
    And I create note with content "hello"
    And I delete note with content "hello"
    Then I should not see note with content "hello"
