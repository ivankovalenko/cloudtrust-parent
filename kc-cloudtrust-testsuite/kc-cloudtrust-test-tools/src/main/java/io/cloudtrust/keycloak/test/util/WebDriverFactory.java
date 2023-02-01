package io.cloudtrust.keycloak.test.util;

import io.github.bonigarcia.wdm.WebDriverManager;

import org.jboss.logging.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class WebDriverFactory {
    private static final Logger LOG = Logger.getLogger(WebDriverFactory.class);
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
        LOG.debug("Creating HTMLUnit driver");
        return new CloudtrustHtmlUnitDriver(ignorePatterns);
    }

    private static void logOptions(String title, AbstractDriverOptions<?> options) {
        LOG.debug(title);
        Map<String, Object> map = options.asMap();
        if (map!=null) {
            for(Map.Entry<String, Object> option : map.entrySet()) {
                LOG.debugf("Driver option: %s=%s", option.getKey(), option.getValue());
            }
        }
    }

    private static WebDriver createChromeDriver(boolean headless) {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.setHeadless(headless);
        options.addArguments("--remote-allow-origins=*"); // stackoverflow.com/questions/75680149/unable-to-establish-websocket-connection
        logOptions("Creating Chrome driver", options);
        return new ChromeDriver(options);
    }

    private static WebDriver createFirefoxDriver(boolean headless) {
        WebDriverManager.firefoxdriver().setup();
        FirefoxOptions options = new FirefoxOptions();
        options.setHeadless(headless);
        logOptions("Creating Firefox driver", options);
        return new FirefoxDriver(options);
    }
}
