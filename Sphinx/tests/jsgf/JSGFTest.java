/*
 * Copyright 1999-2004 Carnegie Mellon University.
 * Portions Copyright 2004 Sun Microsystems, Inc.
 * Portions Copyright 2004 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL
 * WARRANTIES.
 *
 */

package tests.jsgf;

import edu.cmu.sphinx.jsapi.JSGFGrammar;
import edu.cmu.sphinx.util.props.ConfigurationManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import edu.cmu.sphinx.util.props.PropertyException;
import javax.speech.recognition.RuleGrammar;
import javax.speech.recognition.RuleParse;
import javax.speech.recognition.GrammarException;


/**
 * A test program for jsgf grammars. This program will generate a number of
 * random sentences from a JSGF grammar and attempt to validate them
 * via a rule parse.  A count of valid and invalid sentences is
 * reported after the run.  
 *
 * This test will detect and reports only certain grammar failures.
 * It will detect when a JSGFGrammar generates invalid sentences. It
 * will not detect the case where a valid sentence can never be
 * generated by the JSGFGrammar.
 *
 * Note that there is a bug in the Rule Grammar parse where rules with
 * nested recursion cause a stack overflow error.
 *
 * Usage:  JSGFTest config.xml  count
 */
public class JSGFTest {
    /**
     * Main method for running the HelloDigits demo.
     */
    public static void main(String[] args) {
        int match = 0;
        int noMatch = 0;
        if (args.length != 2) {
            System.out.println("Usage: JSGFTest config.xml count");
            System.exit(0);
        }

        try {
            URL url = new File(args[0]).toURI().toURL();
            int count = Integer.parseInt(args[1]);
            ConfigurationManager cm = new ConfigurationManager(url);

	    JSGFGrammar jsgfGrammar = (JSGFGrammar) cm.lookup("jsgfGrammar");

            jsgfGrammar.allocate();

            RuleGrammar rg = jsgfGrammar.getRuleGrammar();
            for (int i = 0; i < count; i++) {
                String sentence = jsgfGrammar.getRandomSentence();
                // System.out.println("Sentence is : " + sentence);
                RuleParse rp = rg.parse(sentence, null);
                if (rp == null) {
                    noMatch++;
                    System.out.println("Fail: " + sentence);
                } else {
                    match++;
                }
            }
            System.out.println("JSGFTest: Matches: " + match 
                    + " Failures: " + noMatch);
        } catch (IOException e) {
            System.err.println("Problem when loading JSGFTest: " + e);
            e.printStackTrace();
        } catch (PropertyException e) {
            System.err.println("Problem configuring JSGFTest: " + e);
            e.printStackTrace();
        } catch (InstantiationException e) {
            System.err.println("Problem creating JSGFTest: " + e);
            e.printStackTrace();
        } catch (GrammarException e) {
            System.err.println("Problem parsing result: " + e);
            e.printStackTrace();
        }
    }
}
