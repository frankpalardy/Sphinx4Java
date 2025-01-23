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

package edu.cmu.sphinx.trainer;

import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * Manages the speech recognition for demo trainer
 */
public class TrainerDemo {
	private Trainer trainer;

	/**
	 * Creates the Trainer
	 * 
	 * @throws IOException
	 *             if an error occurs while loading resources
	 */
	public static void main(String[] args) {
		TrainerDemo demo = new TrainerDemo();
	}

	public TrainerDemo() {
		try {
			URL url = this.getClass().getResource("trainer.config.xml");
			if (url == null) {
				System.out.println("Can't find trainer.config.xml");
			}
			ConfigurationManager cm = new ConfigurationManager(url);
			trainer = (Trainer) cm.lookup("trainer");
			trainer.allocate();
		} catch (PropertyException e) {

			e.printStackTrace();

		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Allocates trainer necessary for recognition.
	 */
	public void startup() throws IOException {
		trainer.allocate();
	}

	/**
	 * Releases trainer resources
	 */
	public void shutdown() {

		if (trainer.getState() == TrainerState.ALLOCATED) {
			trainer.deallocate();
		}
	}

}
