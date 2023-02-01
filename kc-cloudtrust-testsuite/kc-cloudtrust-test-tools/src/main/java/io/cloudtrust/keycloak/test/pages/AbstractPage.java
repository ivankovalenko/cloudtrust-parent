package io.cloudtrust.keycloak.test.pages;

import io.cloudtrust.exception.CloudtrustRuntimeException;
import io.cloudtrust.keycloak.test.util.OAuthClient;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.openqa.selenium.support.ui.ExpectedConditions.javaScriptThrowsNoExceptions;
import static org.openqa.selenium.support.ui.ExpectedConditions.not;
import static org.openqa.selenium.support.ui.ExpectedConditions.urlToBe;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public abstract class AbstractPage {
    private static final Logger log = Logger.getLogger(AbstractPage.class.getName());

    public static final String PAGELOAD_TIMEOUT_PROP = "pageload.timeout";
    public static final Integer PAGELOAD_TIMEOUT_MILLIS = Integer.parseInt(System.getProperty(PAGELOAD_TIMEOUT_PROP, "10000"));

    protected WebDriver driver;
    protected OAuthClient oauthClient;

    public void assertCurrent() {
        String name = getClass().getSimpleName();
        Assertions.assertTrue(isCurrent(), "Expected " + name + " but was " + driver.getTitle() + " (" + driver.getCurrentUrl() + ")");
    }

    public boolean isCurrent() {
        return false;
    }

    protected String getLoginFormUrl() {
        return this.oauthClient.getLoginFormUrl();
    }

    public void open() {
        throw new CloudtrustRuntimeException("open() not implemented");
    }

    /**
     * Navigate to a logout URL. Automatically confirm logout if necessary
     */
    public void openLogout() {
        openLogout(true);
    }

    /**
     * Navigate to a logout URL
     *
     * @param automaticallyConfirmLogout If a confirmation is required, tells if confirmation should be given automatically or not
     */
    public void openLogout(boolean automaticallyConfirmLogout) {
        driver.navigate().to(oauthClient.getLogoutFormUrl());
        if (automaticallyConfirmLogout) {
            WebElement elt = this.driver.findElement(By.id("kc-logout"));
            if (elt != null) {
                elt.click();
            }
        }
    }

    public OAuthClient getOAuthClient() {
        return this.oauthClient;
    }

    public void setOAuthClient(OAuthClient client) {
        this.oauthClient = client;
    }

    public void setDriver(WebDriver driver) {
        this.driver = driver;
    }

    public void clickLink(WebElement element) {
        //waitUntilElement(element).is().clickable();

        // Safari sometimes thinks an element is not visible
        // even though it is. In this case we just move the cursor and click.
        if (driver instanceof SafariDriver && !element.isDisplayed()) {
            performOperationWithPageReload(() -> new Actions(driver).click(element).perform());
        } else {
            performOperationWithPageReload(element::click);
        }
    }

    protected void performOperationWithPageReload(Runnable operation) {
        operation.run();
        waitForPageToLoad();
    }

    protected void waitForPageToLoad() {
        if (this.driver instanceof HtmlUnitDriver) {
            return; // not needed
        }

        String currentUrl = null;

        // Ensure the URL is "stable", i.e. is not changing anymore; if it'd changing, some redirects are probably still in progress
        for (int maxRedirects = 4; maxRedirects > 0; maxRedirects--) {
            currentUrl = this.driver.getCurrentUrl();
            FluentWait<WebDriver> wait = new FluentWait<>(this.driver).withTimeout(Duration.ofMillis(250));
            try {
                wait.until(not(urlToBe(currentUrl)));
            } catch (TimeoutException e) {
                break; // URL has not changed recently - ok, the URL is stable and page is current
            }
            if (maxRedirects == 1) {
                log.warn("URL seems unstable! (Some redirect are probably still in progress)");
            }
        }

        WebDriverWait wait = new WebDriverWait(this.driver, Duration.ofMillis(PAGELOAD_TIMEOUT_MILLIS));
        ExpectedCondition<Boolean> waitCondition = null;

        // Different wait strategies for Admin and Account Consoles
        if (currentUrl.matches("^[^\\/]+:\\/\\/[^\\/]+\\/admin\\/.*$")) { // Admin Console
            // Checks if the document is ready and asks AngularJS, if present, whether there are any REST API requests in progress
            waitCondition = javaScriptThrowsNoExceptions(
                    "if (document.readyState !== 'complete' "
                            + "|| (typeof angular !== 'undefined' && angular.element(document.body).injector().get('$http').pendingRequests.length !== 0)) {"
                            + "throw \"Not ready\";"
                            + "}");
        } else if (currentUrl.matches("^[^\\/]+:\\/\\/[^\\/]+\\/realms\\/[^\\/]+\\/account\\/.*#/.+$")) {
            // check for new Account Console URL
            pause(2000); // TODO rework this temporary workaround once KEYCLOAK-11201 and/or KEYCLOAK-8181 are fixed
        }

        if (waitCondition != null) {
            try {
                wait.until(waitCondition);
            } catch (TimeoutException e) {
                log.warn("waitForPageToLoad time exceeded!");
            }
        }
    }

    public void pause(long millis) {
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ex) {
                log.fatal("Interrupted", ex);
                Thread.currentThread().interrupt();
            }
        }
    }

    public String getTextFromElement(WebElement element) {
        String text = element.getText();
        if (driver instanceof SafariDriver) {
            try {
                // Safari on macOS doesn't comply with WebDriver specs yet again - getText() retrieves hidden text by CSS.
                text = element.findElement(By.xpath("./span[not(contains(@class,'ng-hide'))]")).getText();
            } catch (NoSuchElementException e) {
                // no op
            }
            return text.trim(); // Safari on macOS sometimes for no obvious reason surrounds the text with spaces
        }
        return text;
    }

    public static void setTextValue(WebElement input, String value) {
        input.click();
        input.clear();
        if (!StringUtils.isEmpty(value)) { // setting new input
            input.sendKeys(value);
        } else { // just clearing the input; input.clear() may not fire all JS events so we need to let the page know that something's changed
            input.sendKeys("1");
            input.sendKeys(Keys.BACK_SPACE);
        }
    }

    public String getPageTitle(WebDriver driver) {
        return driver.findElement(By.id("kc-page-title")).getText();
    }

    public boolean pageContains(String text) {
        return driver.getPageSource().contains(text);
    }

    public String getPageTitle() {
        try {
            return driver.findElement(By.id("kc-page-title")).getText();
        } catch (NoSuchElementException e) {
            return null;
        }
    }
}
