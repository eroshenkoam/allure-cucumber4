#language: en
@allure.label.layer:web
@allure.label.owner:baev
Feature: Milestones

  @smoke @regress
  @allure.label.jira:AE-1
  @allure.label.jira:AE-2
  @allure.label.msrv:Billing
  Scenario: Create new milestone for authorized user
    When I open milestones page
    And I create milestone with title "hello"
    Then I should see milestone with title "hello"

  @regress
  @allure.label.jira:AE-2
  @allure.label.msrv:Repository
  Scenario: Close existing milestone for authorized user
    When I open secret page
      | The Devil in the White City          | Erik Larson |
      | The Lion, the Witch and the Wardrobe | C.S. Lewis  |
      | In the Garden of Beasts              | Erik Larson |
    And I create milestone with title "hello"
    And I delete milestone with title "hello"
    Then I should not see milestone with content "hello"
