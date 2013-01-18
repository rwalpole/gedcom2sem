// @formatter:off
/*
 * Copyright 2012, J. Pol This file is part of free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software Foundation. This
 * package is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public
 * License for more details. A copy of the GNU General Public License is available at
 * <http://www.gnu.org/licenses/>.
 */
// @formatter:on
package gedcom2sem.gedsem;

import genj.gedcom.time.PointInTime;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gedcom4j.model.StringTree;
import org.gedcom4j.parser.GedcomParserException;
import org.gedcom4j.parser.GedcomReader;

import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;

public class Parser
{
    private static final List<String> datePrefixes = new ArrayList<String>(Arrays.asList(new String[] {//
            "FROM", "TO", "BET", "BEF", "AFT", "ABT", "CAL", "EST"}));

    private final Map<String, String> tagsOfIds = new HashMap<String, String>();

    private SemanticGedcomModel gedcomModel;

    public Model parse(final BufferedInputStream stream, final Map<String, String> uriMap) throws IOException, GedcomParserException
    {
        final List<StringTree> entities = GedcomReader.read(stream);

        tagsOfIds.clear();
        for (final StringTree entity : entities)
            tagsOfIds.put(entity.id, entity.tag);

        gedcomModel = new SemanticGedcomModel(uriMap);
        for (final StringTree entity : entities)
        {
            final Resource resource = gedcomModel.addEntity(entity.id, entity.tag);
            loadProperties(resource, entity.children);
        }
        return gedcomModel.getModel();
    }

    private void loadProperties(final Resource resource, final List<StringTree> properties)
    {
        if (properties == null || properties.size() == 0)
            return;
        for (final StringTree property : concatCONT(properties))
        {
            if (referencesAnotherNode(property))
            {
                for (final String value : property.value.split(" "))
                    gedcomModel.addConnection(resource, value, property.tag, tagsOfIds.get(value));
            }
            else
            {
                final Resource propertyResource;
                if ("NAME".equals(property.tag))
                {
                    propertyResource = gedcomModel.addProperty(resource, property.tag, property.value);
                    loadNameComponents(propertyResource, property.value);
                }
                else if ("DATE".equals(property.tag))
                {
                    Resource simpleFullDate = trySimpleFullDate(resource, property);
                    if (simpleFullDate == null)
                        propertyResource = gedcomModel.addProperty(resource, property.tag, property.value);
                    else
                        propertyResource = simpleFullDate;
                }
                else
                    propertyResource = gedcomModel.addProperty(resource, property.tag, property.value);
                loadProperties(propertyResource, property.children);
            }
        }
    }

    private List<StringTree> concatCONT(final List<StringTree> properties)
    {
        final List<StringTree> result = new ArrayList<StringTree>();
        StringTree last = null;
        for (final StringTree property : properties)
        {
            if (!"CONT".equals(property.tag))
            {
                result.add(property);
                last = property;
            }
            else if (last != null)
            {
                // FIXME never reached, why not?
                last.value = last.value + "\n" + property.value;
            }
        }
        return result;
    }

    private void loadNameComponents(final Resource resource, final String property)
    {
        if (property == null)
            return;
        final String[] name = property.split("/");
        if (name.length > 0)
            gedcomModel.addProperty(resource, "givn", name[0]);
        if (name.length > 1)
            gedcomModel.addProperty(resource, "last", name[1]);
    }

    private Resource trySimpleFullDate(final Resource resource, final StringTree property)
    {
        if (property.value == null //
                || property.value.trim().length() == 0 //
                || datePrefixes.contains(property.value.split(" ")))
            return null;
        final PointInTime pointInTime = new PointInTime();
        pointInTime.set(property.value);
        if (pointInTime.isComplete() && pointInTime.isGregorian())
        {
            final Resource propertyResource = gedcomModel.addProperty(resource, property.tag, null);
            gedcomModel.addLiteral(propertyResource, gregorianToXsd(pointInTime));
            return propertyResource;
        }
        return null;
        // see e.g. http://tech.groups.yahoo.com/group/jena-dev/message/33075
        // String lex=null;
        // RDFDatatype dtype=new XSDYearMonthType(typename);
        // rdfModel.getModel().createTypedLiteral(lex, dtype);
    }

    private static boolean referencesAnotherNode(final StringTree property)
    {
        return property.value != null && property.value.matches("\\@.*\\@");
    }

    private static XSDDateTime gregorianToXsd(final PointInTime pit)
    {
        return new XSDDateTime(new GregorianCalendar(pit.getYear(), pit.getMonth(), pit.getDay() + 2));
    }

}
