package com.parasoft.findings.jenkins.coverage.api.metrics.steps;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import edu.hm.hafner.coverage.CoverageParser;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.PathUtil;
import edu.hm.hafner.util.SecureXmlParserFactory.ParsingException;

import com.parasoft.findings.jenkins.coverage.api.metrics.steps.CoverageTool.Parser;
import io.jenkins.plugins.util.AgentFileVisitor;

/**
 * Scans the workspace for coverage reports that match a specified Ant file pattern and parse these files with the
 * specified parser. Creates a new {@link ModuleNode} for each parsed file. For files that cannot be read, an empty
 * module node will be returned.
 *
 * @author Ullrich Hafner
 */
public class CoverageReportScanner extends AgentFileVisitor<ModuleNode> {
    private static final long serialVersionUID = 6940864958150044554L;

    private static final PathUtil PATH_UTIL = new PathUtil();
    private final Parser parser;

    /**
     * Creates a new instance of {@link CoverageReportScanner}.
     *
     * @param filePattern
     *         ant file-set pattern to scan for files to parse
     * @param encoding
     *         encoding of the files to parse
     * @param followSymbolicLinks
     *         if the scanner should traverse symbolic links
     * @param parser
     *         the parser to use
     */
    public CoverageReportScanner(final String filePattern, final String encoding,
            final boolean followSymbolicLinks, final Parser parser) {
        super(filePattern, encoding, followSymbolicLinks, true);

        this.parser = parser;
    }

    @Override
    protected Optional<ModuleNode> processFile(final Path file, final Charset charset, final FilteredLog log) {
        try {
            CoverageParser xmlParser = parser.createParser();
            ModuleNode node = xmlParser.parse(Files.newBufferedReader(file, charset), log);
            log.logInfo("Successfully parsed file '%s'", PATH_UTIL.getAbsolutePath(file));
            node.aggregateValues().forEach(v -> log.logInfo("%s", v));
            return Optional.of(node);
        }
        catch (IOException | ParsingException exception) {
            log.logException(exception, "Parsing of file '%s' failed due to an exception:", file);
            return Optional.empty();
        }
    }
}
