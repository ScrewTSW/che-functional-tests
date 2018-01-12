/******************************************************************************* 
 * Copyright (c) 2017 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
*/
package redhat.che.functional.tests;

import com.redhat.arquillian.che.resource.CheWorkspace;
import org.apache.log4j.Logger;
import org.arquillian.extension.recorder.screenshooter.Screenshooter;
import org.jboss.arquillian.drone.api.annotation.Drone;
import org.jboss.arquillian.graphene.findby.FindByJQuery;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.runner.RunWith;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import redhat.che.functional.tests.fragments.EditorPart;
import redhat.che.functional.tests.fragments.Project;
import redhat.che.functional.tests.fragments.window.AskForValueDialog;
import redhat.che.functional.tests.utils.ActionUtils;

import java.util.concurrent.TimeUnit;

import static org.jboss.arquillian.graphene.Graphene.waitGui;
import static redhat.che.functional.tests.utils.Constants.*;

@RunWith(Arquillian.class)
public abstract class AbstractCheFunctionalTest {
	private static final Logger LOG = Logger.getLogger(AbstractCheFunctionalTest.class);

	@Drone
    protected WebDriver driver;

	@ArquillianResource
    protected Screenshooter screenshooter;

    @FindByJQuery("#gwt-debug-projectTree > div:contains('" + VERTX_PROJECT_NAME + "'):first")
    protected Project vertxProject;

    @FindByJQuery("#gwt-debug-projectTree > div:contains('" + NODEJS_PROJECT_NAME + "'):first")
    protected Project nodejsProject;

    @FindBy(id = "gwt-debug-editorMultiPartStack-contentPanel")
    protected EditorPart editorPart;

    @FindByJQuery("#gwt-debug-popup-container:contains('Workspace is running')")
    private WebElement workspaceIsRunningPopup;

    @FindByJQuery("#username, #ide-application-frame")
    private WebElement workspaceOrLoginPage;

    @FindByJQuery(".inDown, .gwt-Label")
    private WebElement workspaceIsStartingUp;

    @FindByJQuery("#ide-application-frame, #gwt-debug-popup-container:contains('Workspace is running'), .inDown, .gwt-Label")
    private WebElement workspacePresentIndicator;

    @FindBy(id = "username")
    private WebElement usernameField;

    @FindBy(id = "password")
    private WebElement passwordField;

    @FindBy(id = "kc-login")
    private WebElement loginButton;

    @FindBy(id = "gwt-debug-askValueDialog-window")
    private AskForValueDialog askForValueDialog;

    @ArquillianResource
    private static CheWorkspace workspace;

    protected void openBrowser() {
        openBrowser(workspace);
    }

    protected void openBrowser(CheWorkspace wkspc) {
        LOG.info("Opening browser");
        driver.get(wkspc.getIdeLink());
        driver.manage().window().maximize();
//        driver.manage().deleteAllCookies();
        screenshooter.setScreenshotTargetDir("target/screenshots");
        // ... other possible options and settings;
        waitUntilWorkspaceOrLoginIsDisplayed();
        waitUntilWorkspaceIsRunningElseRefresh();
//        waitGui().until().element(workspaceOrLoginPage).is().visible();
//        if ("username".equals(workspaceOrLoginPage.getAttribute("id"))) {
//            login();
//            waitUntilWorkspaceIsRunningElseRefresh();
//        }

        waitGui().until().element(workspaceIsRunningPopup).is().not().visible();
    }

    /**
	 * Workarnound for https://github.com/openshiftio/openshift.io/issues/1304.
	 * Should be removed once resolved.
	 */
	private void waitUntilWorkspaceIsRunningElseRefresh() {
	    try {
            waitGui().withTimeout(20, TimeUnit.SECONDS).until("Workspace did not display.").element(workspacePresentIndicator).is().visible();
        } catch (WebDriverException e) {
            exitWithError("Workspace did not open."+e.getMessage());
        }
        try {
            if (workspaceIsStartingUp.isDisplayed()) {
                waitGui().withTimeout(3, TimeUnit.MINUTES).withMessage("Workspace did not start up.").until(webDriver -> {
                    try {
                        waitGui().until("Workspace is still starting up...").element(workspaceIsStartingUp).is().not().visible();
                        return true;
                    } catch (WebDriverException e) {
                        LOG.info("Inner check timed out, refreshing.");
                        screenshooter.takeScreenshot();
                        driver.navigate().refresh();
                        return false;
                    }
                });
            } else {
                waitGui().withTimeout(3, TimeUnit.MINUTES).withMessage("Workspace did not start up.").until(webDriver -> {
                    try {
                        waitGui().until().element(workspaceIsRunningPopup).is().visible();
                        return true;
                    } catch (WebDriverException e) {
                        try {
                            webDriver.switchTo().alert().accept();
                        } catch (NoAlertPresentException ex) {
                            // Alert didn't come up. Do nothing.
                        }
                        screenshooter.takeScreenshot();
                        webDriver.navigate().refresh();
                        return false;
                    }
                });
            }
        } catch (WebDriverException e) {
	        exitWithError(e.getMessage());
        }
	}

	public void waitUntilWorkspaceOrLoginIsDisplayed() {
        try {
            waitGui().withTimeout(3, TimeUnit.MINUTES).withMessage("Workspace nor LogIn screen was displayed.").until(webDriver -> {
                if (workspaceOrLoginPage.isDisplayed()) {
                    if ("username".equals(workspaceOrLoginPage.getAttribute("id"))) {
                        login();
                    }
                    return true;
                }
                return workspaceOrLoginPage.isDisplayed();
            });
        } catch (WebDriverException e) {
            exitWithError("Webspace nor LogIn page could be loaded:" + e.getMessage());
        }
    }

	private void login() {
    	LOG.info("Logging in");
        usernameField.sendKeys(OSIO_USERNAME);
        passwordField.sendKeys(OSIO_PASSWORD);
        loginButton.click();
        //ByJQuery collapse = ByJQuery.selector("div:has(path[id='collapse-expand'])");
        //waitModel().withTimeout(40, SECONDS).until().element(collapse).is().visible();
        //driver.findElement(collapse).click();
    }

    protected void setCursorToLine(int line) {
        ActionUtils.openMoveCursorDialog(driver);
        askForValueDialog.waitFormToOpen();
        askForValueDialog.typeAndWaitText(line);
        askForValueDialog.clickOkBtn();
        askForValueDialog.waitFormToClose();
    }

    private void exitWithError(String err) {
        screenshooter.takeScreenshot();
	    LOG.error(err);
	    System.exit(1);
    }

}
