// Copyright 2008 by Oxford University; see license.txt for details
package org.semanticweb.HermiT.datatypes.rdfplainliteral;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.HermiT.datatypes.ValueSpaceSubset;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.BasicAutomata;
import dk.brics.automaton.BasicOperations;
import dk.brics.automaton.Datatypes;
import dk.brics.automaton.RegExp;

public class RDFPlainLiteralPatternValueSpaceSubset implements ValueSpaceSubset {
    public static final char SEPARATOR='\u0001';
    protected static final Automaton s_separator;
    protected static final Automaton s_languagePatternEnd;
    protected static final Automaton s_languageTag;
    protected static final Automaton s_languageTagOrEmpty;
    protected static final Automaton s_emptyLangTag;
    protected static final Automaton s_nonemptyLangTag;
    protected static final Automaton s_anyLangTag;
    protected static final Automaton s_xsdString;
    protected static final Map<String,Automaton> s_anyDatatype=new HashMap<String,Automaton>();
    protected static final Automaton s_anyString;
    protected static final Automaton s_anyChar;
    protected static final Automaton s_anyStringWithNonemptyLangTag;
    static {
        s_separator=BasicAutomata.makeChar(SEPARATOR);
        s_languagePatternEnd=BasicOperations.optional(BasicAutomata.makeChar('-').concatenate(BasicAutomata.makeAnyString()));
        s_languageTag=languageTagAutomaton();
        s_languageTagOrEmpty=s_languageTag.union(BasicAutomata.makeEmptyString());
        s_emptyLangTag=s_separator;
        s_nonemptyLangTag=s_separator.concatenate(s_languageTag);
        s_anyLangTag=s_separator.concatenate(s_languageTagOrEmpty);
        s_xsdString=Datatypes.get("string");
        s_anyDatatype.put(RDFPlainLiteralDatatypeHandler.XSD_NS+"string",s_xsdString.concatenate(s_emptyLangTag));
        s_anyDatatype.put(RDFPlainLiteralDatatypeHandler.XSD_NS+"normalizedString",normalizedStringAutomaton().concatenate(s_emptyLangTag));
        s_anyDatatype.put(RDFPlainLiteralDatatypeHandler.XSD_NS+"token",tokenAutomaton().concatenate(s_emptyLangTag));
        s_anyDatatype.put(RDFPlainLiteralDatatypeHandler.XSD_NS+"Name",Datatypes.get("Name2").concatenate(s_emptyLangTag));
        s_anyDatatype.put(RDFPlainLiteralDatatypeHandler.XSD_NS+"NCName",Datatypes.get("NCName").concatenate(s_emptyLangTag));
        s_anyDatatype.put(RDFPlainLiteralDatatypeHandler.XSD_NS+"NMTOKEN",Datatypes.get("Nmtoken2").concatenate(s_emptyLangTag));
        s_anyDatatype.put(RDFPlainLiteralDatatypeHandler.XSD_NS+"language",Datatypes.get("language").concatenate(s_emptyLangTag));
        s_anyDatatype.put(RDFPlainLiteralDatatypeHandler.RDF_NS+"PlainLiteral",s_xsdString.concatenate(s_anyLangTag));
        s_anyChar=BasicAutomata.makeAnyChar();
        s_anyString=BasicAutomata.makeAnyString();
        s_anyStringWithNonemptyLangTag=s_anyString.concatenate(s_nonemptyLangTag);
    }
    protected static Automaton languageTagAutomaton() {
        return new RegExp(
            "("+
                "([a-z]{2,3}"+
                    "("+
                        "(-[a-z]{3}){0,3}"+             // extlang
                    ")?"+
                ")|"+
                "[a-z]{4}|"+                            // 4ALPHA
                "[a-z]{5,8}"+                           // 5*8ALPHA
            ")"+                                        // language
            "(-[a-z]{4})?"+                             // script
            "(-([a-z]{2}|[0-9]{3}))?"+                  // region
            "(-([a-z0-9]{5,8}|([0-9][a-z0-9]{3})))*"+   // variant
            "(-([a-wy-z0-9](-[a-z0-9]{2,8})+))*"+       // extension
            "(-x(-[a-z0-9]{1,8})+)?"                    // privateuse
        ).toAutomaton();
    }
    protected static Automaton normalizedStringAutomaton() {
        /* \u0009 \u000D control characters containing Tab, CR, and LF 
           \u0020 SPACE
           XML is allowed to contain unicode code points apart from:
           most of the C0 and C1 control codes: \u0000-\u001F and \u0080�\u009F 
           permanently unassigned: permanently-unassigned code points D800�DFFF
           not allowed: code point ending in FFFE or FFFF  */
        return new RegExp("([\u0020-\u007F\u00A0-\uD7FF\uE000-\uFFFD])*").toAutomaton();
    }
    protected static Automaton tokenAutomaton() {
        return new RegExp("([\u0021-\uD7FF\uE000-\uFFFD]+(\u0020[\u0021-\uD7FF\uE000-\uFFFD]+)*)?").toAutomaton();
    }
    protected final Automaton m_automaton;
    
