package net.thucydides.core.webdriver.integration;

import net.thucydides.core.pages.PageObject;
import org.junit.*;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.interactions.ElementNotDisplayedException;

import java.io.File;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

public class WhenCheckingVisibilityOnAWebSiteUsingPageObjects {

    public class IndexPage extends PageObject {

        public WebElement multiselect;

        public IndexPage(WebDriver driver) {
            super(driver);
        }


    }
    
    private static WebDriver driver;
    
    @BeforeClass
    public static void open_local_static_site() {
        driver = new FirefoxDriver();
        openStaticTestSite(driver);
    }

    @AfterClass
    public static void closeBrowser() {
        driver.quit();
    }

    private static void openStaticTestSite(WebDriver driver) {
        File baseDir = new File(System.getProperty("user.dir"));
        File testSite = new File(baseDir,"src/test/resources/static-site/index.html");
        driver.get("file://" + testSite.getAbsolutePath());
    }


    @Test
    public void should_know_when_an_element_is_visible_on_the_page() {
        IndexPage indexPage = new IndexPage(driver);

        assertThat(indexPage.isElementVisible(By.xpath("//h2[.='A visible title']")), is(true));
    }

    @Test
    public void should_know_when_an_element_is_present_but_not_visible_on_the_page() {
        IndexPage indexPage = new IndexPage(driver);

        assertThat(indexPage.isElementVisible(By.xpath("//h2[.='An invisible title']")), is(false));
    }

    @Test(expected = ElementNotDisplayedException.class)
    public void should_fail_when_waiting_for_an_invisible_object() {
        IndexPage indexPage = new IndexPage(driver);
        indexPage.setWaitForTimeout(150);

        indexPage.waitForRenderedElements(By.xpath("//h2[.='An invisible title']"));

    }

    @Test
    public void can_wait_for_one_of_several_elements_to_be_visible() {
        IndexPage indexPage = new IndexPage(driver);
        indexPage.setWaitForTimeout(150);
        indexPage.waitForAnyRenderedElementOf(By.id("color"), By.id("taste"), By.id("sound"));
    }

    @Test(expected = ElementNotDisplayedException.class)
    public void fails_if_waiting_for_text_to_disappear_too_long() {
        IndexPage indexPage = new IndexPage(driver);
        indexPage.setWaitForTimeout(150);

        indexPage.waitForTextToDisappear("A visible title");
    }

    @Test(expected = ElementNotDisplayedException.class)
    public void should_fail_when_waiting_for_an_undisplayed_text() {
        IndexPage indexPage = new IndexPage(driver);
        indexPage.setWaitForTimeout(150);

        indexPage.waitForTextToAppear("This text never appears");
    }


    @Test
    public void should_know_when_an_element_is_not_present_on_the_page() {
        IndexPage indexPage = new IndexPage(driver);

        assertThat(indexPage.isElementVisible(By.xpath("//h2[.='Non-existant title']")), is(false));
    }
    
}