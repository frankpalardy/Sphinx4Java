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

import edu.cmu.sphinx.linguist.acoustic.AcousticModel;
// import edu.cmu.sphinx.linguist.acoustic.AcousticModelFactory;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.TiedStateAcousticModel;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.trainer.TrainerAcousticModel;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.trainer.TrainerScore;
import edu.cmu.sphinx.util.SphinxProperties;
import edu.cmu.sphinx.util.StatisticsVariable;
import edu.cmu.sphinx.util.Timer;
import edu.cmu.sphinx.util.Utilities;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.PropertyType;
import edu.cmu.sphinx.util.props.Registry;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * This is a dummy implementation of a TrainManager.
 */
public class SimpleTrainManager implements TrainManager {

	private Learner learner;

	private Learner flatLearner;

	private ControlFile controlFile;

	private SphinxProperties props; // sphinx properties

	// private TrainerAcousticModel[] models;
	private TrainerAcousticModel model;

	private float minimumImprovement;

	private int maxIteration;

	private boolean dumpMemoryInfo;

	/**
	 * A SphinxProperty name for the boolean property that controls whether or
	 * not the recognizer will display detailed memory information while it is
	 * running. The default value is <code>true</code>.
	 */
	public final static String PROP_DUMP_MEMORY_INFO = PROP_PREFIX
			+ "dumpMemoryInfo";

	/**
	 * The default value for the property PROP_DUMP_MEMORY_INFO.
	 */
	public final static boolean PROP_DUMP_MEMORY_INFO_DEFAULT = false;

	public final static String PROP_ACOUSTIC_MODEL = "trainerModel";

	public final static String PROP_CONTROL_FILE = "controlFile";

	public final static String PROP_LEARNER = "learner";

	public final static String PROP_FLATLEARNER = "flatLearner";

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
		registry.register(PROP_ACOUSTIC_MODEL, PropertyType.COMPONENT);
		registry.register(PROP_FLATLEARNER, PropertyType.COMPONENT);
		registry.register(PROP_LEARNER, PropertyType.COMPONENT);
		registry.register(PROP_CONTROL_FILE, PropertyType.COMPONENT);
		registry.register(PROP_MINIMUM_IMPROVEMENT, PropertyType.FLOAT);
		registry.register(PROP_MAXIMUM_ITERATION, PropertyType.INT);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#newProperties(edu.cmu.sphinx.util.props.PropertySheet)
	 */
	public void newProperties(PropertySheet ps) throws PropertyException {
		model = (TrainerAcousticModel) ps.getComponent(PROP_ACOUSTIC_MODEL,
				AcousticModel.class);
		minimumImprovement = ps.getFloat(PROP_MINIMUM_IMPROVEMENT,
				PROP_MINIMUM_IMPROVEMENT_DEFAULT);
		maxIteration = ps.getInt(PROP_MAXIMUM_ITERATION,
				PROP_MAXIMUM_ITERATION_DEFAULT);
		flatLearner = (Learner) ps
				.getComponent(PROP_FLATLEARNER, Learner.class);
		learner = (Learner) ps.getComponent(PROP_LEARNER, Learner.class);
		controlFile = (ControlFile) ps.getComponent(PROP_CONTROL_FILE,
				ControlFile.class);
	}