    public RDFPlainLiteralPatternValueSpaceSubset(Automaton automaton) {
        m_automaton=automaton;
    }
    public boolean hasCardinalityAtLeast(int number) {
        Set<String> elements=m_automaton.getFiniteStrings(number);
        if (elements==null)
            return true;
        else
            return elements.size()>=number;
    }
    public boolean containsDataValue(Object dataValue) {
        if (dataValue instanceof String) {
            String string=(String)dataValue;
            return m_automaton.run(string+SEPARATOR);
        }
        else if (dataValue instanceof RDFPlainLiteralDataValue) {
            RDFPlainLiteralDataValue value=(RDFPlainLiteralDataValue)dataValue;
            String string=value.getString();
            String languageTag=value.getLanguageTag().toLowerCase();
            return m_automaton.run(string+SEPARATOR+languageTag);
        }
        else
            return false;
    }
    public void enumerateDataValues(Collection<Object> dataValues) {
        Set<String> elements=m_automaton.getFiniteStrings();
        if (elements==null)
            throw new IllegalStateException("The value space range is infinite.");
        else {
            for (String element : elements) {
                int separatorIndex=element.lastIndexOf(SEPARATOR);
                String string=element.substring(0,separatorIndex);
                String languageTag=element.substring(separatorIndex+1,element.length());
                if (languageTag.length()==0)
                    dataValues.add(string);
                else
                    dataValues.add(new RDFPlainLiteralDataValue(string,languageTag));
            }
        }
    }
    public String toString() {
        StringBuffer buffer=new StringBuffer();
        buffer.append("rdf:PlainLiteral{");
        buffer.append(m_automaton.toString());
        buffer.append('}');
        return buffer.toString();
    }
    public static Automaton toAutomaton(RDFPlainLiteralLengthValueSpaceSubset valueSpaceSubset) {
        List<RDFPlainLiteralLengthInterval> intervals=valueSpaceSubset.m_intervals;
        Automaton result=null;
        for (int intervalIndex=intervals.size()-1;intervalIndex>=0;--intervalIndex) {
            RDFPlainLiteralLengthInterval interval=intervals.get(intervalIndex);
            Automaton stringPart;
            if (interval.m_maxLength==Integer.MAX_VALUE) {
                if (interval.m_minLength==0)
                    stringPart=s_anyString;
                else
                    stringPart=s_anyString.intersection(BasicOperations.repeat(s_anyChar,interval.m_minLength));
            }
            else
                stringPart=s_anyString.intersection(BasicOperations.repeat(s_anyChar,interval.m_minLength,interval.m_maxLength));
            Automaton intervalAutomaton;
            if (interval.m_languageTagMode==RDFPlainLiteralLengthInterval.LanguageTagMode.ABSENT)
                intervalAutomaton=stringPart.concatenate(s_emptyLangTag);
            else
                intervalAutomaton=stringPart.concatenate(s_nonemptyLangTag);
            if (result==null)
                result=intervalAutomaton;
            else
                result=result.intersection(intervalAutomaton);
        }
        return result;
    }
    public static Automaton toAutomaton(int minLength,int maxLength) {
        assert minLength<=maxLength;
        Automaton stringPart;
        if (maxLength==Integer.MAX_VALUE) {
            if (minLength==0)
                stringPart=s_anyString;
            else
                stringPart=s_anyString.intersection(BasicOperations.repeat(s_anyChar,minLength));
        }
        else
            stringPart=s_anyString.intersection(BasicOperations.repeat(s_anyChar,minLength,maxLength));
        return stringPart.concatenate(s_anyLangTag);
    }
    public static boolean isValidPattern(String pattern) {
        try {
            new RegExp(pattern);
            return true;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }
    public static Automaton getPatternAutomaton(String pattern) {
        Automaton stringPart=new RegExp(pattern).toAutomaton();
        return stringPart.concatenate(s_anyLangTag);
    }
    public static Automaton getLanguageRangeAutomaton(String languageRange) {
        if ("*".equals(languageRange))
            return s_anyStringWithNonemptyLangTag;
        else {
            Automaton languageTagPart=BasicAutomata.makeString(languageRange.toLowerCase()).concatenate(s_languagePatternEnd);
            return s_anyString.concatenate(s_separator).concatenate(languageTagPart);
        }
    }
    public static Automaton getDatatypeAutomaton(String datatypeURI) {
        return s_anyDatatype.get(datatypeURI);
    }
}
