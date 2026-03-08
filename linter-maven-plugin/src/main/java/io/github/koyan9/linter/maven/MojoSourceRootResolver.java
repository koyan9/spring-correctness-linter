/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.koyan9.linter.maven;

import io.github.koyan9.linter.core.SourceRoot;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MojoSourceRootResolver {

    List<SourceRoot> resolve(
            Path projectRoot,
            MavenProject project,
            List<MavenProject> reactorProjects,
            java.io.File sourceDirectory,
            String additionalSourceDirectories,
            boolean scanReactorModules,
            boolean includeTestSourceRoots
    ) {
        LinkedHashMap<Path, SourceRoot> sourceRoots = new LinkedHashMap<>();

        if (scanReactorModules) {
            List<MavenProject> projectsToScan = reactorProjects == null || reactorProjects.isEmpty()
                    ? List.of(project)
                    : reactorProjects;
            for (MavenProject reactorProject : projectsToScan) {
                addProjectSourceRoots(reactorProject, sourceRoots, includeTestSourceRoots);
            }
        } else if (project != null) {
            addProjectSourceRoots(project, sourceRoots, includeTestSourceRoots);
        }

        if (sourceDirectory != null) {
            SourceRoot sourceRoot = new SourceRoot(sourceDirectory.toPath().toAbsolutePath().normalize(), moduleId(project));
            sourceRoots.put(sourceRoot.path(), sourceRoot);
        }
        if (additionalSourceDirectories != null && !additionalSourceDirectories.isBlank()) {
            for (String token : additionalSourceDirectories.split("[,;]")) {
                if (!token.isBlank()) {
                    Path sourceRoot = Path.of(token.trim());
                    Path resolvedPath = (sourceRoot.isAbsolute() ? sourceRoot : projectRoot.resolve(sourceRoot)).toAbsolutePath().normalize();
                    SourceRoot currentSourceRoot = new SourceRoot(resolvedPath, moduleId(project));
                    sourceRoots.put(currentSourceRoot.path(), currentSourceRoot);
                }
            }
        }

        return sourceRoots.values().stream().toList();
    }

    private void addProjectSourceRoots(MavenProject currentProject, Map<Path, SourceRoot> sourceRoots, boolean includeTestSourceRoots) {
        if (currentProject == null) {
            return;
        }
        addStringRoots(currentProject.getCompileSourceRoots(), sourceRoots, moduleId(currentProject));
        if (includeTestSourceRoots) {
            addStringRoots(currentProject.getTestCompileSourceRoots(), sourceRoots, moduleId(currentProject));
        }
    }

    private void addStringRoots(List<String> roots, Map<Path, SourceRoot> sourceRoots, String moduleId) {
        if (roots == null) {
            return;
        }
        for (String root : roots) {
            if (root != null && !root.isBlank()) {
                Path sourceRootPath = Path.of(root).toAbsolutePath().normalize();
                sourceRoots.put(sourceRootPath, new SourceRoot(sourceRootPath, moduleId));
            }
        }
    }

    private String moduleId(MavenProject currentProject) {
        if (currentProject == null) {
            return ".";
        }
        if (currentProject.getArtifactId() != null && !currentProject.getArtifactId().isBlank()) {
            return currentProject.getArtifactId();
        }
        if (currentProject.getFile() != null && currentProject.getFile().getParentFile() != null) {
            return currentProject.getFile().getParentFile().getName();
        }
        return ".";
    }
}