	/**
	 * Initializes the TrainManager with the proper context.
	 */
	public void initialize() {
		try {
			model.load();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Starts the TrainManager.
	 */
	public void start() {
	}

	/**
	 * Stops the TrainManager.
	 */
	public void stop() {
	}

	/**
	 * Do the train.
	 */
	public void train() {
		// assert controlFile != null;
		for (controlFile.startUtteranceIterator(); controlFile
				.hasMoreUtterances();) {
			Utterance utterance = controlFile.nextUtterance();
			System.out.println(utterance);
			for (utterance.startTranscriptIterator(); utterance
					.hasMoreTranscripts();) {
				System.out.println(utterance.nextTranscript());
			}
		}
	}

	/**
	 * Copy the model.
	 * 
	 * This method copies to model set, possibly to a new location and new
	 * format. This is useful if one wants to convert from binary to ascii and
	 * vice versa, or from a directory structure to a JAR file. If only one
	 * model is used, then name can be null.
	 * 
	 * @param context
	 *            this TrainManager's context
	 * 
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	public void copyModels(String context) throws IOException {
		// loadModels(context);
		// saveModels(context);
	}

	/**
	 * Saves the acoustic models.
	 * 
	 * @param context
	 *            the context of this TrainManager
	 * 
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	public void saveModels(String context) throws IOException {
		model.save(null);
		/*
		 * if (1 == models.length) { models[0].save(null); } else { String name;
		 * Collection modelList = AcousticModelFactory.getNames(props); for
		 * (Iterator i = modelList.iterator(); i.hasNext();) { name = "one"; try {
		 * AcousticModel model = AcousticModelFactory.getModel(props, name); if
		 * (model instanceof TrainerAcousticModel) { TrainerAcousticModel tmodel =
		 * (TrainerAcousticModel) model; tmodel.save(name); } } catch
		 * (InstantiationException ie) { ie.printStackTrace(); throw new
		 * IOException("InstantiationException occurred."); } } }
		 */
	}

	/**
	 * Loads the acoustic models.
	 * 
	 * @param context
	 *            the context of this TrainManager
	 */
	private void loadModels() throws IOException {

		// props = SphinxProperties.getSphinxProperties(context);
		// dumpMemoryInfo = props.getBoolean(PROP_DUMP_MEMORY_INFO,
		// PROP_DUMP_MEMORY_INFO_DEFAULT);

		// dumpMemoryInfo("TrainManager start");

		// models = getTrainerAcousticModels(context);
		// for (int m = 0; m < models.length; m++) {
		// models[m].load();
		// }
		// dumpMemoryInfo("acoustic model");
		model.load();

	}

	/**
	 * Initializes the acoustic models.
	 * 
	 * @param context
	 *            the context of this TrainManager
	 */
	public void initializeModels(String context) throws IOException {
		TrainerScore score[];
		props = SphinxProperties.getSphinxProperties(context);
		dumpMemoryInfo = props.getBoolean(PROP_DUMP_MEMORY_INFO,
				PROP_DUMP_MEMORY_INFO_DEFAULT);

		dumpMemoryInfo("TrainManager start");

		// models = getTrainerAcousticModels(context);
		// for (int m = 0; m < models.length; m++) {

		// learner = new FlatInitializerLearner(props);
		// if (controlFile == null) {
		// controlFile = new SimpleControlFile(context);
		// }

		// I had to add these two lines.
		controlFile.allocate();
		model.initialize("loader", "context");
		model.resetBuffers();
		for (controlFile.startUtteranceIterator(); controlFile
				.hasMoreUtterances();) {
			Utterance utterance = controlFile.nextUtterance();
			flatLearner.setUtterance(utterance);
			while ((score = flatLearner.getScore()) != null) {
				// assert score.length == 1;
				// models[m].accumulate(0, score);
				model.accumulate(0, score);
			}
		}
		// normalize() has a return value, but we can ignore it here.
		// float dummy = models[m].normalize();
		float dummy = model.normalize();
		// }
		dumpMemoryInfo("acoustic model");
	}

	/**
	 * Gets an array of models.
	 * 
	 * @param context
	 *            the context of interest
	 * 
	 * @return the AcousticModel(s) used by this Recognizer, not initialized
	 */
	/*
	 * protected TrainerAcousticModel[] getTrainerAcousticModels(String context)
	 * throws IOException { SphinxProperties props =
	 * SphinxProperties.getSphinxProperties(context); List modelList = new
	 * ArrayList(); Collection modelNames =
	 * AcousticModelFactory.getNames(props);
	 * 
	 * for (Iterator i = modelNames.iterator(); i.hasNext();) { String modelName =
	 * (String) i.next(); try { AcousticModel model =
	 * AcousticModelFactory.getModel(props, modelName); if (model instanceof
	 * TrainerAcousticModel) { modelList.add(model); } } catch
	 * (InstantiationException ie) { ie.printStackTrace(); throw new
	 * IOException("InstantiationException occurred."); } } return
	 * (TrainerAcousticModel[]) modelList.toArray(new
	 * TrainerAcousticModel[modelList.size()]); }
	 */
	/**
	 * Trains context independent models. If the initialization stage was
	 * skipped, it loads models from files, automatically.
	 * 
	 * @param context
	 *            the context of this train manager.
	 * 
	 * @throws IOException
	 */
	public void trainContextIndependentModels() throws IOException {
		UtteranceGraph uttGraph;
		TranscriptGraph transcriptGraph;
		TrainerScore[] score;
		TrainerScore[] nextScore;

		dumpMemoryInfo("TrainManager start");

		// assert models != null;
		// models = getTrainerAcousticModels(context);
		// for (int m = 0; m < models.length; m++) {
		float logLikelihood;
		float lastLogLikelihood = Float.MAX_VALUE;
		float relativeImprovement = 100.0f;
		// controlFile.allocate();
		for (int iteration = 0; (iteration < maxIteration)
				&& (relativeImprovement > minimumImprovement); iteration++) {
			System.out.println("Iteration: " + iteration);
			model.resetBuffers();
			// learner.initialize();
			for (controlFile.startUtteranceIterator(); controlFile
					.hasMoreUtterances();) {
				Utterance utterance = controlFile.nextUtterance();
				uttGraph = new UtteranceHMMGraph(utterance, model);
				learner.initializeComputation(utterance, uttGraph);
				nextScore = null;

				while ((score = learner.getScore()) != null) {
					for (int i = 0; i < score.length; i++) {
						if (i > 0) {
							model.accumulate(i, score, nextScore);
						} else {
							model.accumulate(i, score);
						}
					}
					nextScore = score;
				}
				model.updateLogLikelihood();
			}
			logLikelihood = model.normalize();
			// the way normalize works is it is subtracting
			// the denominator by the numerator and calling it dividing
			System.out.println("Loglikelihood: " + logLikelihood);
			// saveModels(context);
			if (iteration > 0) {
				if (lastLogLikelihood != 0) {
					relativeImprovement = (logLikelihood - lastLogLikelihood)
							/ lastLogLikelihood * 100.0f;
				} else if (lastLogLikelihood == logLikelihood) {
					relativeImprovement = 0;
				}
				System.out.println("Finished iteration: " + iteration
						+ " - Improvement: " + relativeImprovement);
			}
			lastLogLikelihood = logLikelihood;
		}
		// }
	}

	/**
	 * Conditional dumps out memory information
	 * 
	 * @param what
	 *            an additional info string
	 */
	private void dumpMemoryInfo(String what) {
		if (dumpMemoryInfo) {
			Utilities.dumpMemoryInfo(what);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.decoder.search.SearchManager#allocate()
	 */
	public void allocate() throws IOException {

		model.allocate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.decoder.search.SearchManager#deallocate()
	 */
	public void deallocate() {

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
