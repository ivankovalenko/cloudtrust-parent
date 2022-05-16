package io.cloudtrust.keycloak.test.util;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class WebDriverFactory {
    private static WebDriver webDriver;
    private static final List<String> ignorePatterns = Arrays.asList("patternfly/dist/css", "react-core/dist", "keycloak.v2/public/layout.css");

    private WebDriverFactory() {
    }

    public static void ignorePatterns(Collection<String> patterns) {
        WebDriverFactory.ignorePatterns.clear();
        WebDriverFactory.ignorePatterns.addAll(patterns);
    }

    public static WebDriver provide() {
        if (webDriver == null) {
            String driver = TestSuiteParameters.get().getEnv("browser", "");
            switch (driver) {
                case "chrome":
                    webDriver = createChromeDriver(false);
                    break;
                case "chrome-headless":
                    webDriver = createChromeDriver(true);
                    break;
                case "firefox":
                    webDriver = createFirefoxDriver(false);
                    break;
                case "firefox-headless":
                    webDriver = createFirefoxDriver(true);
                    break;
                default:
                    webDriver = createHtmlUnitDriver();
            }
        }
        return webDriver;
    }

    private static WebDriver createHtmlUnitDriver() {
        return new CloudtrustHtmlUnitDriver(ignorePatterns);
    }

    private static WebDriver createChromeDriver(boolean headless) {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless");
        }
        return new ChromeDriver(options);
    }

    private static WebDriver createFirefoxDriver(boolean headless) {
        WebDriverManager.firefoxdriver().setup();
        FirefoxOptions options = new FirefoxOptions();
        options.setHeadless(headless);
        return new FirefoxDriver(options);
    }
}
