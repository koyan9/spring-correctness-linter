/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.core;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseMetadataConsistencyTest {

    private static final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();

    @Test
    void rootVersionMatchesModuleParentVersionsAndScmTag() throws Exception {
        Path moduleBase = Path.of("").toAbsolutePath().normalize();
        Path repositoryRoot = moduleBase.getParent();

        String rootVersion = readXPath(repositoryRoot.resolve("pom.xml"), "/*[local-name()='project']/*[local-name()='version']/text()");
        String rootScmTag = readXPath(repositoryRoot.resolve("pom.xml"), "/*[local-name()='project']/*[local-name()='scm']/*[local-name()='tag']/text()");
        String coreParentVersion = readXPath(moduleBase.resolve("pom.xml"), "/*[local-name()='project']/*[local-name()='parent']/*[local-name()='version']/text()");
        String pluginParentVersion = readXPath(repositoryRoot.resolve("linter-maven-plugin/pom.xml"), "/*[local-name()='project']/*[local-name()='parent']/*[local-name()='version']/text()");

        assertEquals(rootVersion, coreParentVersion, "linter-core parent version should match the root release version");
        assertEquals(rootVersion, pluginParentVersion, "linter-maven-plugin parent version should match the root release version");
        assertEquals("v" + rootVersion, rootScmTag, "root scm.tag should follow the current release version");
    }

    private String readXPath(Path xmlFile, String expression) throws Exception {
        var documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(false);
        var documentBuilder = documentBuilderFactory.newDocumentBuilder();
        var document = documentBuilder.parse(xmlFile.toFile());
        return (String) XPATH_FACTORY.newXPath().evaluate(expression, document, XPathConstants.STRING);
    }
}
