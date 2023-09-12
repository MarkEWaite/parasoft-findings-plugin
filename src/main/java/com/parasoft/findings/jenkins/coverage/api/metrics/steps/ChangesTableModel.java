package com.parasoft.findings.jenkins.coverage.api.metrics.steps;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Node;

import hudson.Functions;

import com.parasoft.findings.jenkins.coverage.api.metrics.color.ColorProvider;

/**
 * A base class for coverage table models that handle the changes to a result of a reference build.
 */
abstract class ChangesTableModel extends CoverageTableModel {
    private final Node changeRoot;

    ChangesTableModel(final String id, final Node root, final Node changeRoot,
            final RowRenderer renderer, final ColorProvider colorProvider) {
        super(id, root, renderer, colorProvider);

        this.changeRoot = changeRoot;
    }

    @Override
    public List<Object> getRows() {
        Locale browserLocale = Functions.getCurrentLocale();
        return changeRoot.getAllFileNodes().stream()
                .map(file -> createRow(file, browserLocale))
                .collect(Collectors.toList());
    }

    abstract CoverageRow createRow(FileNode file, Locale browserLocale);

    FileNode getOriginalNode(final FileNode fileNode) {
        return getRoot().getAllFileNodes().stream()
                .filter(node -> node.getRelativePath().equals(fileNode.getRelativePath())
                        && node.getName().equals(fileNode.getName()))
                .findFirst()
                .orElse(fileNode); // return this as fallback to prevent exceptions
    }

    /**
     * UI row model for the changes rows of a table.
     */
    static class ChangesRow extends CoverageRow {
        private final FileNode originalFile;

        ChangesRow(final FileNode originalFile, final FileNode changedFileNode,
                final Locale browserLocale, final RowRenderer renderer, final ColorProvider colorProvider) {
            super(changedFileNode, browserLocale, renderer, colorProvider);

            this.originalFile = originalFile;
        }

        FileNode getOriginalFile() {
            return originalFile;
        }
    }
}
