package io.eroshenkoam.allure;

import io.cucumber.junit.AllureCucumber;
import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(AllureCucumber.class)
@CucumberOptions(plugin = {
        "io.qameta.allure.cucumber4jvm.AllureCucumber4Jvm",
        "progress",
        "summary"
})
public class CucumberTest {

}
