package net.thucydides.junit.runners;

import java.io.File;

import net.thucydides.junit.internals.ManagedWebDriverAnnotatedField;

import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.thucydides.core.screenshots.Photographer;

/**
 * A test runner for WebDriver-based web tests. This test runner initializes a
 * WebDriver instance before running the tests in their order of appearance. At
 * the end of the tests, it closes and quits the WebDriver instance.
 * 
 * @author johnsmart
 * 
 */
public class ThucydidesRunner extends BlockJUnit4ClassRunner {

    private static final String DEFAULT_OUTPUT_DIRECTORY = "target/thucydides";

    /**
     * Creates new browser instances. The Browser Factory's job is to provide
     * new web driver instances. It is designed to isolate the test runner from
     * the business of creating and managing WebDriver drivers.
     */
    private WebDriverFactory webDriverFactory;

    ThreadLocal<WebDriver> webdriver = new ThreadLocal<WebDriver>();

    /**
     * HTML and XML reports will be generated in this directory.
     */
    private File outputDirectory;

    /**
     * Records screenshots for successful or failing tests.
     */
    private ScreenshotListener screenshotListener;
    
    /**
     * As soon as a test fails, all subsequent tests are ignored.
     */
    private boolean aPreviousTestHasFailed = false;

    /**
     * Takes cares of screenshots.
     * The member variable makes for more convenient testing.
     */
    private Photographer photographer;
    
    /**
     * Creates a new test runner for WebDriver web tests.
     * 
     * @throws InitializationError
     *             if some JUnit-related initialization problem occurred
     * @throws UnsupportedDriverException
     *             if the requested driver type is not supported
     */
    public ThucydidesRunner(Class<?> klass) throws InitializationError {
        super(klass);
        checkRequestedDriverType();
        checkThatManagedFieldIsDefinedIn(klass);
        webDriverFactory = new WebDriverFactory();
    }

    private void setupThePhotographer() {
        outputDirectory = new File(DEFAULT_OUTPUT_DIRECTORY);
        outputDirectory.mkdirs();
        photographer = getPhotographerFor((TakesScreenshot) getDriver(), outputDirectory);
    }
    
    protected Photographer getPhotographer() {
        if (photographer == null) {
            setupThePhotographer();
        }
        return photographer;
    }

    protected Photographer getPhotographerFor(TakesScreenshot driver, File outputDirectory) {
        return new Photographer((TakesScreenshot) getDriver(), outputDirectory);
    }
    
    private void checkThatManagedFieldIsDefinedIn(Class<?> testCase) {
        ManagedWebDriverAnnotatedField.findFirstAnnotatedField(testCase);
    }

    /**
     * Ensure that the requested driver type is valid before we start the tests.
     * Otherwise, throw an InitializationError.
     */
    private void checkRequestedDriverType() throws UnsupportedDriverException {
        findDriverType();
    }

    /**
     * Override the default web driver factory. Normal users shouldn't need to
     * do this very often.
     */
    public void setWebDriverFactory(WebDriverFactory webDriverFactory) {
        this.webDriverFactory = webDriverFactory;
    }

    @Override
    public void run(RunNotifier notifier) {
        initializeDriver();
        noPreviousTestHasFailed();
        notifier.addListener(new FailureListener(this));

        screenshotListener = new ScreenshotListener(getPhotographer());
        notifier.addListener(screenshotListener);
        
        super.run(notifier);
       
        closeDriver();
    }

    private void noPreviousTestHasFailed() {
        aPreviousTestHasFailed = false;
    }

    protected void aTestHasFailed() {
        aPreviousTestHasFailed = true;        
    }

    private void notifyListenersOfTestFailure(ScreenshotListener screenshotListener) {
        screenshotListener.aTestHasFailed();
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        if (aPreviousTestHasFailed) {
            notifyListenersOfTestFailure(screenshotListener);
            notifier.fireTestIgnored(describeChild(method));
        } else {
            super.runChild(method, notifier);
        }
    }
    
    @Override
    protected Statement methodInvoker(FrameworkMethod method, Object test) {
        injectDriverInto(test);
        return super.methodInvoker(method, test);
    }

    /**
     * Instanciate the @Managed-annotated WebDriver instance with current
     * WebDriver.
     */
    protected void injectDriverInto(Object testCase) {
        ManagedWebDriverAnnotatedField webDriverField = ManagedWebDriverAnnotatedField
                .findFirstAnnotatedField(testCase.getClass());

        webDriverField.setValue(testCase, getDriver());
    }

    /**
     * A new WebDriver is created before any of the tests are run. The driver is
     * determined by the 'webdriver.driver' system property.
     * 
     * @throws UnsupportedDriverException
     */
    private void initializeDriver() throws UnsupportedDriverException {
        webdriver.set(newDriver());
    }

    /**
     * Create a new driver instance based on system property values. You can
     * override this method to use a custom driver if you really know what you
     * are doing.
     * 
     * @throws UnsupportedDriverException
     *             if the driver type is not supported.
     */
    protected WebDriver newDriver() throws UnsupportedDriverException {
        SupportedWebDriver supportedDriverType = findDriverType();
        return webDriverFactory.newInstanceOf(supportedDriverType);
    }

    private SupportedWebDriver findDriverType()
            throws UnsupportedDriverException {
        String driverType = System.getProperty("webdriver.driver", "firefox");
        SupportedWebDriver supportedDriverType = lookupSupportedDriverTypeFor(driverType);
        return supportedDriverType;
    }

    protected WebDriver getDriver() {
        return webdriver.get();
    }

    /**
     * Transform a driver type into the SupportedWebDriver enum. Driver type can
     * be any case.
     * 
     * @throws UnsupportedDriverException
     */
    private SupportedWebDriver lookupSupportedDriverTypeFor(String driverType)
            throws UnsupportedDriverException {
        SupportedWebDriver driver = null;
        try {
            driver = SupportedWebDriver.valueOf(driverType.toUpperCase());
        } catch (NullPointerException npe) {
            throwUnsupportedDriverExceptionFor(driverType);
        } catch (IllegalArgumentException iae) {
            throwUnsupportedDriverExceptionFor(driverType);
        }
        return driver;
    }

    private void throwUnsupportedDriverExceptionFor(String driverType)
            throws UnsupportedDriverException {
        throw new UnsupportedDriverException(driverType
                + " is not a supported browser. Supported driver values are: "
                + SupportedWebDriver.listOfSupportedDrivers());
    }

    /**
     * We shut down the Webdriver at the end of the tests. This should shut down
     * the browser as well.
     */
    private void closeDriver() {
        if ((webdriver != null) && (webdriver.get() != null)) {
            webdriver.get().quit();
        }
    }

    /**
     * Keeps track of failing tests, so that subsequent ones can be ignored.
     * @author johnsmart
     *
     */
    private class FailureListener extends RunListener {

        private final ThucydidesRunner thucydidesRunner;
        
        public FailureListener(ThucydidesRunner thucydidesRunner) {
            this.thucydidesRunner = thucydidesRunner;
        }

        public void testFailure(Failure failure) throws Exception {
            thucydidesRunner.aTestHasFailed();
        }
    }
    
}