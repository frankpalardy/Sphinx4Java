/*
 * Copyright 1999-2002 Carnegie Mellon University.  
 * Portions Copyright 2002 Sun Microsystems, Inc.  
 * Portions Copyright 2002 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */

package edu.cmu.sphinx.trainer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import edu.cmu.sphinx.frontend.FrontEnd;
import edu.cmu.sphinx.linguist.dictionary.Dictionary;
import edu.cmu.sphinx.util.SphinxProperties;
import edu.cmu.sphinx.util.props.Configurable;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.PropertyType;
import edu.cmu.sphinx.util.props.Registry;

/**
 * Provides mechanisms for accessing a next utterance's file name and
 * transcription.
 */
public class SimpleControlFile implements ControlFile, Configurable {

	String PROP_DICTIONARY = "dictionary";

	String PROP_DICTIONARY_DEFAULT = "dic";

	private SphinxProperties props; // the sphinx properties

	private String audioFile; // the audio file

	private String transcriptFile; // the transcript file

	private Dictionary dictionary; // the dictionary

	private String wordSeparator; // the word separator

	private int currentPartition; // the current partition

	private int numberOfPartitions; // total number of partitions

	private Iterator audioFileIterator; // iterator for the control file

	private Iterator transcriptFileIterator; // iterator for the

	// transcriptions

	private List audioFileList; // list containing the audio files

	private List transcriptFileList; // list containing the transcriptions

	/*
	 * The logger for this class
	 */
	private static Logger logger = Logger
			.getLogger("edu.cmu.sphinx.trainer.SimpleControlFile");

	private String name;

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#register(java.lang.String,
	 *      edu.cmu.sphinx.util.props.Registry)
	 */
	public void register(String name, Registry registry)
			throws PropertyException {
		this.name = name;
		registry.register(PROP_AUDIO_FILE, PropertyType.STRING);
		registry.register(PROP_TRANSCRIPT_FILE, PropertyType.STRING);
		registry.register(PROP_DICTIONARY, PropertyType.COMPONENT);
		registry.register(PROP_WHICH_BATCH, PropertyType.INT);
		registry.register(PROP_TOTAL_BATCHES, PropertyType.INT);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#newProperties(edu.cmu.sphinx.util.props.PropertySheet)
	 */
	public void newProperties(PropertySheet ps) throws PropertyException {
		audioFile = ps.getString(PROP_AUDIO_FILE, PROP_AUDIO_FILE_DEFAULT);
		transcriptFile = ps.getString(PROP_TRANSCRIPT_FILE,
				PROP_TRANSCRIPT_FILE_DEFAULT);
		dictionary = (Dictionary) ps.getComponent(PROP_DICTIONARY,
				Dictionary.class);
		currentPartition = ps
				.getInt(PROP_WHICH_BATCH, PROP_WHICH_BATCH_DEFAULT);
		numberOfPartitions = ps.getInt(PROP_TOTAL_BATCHES,
				PROP_TOTAL_BATCHES_DEFAULT);
	}

	public void initialize() {

	}

	public void allocate() {

		logger.info("Audio control file: " + this.audioFile);
		logger.info("Transcript file: " + this.transcriptFile);
		logger.info("Dictionary file: " + this.dictionary);
		try {
			dictionary.allocate();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		this.wordSeparator = " \t\n\r\f"; // the white spaces
		logger.info("Processing part " + this.currentPartition + " of "
				+ this.numberOfPartitions);
		try {
			this.audioFileList = getLines(audioFile);
		} catch (IOException ioe) {
			throw new Error("IOE: Can't open file " + audioFile, ioe);
		}
		try {
			this.transcriptFileList = getLines(transcriptFile);
		} catch (IOException ioe) {
			throw new Error("IOE: Can't open file " + transcriptFile, ioe);
		}
	}

	/**
	 * Gets an iterator for utterances.
	 */
	public void startUtteranceIterator() {
		audioFileIterator = audioFileList.iterator();
		transcriptFileIterator = transcriptFileList.iterator();
	}

	/**
	 * Returns whether there is another utterance.
	 * 
	 * @return true if there is another utterance.
	 */
	public boolean hasMoreUtterances() {
		// Should throw exception or break if one has next and the
		// other doesn't.
		return (audioFileIterator.hasNext() && transcriptFileIterator.hasNext());
	}

	/**
	 * Gets the next utterance.
	 * 
	 * @return the next utterance.
	 */
	public Utterance nextUtterance() {
		String utteranceLine = (String) audioFileIterator.next();
		Utterance utterance = new SimpleUtterance(utteranceLine);
		String utteranceFilename = utteranceLine.replaceFirst("^.*/", "")
				.replaceFirst("\\..*$", "");
		String transcriptLine = (String) transcriptFileIterator.next();
		// Finds out if the audio file name is part of the transcript line
		// assert transcriptLine.matches(".*[ \t]\\(" + utteranceFilename +
		// "\\)$") :
		// "File name in transcript \"" + transcriptLine +
		// "\" and control file \"" + utteranceFilename +
		// "\" have to match.";
		// Removes the filename from the transcript line.
		// The transcript line is of the form:
		// She washed her dark suit (st002)
		String transcript = transcriptLine.replaceFirst("[ \t]\\(.*\\)$", "");
		utterance.add(transcript, dictionary, true, wordSeparator);
		return utterance;
	}

	// Next method copied from decoder.BatchDecoder

	/**
	 * Gets the set of lines from the file.
	 * 
	 * @param file
	 *            the name of the file
	 * @throws IOException
	 *             if error occurs while reading file
	 */
	private List getLines(String file) throws IOException {
		List list = new ArrayList();
		BufferedReader reader = new BufferedReader(new FileReader(file));

		String line = null;

		while ((line = reader.readLine()) != null) {
			list.add(line);
		}
		reader.close();

		if (numberOfPartitions > 1) {
			int linesPerBatch = list.size() / numberOfPartitions;
			if (linesPerBatch < 1) {
				linesPerBatch = 1;
			}
			if (currentPartition >= numberOfPartitions) {
				currentPartition = numberOfPartitions - 1;
			}
			int startLine = currentPartition * linesPerBatch;
			// last batch needs to get all remaining lines
			if (currentPartition == (numberOfPartitions - 1)) {
				list = list.subList(startLine, list.size());
			} else {
				list = list.subList(startLine, startLine + linesPerBatch);
			}
		}
		return list;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#getName()
	 */
	public String getName() {
		return name;
	}
}
