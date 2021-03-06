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

import com.redhat.arquillian.che.annotations.Workspace;
import com.redhat.arquillian.che.resource.Stack;
import org.apache.log4j.Logger;
import org.jboss.arquillian.graphene.Graphene;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import redhat.che.functional.tests.fragments.ProjectItem;

import java.util.concurrent.TimeUnit;

@RunWith(Arquillian.class)
@Workspace(stackID = Stack.NODEJS)
public class PackageJsonTestCase extends AbstractCheFunctionalTest {
    private static final Logger LOG = Logger.getLogger(PackageJsonTestCase.class);

    @FindBy(className = "currentLine")
    private WebElement currentLine;

    @FindBy(id = "gwt-debug-contribute-pull-request-panel")
    private WebElement gitPanel;

    private static final String jsonDependency = "\"serve-static\": \"1.7.1\" ,\n";
    private static final String jsonExpectedError = "Package serve-static-1.7.1 is vulnerable: CVE-2015-1164. Recommendation: use version 1.7.2";
    private static final Integer jsonExpectedErrorLine = 12;
    private static final Integer jsonInjectionEntryPoint = 12;

    @Before
    public void setEnvironment(){
        LOG.info("Starting: " + this.getClass().getName());
        openBrowser();
        waitUntilProjectImported("Project web-nodejs-sample imported", 30);
    }


    @After
    public void resetEnvironment(){
        editorPart.codeEditor().setCursorToLine(jsonInjectionEntryPoint);
        editorPart.codeEditor().deleteNextLines(1);
    }

    @Test
    public void testPackageJsonBayesian() {
        openPackageJson();
        editorPart.codeEditor().setCursorToLine(jsonInjectionEntryPoint);
        editorPart.codeEditor().writeDependency(jsonDependency);
        boolean annotationFound = editorPart.codeEditor().verifyAnnotationErrorIsPresent(jsonExpectedError, jsonExpectedErrorLine);
        if (isProdPreview()) {
            Assert.assertFalse(bayesianErrorNotVisible, annotationFound);
        } else {
            Assert.assertTrue(annotationFound);
        }
    }

    private void openPackageJson() {
        ProjectItem item = nodejsProject.getResource("package.json");
        if(isProdPreview()){
            Graphene.waitGui().until().element(gitPanel).is().visible();
        }
        item.open();
        Graphene.waitGui().withTimeout(90, TimeUnit.SECONDS).until().element(currentLine).is().visible();
    }

}
