#language: en
@allure.label.layer:rest
@allure.label.owner:eroshenkoam
Feature: Labels

  @smoke
  @allure.label.msrv:Billing
  Scenario: Create new label via api
    When I create label with title "hello" via api
    Then I should see label with title "hello" via api

  @regress
  @allure.label.jira:AE-1
  @allure.label.msrv:Repository
  Scenario: Delete existing label via api
    When I create label with title "hello" via api
    And I delete label with title "hello" via api
    Then I should not see label with content "hello" via api
