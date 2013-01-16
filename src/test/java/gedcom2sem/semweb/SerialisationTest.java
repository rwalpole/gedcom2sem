package gedcom2sem.semweb;

import gedcom2sem.gedsem.Parser;
import gedcom2sem.gedsem.UriFormats;
import gedcom2sem.sem.Extension;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.junit.Test;

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

public class SerialisationTest
{
    private static final String SIMPLE_QUERY = "PREFIX t: <http://genj.sourceforge.net/rdf/GedcomTags/type#> " + //
            "select * {?s a t:INDI}";

    private static final String TMP_MODEL = "target/model.ttl";
    private static final String GEDCOM = "src/test/resources/kennedy.ged";
    private static final String GEDCOM_TTL = new File("src/test/resources/kennedy.ttl").getAbsolutePath();

    @Test
    public void withoutSerialisation() throws Exception
    {
        final BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(GEDCOM));
        final Model model = new Parser().parse(inputStream, new UriFormats().getURIs());
        runQuery(SIMPLE_QUERY, model);
    }

    @Test
    public void withTestData() throws Exception
    {
        final String modelUrl = new File(GEDCOM_TTL).toURI().toURL().toString();
        final Model model = ModelFactory.createDefaultModel();
        model.read(modelUrl, Extension.ttl.language());
        runQuery(SIMPLE_QUERY, model);
    }

    @Test
    public void diySerialisation() throws Exception
    {
        final String modelUrl = new File(TMP_MODEL).toURI().toURL().toString();
        final BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(GEDCOM));
        Model model = new Parser().parse(inputStream, new UriFormats().getURIs());
        model.write(new OutputStreamWriter(new FileOutputStream(TMP_MODEL), "UTF8"), "TURTLE");
        model.close();
        model = ModelFactory.createDefaultModel();
        model.read(modelUrl, "TURTLE");
        runQuery(SIMPLE_QUERY, model);
    }

    private void runQuery(final String queryStr, final Model model) throws IOException
    {
        final QueryExecution queryExecution = QueryExecutionFactory.create(queryStr, Syntax.syntaxARQ, model, new QuerySolutionMap());
        System.out.write(ResultSetFormatter.asText(queryExecution.execSelect()).getBytes());
    }
}