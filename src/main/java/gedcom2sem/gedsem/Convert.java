// @formatter:off
/*
 * Copyright 2013, J. Pol
 *
 * This file is part of free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation.
 *
 * This package is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * See the GNU General Public License for more details. A copy of the GNU General Public License is
 * available at <http://www.gnu.org/licenses/>.
 */
// @formatter:on
package gedcom2sem.gedsem;

import gedcom2sem.io.FileNameArguments;
import gedcom2sem.io.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Set;

import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.util.PrintUtil;
import org.gedcom4j.parser.GedcomParserException;

public class Convert
{
    /**
     * Converts a file with extension ged into semantic statements. The gedcom tags are simply turned
     * into predicates and type values.
     * 
     * @throws GedcomParserException
     * @throws IOException
     * @throws FileNotFoundException
     */
    public static void main(final String... files) throws FileNotFoundException, IOException, GedcomParserException
    {
        execute(files);
    }

    static Set<Statement> execute(final String... files) throws IOException, FileNotFoundException, MalformedURLException, GedcomParserException
    {
        if (files == null || files.length < 2)
            throw new IllegalArgumentException("need at least a gedcom file and an output file");

        final FileNameArguments arguments = new FileNameArguments(files);
        final Model model = arguments.readGedcom();
        arguments.readInto(model); // additional triples and/or prefixes
        arguments.write(model); // flush in case rules take too long or too much heap space

        preparePrefixes(model);
        Model infModel = model;
        for (final File file : arguments.getRuleFiles())
        {
            System.err.println("before " + file + " " + infModel.listStatements().toList().size());
            infModel = applyRules(FileUtil.read(file), infModel);
            arguments.write(infModel); // flush
        }
        if (infModel == model)
            return null;
        return getInferredStatements(model.listStatements(), infModel);
    }

    private static Set<Statement> getInferredStatements(final StmtIterator originalStatements, final Model infModel)
    {
        final Set<Statement> statements = infModel.listStatements().toSet();
        System.err.println("finaly: " + statements.size());
        statements.removeAll(originalStatements.toSet());
        System.err.println("inferred: " + statements.size());
        return statements;
    }

    public static void preparePrefixes(final Model model)
    {
        for (final String key:model.getNsPrefixMap().keySet())
            PrintUtil.registerPrefix(key, model.getNsPrefixMap().get(key));
    }

    public static InfModel applyRules(final String rules, final Model model)
    {
        final GenericRuleReasoner reasoner = new GenericRuleReasoner(Rule.parseRules(rules));
        reasoner.setMode(GenericRuleReasoner.FORWARD);

        final InfModel infModel = ModelFactory.createInfModel(reasoner, model);
        infModel.prepare();
        return infModel;
    }
}
