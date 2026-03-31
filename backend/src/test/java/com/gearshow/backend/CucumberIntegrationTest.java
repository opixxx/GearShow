package com.gearshow.backend;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Cucumber 테스트 실행 진입점.
 * classpath:features/ 하위의 feature 파일과 이 패키지의 glue 코드를 스캔한다.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.gearshow.backend")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
public class CucumberIntegrationTest {
}
