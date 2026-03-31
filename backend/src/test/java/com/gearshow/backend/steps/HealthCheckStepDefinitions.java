package com.gearshow.backend.steps;

import com.gearshow.backend.support.ScenarioContext;
import com.gearshow.backend.support.TestApiClient;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 헬스체크 기능의 Step Definition.
 * 향후 모든 Step Definition이 따라야 할 TestApiClient + ScenarioContext 패턴을 보여준다.
 */
public class HealthCheckStepDefinitions {

    private final TestApiClient apiClient;
    private final ScenarioContext context;

    public HealthCheckStepDefinitions(TestApiClient apiClient, ScenarioContext context) {
        this.apiClient = apiClient;
        this.context = context;
    }

    @When("I send a GET request to {string}")
    public void iSendAGetRequestTo(String path) {
        context.setLastResponse(apiClient.get(path));
    }

    @Then("the response status code should be {int}")
    public void theResponseStatusCodeShouldBe(int expectedStatusCode) {
        assertThat(context.getLastResponse().statusCode()).isEqualTo(expectedStatusCode);
    }

    @And("the response field {string} should be {string}")
    public void theResponseFieldShouldBe(String fieldName, String expectedValue) {
        Object actual = context.getLastResponse().field(fieldName);
        assertThat(actual).isNotNull();
        assertThat(actual.toString()).isEqualTo(expectedValue);
    }
}
