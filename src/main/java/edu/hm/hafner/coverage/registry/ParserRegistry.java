package edu.hm.hafner.coverage.registry;

import edu.hm.hafner.coverage.CoverageParser;
import edu.hm.hafner.coverage.parser.CoberturaParser;
import edu.hm.hafner.coverage.parser.JacocoParser;
import edu.hm.hafner.coverage.parser.PitestParser;

/**
 * Provides a registry for all available {@link CoverageParserType parsers}.
 *
 * @author Ullrich Hafner
 */
public class ParserRegistry {
    /** Supported parsers. */
    public enum CoverageParserType {
        COBERTURA,
        JACOCO,
        PIT
    }

    /**
     * Returns the parser for the specified name.
     *
     * @param parserName
     *         the name of the parser
     *
     * @return the created parser
     */
    public CoverageParser getParser(final String parserName) {
        return getParser(CoverageParserType.valueOf(parserName));
    }

    /**
     * Returns the parser for the specified name.
     *
     * @param parser
     *         the parser
     *
     * @return the created parser
     */
    public CoverageParser getParser(final CoverageParserType parser) {
        switch (parser) {
            case COBERTURA:
                return new CoberturaParser();
            case JACOCO:
                return new JacocoParser();
            case PIT:
                return new PitestParser();
        }
        throw new IllegalArgumentException("Unknown parser type: " + parser);
    }
}
