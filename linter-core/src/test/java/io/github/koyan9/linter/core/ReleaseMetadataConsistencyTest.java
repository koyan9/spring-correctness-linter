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
import java.util.List;

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

    @Test
    void samplePomVersionsAlignWithRootReleaseVersion() throws Exception {
        Path moduleBase = Path.of("").toAbsolutePath().normalize();
        Path repositoryRoot = moduleBase.getParent();
        String rootVersion = readXPath(repositoryRoot.resolve("pom.xml"), "/*[local-name()='project']/*[local-name()='version']/text()");

        assertEquals(
                rootVersion,
                readXPath(repositoryRoot.resolve("samples/vulnerable-sample/pom.xml"), "/*[local-name()='project']/*[local-name()='version']/text()"),
                "vulnerable-sample version should match the root release version"
        );
        assertEquals(
                rootVersion,
                readXPath(repositoryRoot.resolve("samples/vulnerable-sample/pom.xml"), "/*[local-name()='project']/*[local-name()='properties']/*[local-name()='spring.correctness.linter.version']/text()"),
                "vulnerable-sample plugin version property should match the root release version"
        );

        assertEquals(
                rootVersion,
                readXPath(repositoryRoot.resolve("samples/reactor-sample/pom.xml"), "/*[local-name()='project']/*[local-name()='version']/text()"),
                "reactor-sample parent version should match the root release version"
        );
        assertEquals(
                rootVersion,
                readXPath(repositoryRoot.resolve("samples/reactor-sample/pom.xml"), "/*[local-name()='project']/*[local-name()='properties']/*[local-name()='spring.correctness.linter.version']/text()"),
                "reactor-sample plugin version property should match the root release version"
        );
        for (String childPom : List.of(
                "samples/reactor-sample/root-app/pom.xml",
                "samples/reactor-sample/module-a/pom.xml"
        )) {
            assertEquals(
                    rootVersion,
                    readXPath(repositoryRoot.resolve(childPom), "/*[local-name()='project']/*[local-name()='parent']/*[local-name()='version']/text()"),
                    childPom + " parent version should match the root release version"
            );
        }

        assertEquals(
                rootVersion,
                readXPath(repositoryRoot.resolve("samples/adoption-suite/pom.xml"), "/*[local-name()='project']/*[local-name()='version']/text()"),
                "adoption-suite parent version should match the root release version"
        );
        assertEquals(
                rootVersion,
                readXPath(repositoryRoot.resolve("samples/adoption-suite/pom.xml"), "/*[local-name()='project']/*[local-name()='properties']/*[local-name()='spring.correctness.linter.version']/text()"),
                "adoption-suite plugin version property should match the root release version"
        );
        for (String childPom : List.of(
                "samples/adoption-suite/basic-app/pom.xml",
                "samples/adoption-suite/centralized-security-app/pom.xml",
                "samples/adoption-suite/cache-convention-app/pom.xml",
                "samples/adoption-suite/external-rules-app/pom.xml",
                "samples/adoption-suite/external-rules-app/custom-rule-provider/pom.xml",
                "samples/adoption-suite/external-rules-app/consumer-app/pom.xml"
        )) {
            assertEquals(
                    rootVersion,
                    readXPath(repositoryRoot.resolve(childPom), "/*[local-name()='project']/*[local-name()='parent']/*[local-name()='version']/text()"),
                    childPom + " parent version should match the root release version"
            );
        }
    }

    private String readXPath(Path xmlFile, String expression) throws Exception {
        var documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(false);
        var documentBuilder = documentBuilderFactory.newDocumentBuilder();
        var document = documentBuilder.parse(xmlFile.toFile());
        return (String) XPATH_FACTORY.newXPath().evaluate(expression, document, XPathConstants.STRING);
    }
}
