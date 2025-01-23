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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import edu.cmu.sphinx.decoder.Decoder;
import edu.cmu.sphinx.frontend.Data;
import edu.cmu.sphinx.frontend.DataEndSignal;
import edu.cmu.sphinx.frontend.DataProcessingException;
import edu.cmu.sphinx.frontend.DataProcessor;
import edu.cmu.sphinx.frontend.DataStartSignal;
import edu.cmu.sphinx.frontend.FrontEnd;
// import edu.cmu.sphinx.frontend.FrontEndFactory;
import edu.cmu.sphinx.frontend.Signal;
import edu.cmu.sphinx.frontend.util.StreamCepstrumSource;
import edu.cmu.sphinx.frontend.util.StreamDataSource;
import edu.cmu.sphinx.linguist.acoustic.AcousticModel;
import edu.cmu.sphinx.linguist.acoustic.HMMState;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.trainer.TrainerAcousticModel;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.trainer.TrainerScore;
import edu.cmu.sphinx.recognizer.RecognizerState;
import edu.cmu.sphinx.util.SphinxProperties;
import edu.cmu.sphinx.util.Utilities;
import edu.cmu.sphinx.util.props.Configurable;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.PropertyType;
import edu.cmu.sphinx.util.props.Registry;

/**
 * Provides mechanisms for computing statistics given a set of states and input
 * data.
 */
public class FlatInitializerLearner implements Learner, Configurable {

	private final static String PROP_PREFIX = "edu.cmu.sphinx.trainer.";

	/**
	 * The SphinxProperty name for the input data type.
	 */
	public final static String PROP_INPUT_TYPE = PROP_PREFIX + "inputDataType";

	/**
	 * The default value for the property PROP_INPUT_TYPE.
	 */
	public final static String PROP_INPUT_TYPE_DEFAULT = "cepstrum";

	/**
	 * The sphinx property for the front end class.
	 */
	// public final static String PROP_FRONT_END = PROP_PREFIX + "frontend";
	public final static String PROP_FRONT_END = "frontend";

	public final static String PROP_DATA_PROCESSOR = "dataProcessor";

	/**
	 * The default value of PROP_FRONT_END.
	 */
	public final static String PROP_FRONT_END_DEFAULT = "edu.cmu.sphinx.frontend.SimpleFrontEnd";

	private FrontEnd frontEnd;

	private DataProcessor dataSource;

	private String context;

	private String inputDataType;

	private SphinxProperties props;

	private Data curFeature;

	/**
	 * Property name for the trainer from recognizer
	 */
	public final static String PROP_FRONTEND = "frontend";

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
		registry.register(PROP_FRONTEND, PropertyType.COMPONENT);
		registry.register(PROP_INPUT_TYPE, PropertyType.STRING);
		registry.register(PROP_DATA_PROCESSOR, PropertyType.COMPONENT);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#newProperties(edu.cmu.sphinx.util.props.PropertySheet)
	 */
	public void newProperties(PropertySheet ps) throws PropertyException {
		frontEnd = (FrontEnd) ps.getComponent(PROP_FRONTEND, FrontEnd.class);
		inputDataType = ps.getString(PROP_INPUT_TYPE, PROP_INPUT_TYPE_DEFAULT);
		dataSource = (DataProcessor) ps.getComponent(PROP_DATA_PROCESSOR,
				DataProcessor.class);
	}

	/**
	 * Constructor for this learner.
	 */
	// public FlatInitializerLearner()
	// throws IOException {
	// initialize();
	// }
	/**
	 * Initializes the Learner with the proper context and frontend.
	 * 
	 * @throws IOException
	 */
	public void initialize() throws IOException {

		if (inputDataType.equals("audio")) {
			// dataSource = new StreamDataSource();
			// dataSource.initialize("batchAudioSource", null, props, null);
			dataSource.initialize();
		} else if (inputDataType.equals("cepstrum")) {
			// dataSource = new StreamCepstrumSource();
			// dataSource.initialize("batchCepstrumSource", null, props, null);
			// there is no such method
			dataSource.initialize();
		} else {
			throw new Error("Unsupported data type: " + inputDataType + "\n"
					+ "Only audio and cepstrum are supported\n");
		}

		// /frontEnd = getFrontEnd(); this isn't needed cause it's registered
	}

