/*
 * MIT License
 *
 * Copyright (c) 2022 Dr. Ullrich Hafner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.parasoft.findings.jenkins.coverage.model;

/**
 * A {@link Node} for a specific class.
 */
public final class ClassNode extends Node {
    private static final long serialVersionUID = 1621410859864978552L;

    /**
     * Creates a new {@link ClassNode} with the given name.
     *
     * @param name
     *         the name of the class
     */
    public ClassNode(final String name) {
        super(Metric.CLASS, name);
    }

    @Override
    public ClassNode copy() {
        return new ClassNode(getName());
    }

    /**
     * Create a new method node with the given method name and signature and add it to the list of children.
     *
     * @param methodName
     *         the method name
     * @param signature
     *         the signature of the method
     *
     * @return the created and linked package node
     */
    public MethodNode createMethodNode(final String methodName, final String signature) {
        var fileNode = new MethodNode(methodName, signature);
        addChild(fileNode);
        return fileNode;
    }
}
