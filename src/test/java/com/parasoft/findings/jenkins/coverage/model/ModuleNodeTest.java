package com.parasoft.findings.jenkins.coverage.model;

import org.junit.jupiter.api.Test;

import com.parasoft.findings.jenkins.coverage.model.Coverage.CoverageBuilder;

import static com.parasoft.findings.jenkins.coverage.model.Metric.FILE;
import static com.parasoft.findings.jenkins.coverage.model.Metric.*;
import static com.parasoft.findings.jenkins.coverage.Assertions.*;

class ModuleNodeTest extends AbstractNodeTest {
    @Override
    Metric getMetric() {
        return MODULE;
    }

    @Override
    Node createNode(final String name) {
        var moduleNode = new ModuleNode(name);
        moduleNode.addSource("/path/to/sources");
        return moduleNode;
    }

    @Test
    void shouldSplitPackagesWithoutPackageNodes() {
        var root = new ModuleNode("Root");
        assertThat(root.getAll(PACKAGE)).isEmpty();
        root.splitPackages();
        assertThat(root.getAll(PACKAGE)).isEmpty();

        root.addChild(new FileNode("file.c", "path"));
        root.splitPackages();
        assertThat(root.getAll(PACKAGE)).isEmpty();
    }

    @Test
    void shouldSplitPackagesWithoutName() {
        var root = new ModuleNode("Root");
        assertThat(root.getAll(PACKAGE)).isEmpty();
        root.splitPackages();
        assertThat(root.getAll(PACKAGE)).isEmpty();

        Node packageNode = new PackageNode("");
        root.addChild(packageNode);
        assertThat(root.getAll(PACKAGE)).hasSize(1);

        root.splitPackages();
        assertThat(root.getAll(PACKAGE)).hasSize(1);
        assertThat(root).hasOnlyChildren(packageNode);
    }

    @Test
    void shouldSplitPackagesIntoHierarchy() {
        var root = new ModuleNode("Root");
        assertThat(root.getAll(PACKAGE)).isEmpty();
        root.splitPackages();
        assertThat(root.getAll(PACKAGE)).isEmpty();

        root.addChild(new PackageNode("edu.hm.hafner"));
        assertThat(root.getAll(PACKAGE)).hasSize(1);
        root.splitPackages();
        assertThat(root.getAll(PACKAGE)).hasSize(3).satisfiesExactly(
                s -> assertThat(s).hasName("hafner"),
                s -> assertThat(s).hasName("hm"),
                s -> assertThat(s).hasName("edu")
        );
    }

    @Test
    void shouldDetectExistingPackagesOnSplit() {
        var root = new ModuleNode("Root");
        Node eduPackage = new PackageNode("edu");
        Node differentPackage = new PackageNode("org");

        root.addChild(differentPackage);
        root.addChild(eduPackage);

        eduPackage.addChild(new FileNode("File.c", "edu/File.c"));

        var builder = new CoverageBuilder().setMetric(LINE);
        eduPackage.addValue(builder.setCovered(10).setMissed(0).build());

        assertThat(root.getAll(PACKAGE)).hasSize(2);
        assertThat(root.getValue(LINE)).contains(builder.build());

        var subPackage = new PackageNode("edu.hm.hafner");
        root.addChild(subPackage);
        subPackage.addValue(builder.setMissed(10).build());
        subPackage.addChild(new FileNode("OtherFile.c", "edu.hm.hafner/OtherFile.c"));
        assertThat(root.getValue(LINE)).contains(builder.setCovered(20).setMissed(10).build());

        root.splitPackages();
        assertThat(root.getAll(PACKAGE)).hasSize(4);

        assertThat(root.getChildren()).hasSize(2).satisfiesExactlyInAnyOrder(
                org -> assertThat(org.getName()).isEqualTo("org"),
                edu -> assertThat(edu.getName()).isEqualTo("edu"));

        assertThat(root.getValue(LINE)).contains(builder.setCovered(20).setMissed(10).build());
    }

    @Test
    void shouldKeepNodesAfterSplitting() {
        var root = new ModuleNode("Root");
        Node pkg = new PackageNode("edu.hm.hafner");
        Node file = new FileNode("HelloWorld.java", "path");

        root.addChild(pkg);
        pkg.addChild(file);
        root.splitPackages();

        assertThat(root.getAll(PACKAGE)).hasSize(3);
        assertThat(root.getAll(FILE)).hasSize(1);
    }

    @Test
    void shouldNotMergeWhenDifferentMetric() {
        var root = new ModuleNode("Root");
        Node pkg = new PackageNode("edu.hm.hafner");
        Node file = new FileNode("Helicopter.java", "path");

        root.addChild(pkg);
        root.addChild(file);
        root.splitPackages();

        assertThat(root.getAll(PACKAGE)).hasSize(3);
        assertThat(root.getAll(FILE)).hasSize(1);
    }
}