	/** this isn't needed cause it's registered
	 * Initialize and return the frontend based on the given sphinx properties.
	 * 
	 * protected FrontEnd getFrontEnd() { String path = null; try { // need to
	 * figure how to set up frontend Collection names =
	 * FrontEndFactory.getNames(props); // assert names.size() == 1; FrontEnd fe =
	 * null; for (Iterator i = names.iterator(); i.hasNext(); ) { String name =
	 * (String) i.next(); fe = FrontEndFactory.getFrontEnd(props, name); }
	 * return fe; } catch (InstantiationException ie) { throw new Error("IE:
	 * Can't create front end " + path, ie); } }
	 * 
	 * /** Sets the learner to use a utterance.
	 * 
	 * @param utterance
	 *            the utterance
	 * 
	 * @throws IOException
	 */
	public void setUtterance(Utterance utterance) throws IOException {
		String file = utterance.toString();

		InputStream is = new FileInputStream(file);

		if (inputDataType.equals("audio")) {
			((StreamDataSource) dataSource).setInputStream(is, file);
		} else if (inputDataType.equals("cepstrum")) {
			boolean bigEndian = Utilities.isCepstraFileBigEndian(file);
			((StreamCepstrumSource) dataSource).setInputStream(is, bigEndian);
		}
	}

	/**
	 * Returns a single frame of speech.
	 * 
	 * @return a feature frame
	 * 
	 * @throws IOException
	 */
	private boolean getFeature() {
		try {
			// I'm using files from an4 so I don't want get front end
			//curFeature = frontEnd.getData(); 
			curFeature =  dataSource.getData();

			if (curFeature == null) {
				return false;
			}

			if (curFeature instanceof DataStartSignal) {
				//curFeature = frontEnd.getData();
				curFeature = dataSource.getData();
				if (curFeature == null) {
					return false;
				}
			}

			if (curFeature instanceof DataEndSignal) {
				return false;
			}

			if (curFeature instanceof Signal) {
				throw new Error("Can't score non-content feature");
			}

		} catch (DataProcessingException dpe) {
			System.out.println("DataProcessingException " + dpe);
			dpe.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Starts the Learner.
	 */
	public void start() {
	}

	/**
	 * Stops the Learner.
	 */
	public void stop() {
	}

	/**
	 * Initializes computation for current utterance and utterance graph.
	 * 
	 * @param utterance
	 *            the current utterance
	 * @param graph
	 *            the current utterance graph
	 * 
	 * @throws IOException
	 */
	public void initializeComputation(Utterance utterance, UtteranceGraph graph)
			throws IOException {
		setUtterance(utterance);
		setGraph(graph);
	}

	/**
	 * Implements the setGraph method. Since the flat initializer does not need
	 * a graph, this method produces an error.
	 * 
	 * @param graph
	 *            the graph
	 */
	public void setGraph(UtteranceGraph graph) {
		new Error("Flat initializer does not use a graph!");
	}

	/**
	 * Gets the TrainerScore for the next frame
	 * 
	 * @return the TrainerScore
	 */
	public TrainerScore[] getScore() {
		// If getFeature() is true, curFeature contains a valid
		// Feature. If not, a problem or EOF was encountered.
		ArrayList scoreList = new ArrayList();
		if (getFeature()) {
			// Since it's flat initialization, the probability is
			// neutral, and the senone means "all senones".
			TrainerScore[] score = new TrainerScore[1];
			// might need to initialize all the gammas etc.
			score[0] = new TrainerScore(curFeature, 0.0f,
					TrainerAcousticModel.ALL_MODELS);
			//scoreList.add(score);
		//} 
		//return scoreList.toArray();
			return score;
		} else {
			return null;
		}
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
