#language: en
@allure.label.layer:web
@allure.label.owner:eroshenkoam
Feature: Notes

  @regress @critical
  @allure.label.jira:AE-2
  Scenario: Creating note
    When I open notes page
    And I create note with content "hello"
    Then I should see note with content "hello"

  @smoke
  @allure.label.jira:AE-1
  Scenario: Adding note to advertisement
    When I open advertisement page 123123
    And I add note with content "hello" to advertisement
    And I open notes page
    Then I should see note with content "hello"

  @smoke
  @allure.label.jira:AE-1
  Scenario: Deleting note for authorized user
    When I open notes page
    And I create note with content "hello"
    And I delete note with content "hello"
    Then I should not see note with content "hello"
