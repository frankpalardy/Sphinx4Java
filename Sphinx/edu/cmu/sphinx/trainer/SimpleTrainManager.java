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
import edu.cmu.sphinx.linguist.acoustic.Unit;
// import edu.cmu.sphinx.linguist.acoustic.AcousticModelFactory;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.TiedStateAcousticModel;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.trainer.TrainerAcousticModel;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.trainer.TrainerScore;
import edu.cmu.sphinx.util.LogMath;
import edu.cmu.sphinx.util.SphinxProperties;
import edu.cmu.sphinx.util.StatisticsVariable;
import edu.cmu.sphinx.util.Timer;
import edu.cmu.sphinx.util.Utilities;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.PropertyType;
import edu.cmu.sphinx.util.props.Registry;

import java.util.LinkedHashMap;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class SimpleTrainManager implements TrainManager {

	public final static String PROP_LOG_MATH = "logMath";

	private LogMath logMath;

	private Learner learner;

	private Learner flatLearner;

	private ControlFile controlFile;

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
		registry.register(PROP_LOG_MATH, PropertyType.COMPONENT);
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
		logMath = (LogMath) ps.getComponent(PROP_LOG_MATH, LogMath.class);
		model = (TrainerAcousticModel) ps.getComponent(PROP_ACOUSTIC_MODEL,
				TrainerAcousticModel.class);
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
			model.initialize("a", "b");
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
	public void saveModels(String name) throws IOException {
		model.save(name);
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

		// models = getTrainerAcousticModels(context);
		// for (int m = 0; m < models.length; m++) {
		// models[m].load();
		// }
		// dumpMemoryInfo("acoustic model");
		model.load();
		model.save("model");
	}

	/**
	 * Initializes the acoustic models.
	 * 
	 * @param context
	 *            the context of this TrainManager
	 */
	public void initializeModels(String context) throws IOException {
		TrainerScore score[];
		// models = getTrainerAcousticModels(context);
		// for (int m = 0; m < models.length; m++) {

		controlFile.allocate();
		model.resetBuffers();
		for (controlFile.startUtteranceIterator(); controlFile
				.hasMoreUtterances();) {
			Utterance utterance = controlFile.nextUtterance();
			flatLearner.setUtterance(utterance);
			//Object scores[] = flatLearner.getScore();
			//for (int s = 0; s < scores.length; s++) {
			//	score = (TrainerScore[]) scores[s];	
			while ((score = flatLearner.getScore()) != null) {
				for (int i = 0; i < score.length; i++) {
				// assert score.length == 1;
				// models[m].accumulate(0, score);
				model.accumulate(0, score);
				}
			}
		}
		model.normalize();
		// }
		dumpMemoryInfo("acoustic model");
	}

	/**
	 * Initializes the cd models based on which triphones are in the training
	 * transcript
	 * 
	 * @param context
	 *            the context of this TrainManager
	 */
	public void addCDTriphones(String context) throws IOException {

		UtteranceGraph uttGraph;
		controlFile.allocate();
		LinkedHashMap triPhones = (LinkedHashMap) model.getTriPhones();
		model.resetBuffers();
		// find all triphones that are in all utterances
		for (controlFile.startUtteranceIterator(); controlFile
				.hasMoreUtterances();) {
			Utterance utterance = controlFile.nextUtterance();
			uttGraph = new UtteranceHMMGraph(utterance, model,
					model.unitManager);
			String sil = "SIL";
			String tri;
			for (int i = 0; i < uttGraph.size() - 1; i++) {
				if (i == 0) {
					tri = sil + " " + uttGraph.getNode(i).getID() + " "
							+ uttGraph.getNode(i + 1).getID();
				} else if (uttGraph.getNode(i + 1).getID() == null) {
					tri = uttGraph.getNode(i - 1).getID() + " "
							+ uttGraph.getNode(i).getID() + " " + sil;
					i = uttGraph.size();
				} else {
					tri = uttGraph.getNode(i - 1).getID() + " "
							+ uttGraph.getNode(i).getID() + " "
							+ uttGraph.getNode(i + 1).getID();
				}
				if (triPhones.containsKey(tri)) {
					int t = ((Integer) triPhones.get(tri)).intValue();
					t++;
					triPhones.put(tri, t);
				}
			}
		}
		// check which triphones are above a threshold
		Iterator m = triPhones.keySet().iterator();
		int threshold = 3;
		String key;
		LinkedHashMap triPhonesUsed = new LinkedHashMap();
		while (m.hasNext()) {
			key = (String) m.next();
			if (((Integer) triPhones.get(key)).intValue() >= threshold) {
				triPhonesUsed.put(key, triPhones.get(key));
			}
		}
		model.addCDPools(triPhonesUsed, 3, false);
	}

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
		// model.save("this");
		addCDTriphones("bla");
		UtteranceGraph uttGraph;
		TranscriptGraph transcriptGraph;
		TrainerScore[] score;
		TrainerScore[] nextScore;
		TrainerScore thisScore;
		dumpMemoryInfo("TrainManager start");
		// assert models != null;
		// models = getTrainerAcousticModels(context);
		// for (int m = 0; m < models.length; m++) {
		float totalLogLikelihood = 0;
		;
		float lastLogLikelihood = .00001f;
		float logLikelihood = 0;
		double relativeImprovement = 100.0f;
		controlFile.allocate();
		for (int iteration = 0; (iteration <= maxIteration)
				&& (relativeImprovement > minimumImprovement); iteration++) {

			model.resetBuffers();
			// learner.initialize();
			for (controlFile.startUtteranceIterator(); controlFile
					.hasMoreUtterances();) {
				Utterance utterance = controlFile.nextUtterance();
				uttGraph = new UtteranceHMMGraph(utterance, model,
						model.unitManager);
				learner.initializeComputation(utterance, uttGraph);
				nextScore = null;

				//Object scores[] = learner.getScore();
				while ((score = learner.getScore()) != null) {
					for (int i = 0; i < score.length; i++) {
				//for (int s = 0; s < scores.length; s++) {
				//	score = (TrainerScore[]) scores[s];
				//	for (int i = 0; i < score.length; i++) {
						if (i > 0) {
							model.accumulate(i, score, nextScore);
						} else {
							model.accumulate(i, score);
						}
						score[i].setScalingFactor(score[i].getGamma());
					}
					nextScore = score.clone();
				}
				logLikelihood = TrainerScore.getLogLikelihood();
				totalLogLikelihood = logMath.addAsLinear(
						totalLogLikelihood, logLikelihood);
			}
			model.normalize();
			// saveModels(context);
			System.out.println("lastLoglikelihood: "
					+ logMath.logToLinear(lastLogLikelihood));
			System.out.println("totalLoglikelihood: "
					+ logMath.logToLinear(totalLogLikelihood));
			if (iteration > 0) {

				if (lastLogLikelihood != 0) {
					// this formula is not for logs!
					relativeImprovement = (logMath
							.logToLinear(totalLogLikelihood) - logMath
							.logToLinear(lastLogLikelihood))
							/ (logMath.logToLinear(lastLogLikelihood) * 100.0f);
				} else if (lastLogLikelihood == totalLogLikelihood) {
					relativeImprovement = 0;
				}
				System.out.println("Finished iteration: " + iteration
						+ " - Improvement: " + relativeImprovement);
			}
			lastLogLikelihood = totalLogLikelihood;
			totalLogLikelihood = 0;
		}
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
	 * (TrainerAcousticModel[]) modelList .toArray(new
	 * TrainerAcousticModel[modelList.size()]); }
	 */
}
