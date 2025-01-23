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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.cmu.sphinx.linguist.acoustic.Context;
import edu.cmu.sphinx.trainer.TrainManager;
import edu.cmu.sphinx.frontend.util.Microphone;
import edu.cmu.sphinx.trainer.StateListener;
import edu.cmu.sphinx.trainer.Trainer;
import edu.cmu.sphinx.trainer.TrainerState;
import edu.cmu.sphinx.util.props.Configurable;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.PropertyType;
import edu.cmu.sphinx.util.props.Registry;

/**
 * Trains models given a set of audio files.
 * 
 * At this point, a very simple file that helps us debug the code.
 */
public class Trainer implements Configurable {

	/**
	 * Prefix for SphinxProperties in this file.
	 */
	public final static String PROP_PREFIX = "edu.cmu.sphinx.trainer.Trainer.";

	/**
	 * The SphinxProperty name for the initial trainer stage to be processed.
	 */
	public final static String PROP_INITIAL_STAGE = PROP_PREFIX
			+ "initialStage";

	/**
	 * Default initial stage.
	 */
	public final static String PROP_INITIAL_STAGE_DEFAULT = "_00_INITIALIZATION";

	/**
	 * The SphinxProperty name for the final trainer stage to be processed.
	 */
	public final static String PROP_FINAL_STAGE = PROP_PREFIX + "finalStage";

	/**
	 * Default final stage.
	 */
	public final static String PROP_FINAL_STAGE_DEFAULT = "_40_TIED_CD_TRAIN";

	/**
	 * Property name for the train manager to be used by this recognizer.
	 */
	public final static String PROP_TRAIN_MANAGER = "trainManager";

	private String initialStage;

	private String finalStage;

	private boolean isStageActive = false;

	private List StageList = new LinkedList();

	private Set StageNames = new HashSet();

	private TrainManager trainManager;

	private String name;

	private TrainerState currentState = TrainerState.DEALLOCATED;

	private List stateListeners = Collections.synchronizedList(new ArrayList());

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#register(java.lang.String,
	 *      edu.cmu.sphinx.util.props.Registry)
	 */
	public void register(String name, Registry registry)
			throws PropertyException {
		this.name = name;
		registry.register(PROP_INITIAL_STAGE, PropertyType.STRING);
		registry.register(PROP_FINAL_STAGE, PropertyType.STRING);
		registry.register(PROP_TRAIN_MANAGER, PropertyType.COMPONENT);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#newProperties(edu.cmu.sphinx.util.props.PropertySheet)
	 */
	public void newProperties(PropertySheet ps) throws PropertyException {
		initialStage = ps.getString(PROP_INITIAL_STAGE,
				PROP_INITIAL_STAGE_DEFAULT);
		finalStage = ps.getString(PROP_FINAL_STAGE, PROP_FINAL_STAGE_DEFAULT);
		trainManager = (TrainManager) ps.getComponent(PROP_TRAIN_MANAGER,
				TrainManager.class);
	}

	/**
	 * Allocate the resources needed for the trainer. Note this method make
	 * take some time to complete. This method should only be called when the
	 * trainer is in the <code> deallocated </code> state.
	 * 
	 * @throws IllegalStateException
	 */
	public void allocate() throws IllegalStateException, IOException {
		checkState(TrainerState.DEALLOCATED);
		setState(TrainerState.ALLOCATING);
		trainManager.initialize();
		setState(TrainerState.ALLOCATED);
		setState(TrainerState.READY);
		init();
		processStages();
	}

	/**
	 * Deallocates the trainer. This method should only be called if the trainer
	 * is in the <code> allocated </code> state.
	 * 
	 * @throws IllegalStateException
	 *             if the trainer is not in the <code>ALLOCATED</code> state
	 */
	public void deallocate() throws IllegalStateException {
		checkState(TrainerState.READY);
		setState(TrainerState.DEALLOCATING);
		trainManager.deallocate();
		setState(TrainerState.DEALLOCATED);
	}

	/**
	 * Checks to ensure that the recognizer is in the given state.
	 * 
	 * @param desiredState
	 *            the state that the recognizer should be in
	 * 
	 * @throws IllegalStateException
	 *             if the recognizer is not in the desired state.
	 */
	private void checkState(TrainerState desiredState) {
		if (currentState != desiredState) {
			throw new IllegalStateException("Expected state " + desiredState
					+ " actual state " + currentState);
		}
	}

	/**
	 * sets the current state
	 * 
	 * @param newState
	 *            the new state
	 */
	private void setState(TrainerState newState) {
		currentState = newState;
		synchronized (stateListeners) {
			for (Iterator i = stateListeners.iterator(); i.hasNext();) {
				StateListener stateListener = (StateListener) i.next();
				stateListener.statusChanged(currentState);
			}
		}
	}

	/**
	 * Retrieves the trainer state. This method can be called in any state.
	 * 
	 * @return the trainer state
	 */
	public TrainerState getState() {
		return currentState;
	}

	/**
	 * Common intialization code
	 * 
	 * @param props
	 *            the sphinx properties
	 */
	private void init() {

		addStage(Stage._00_INITIALIZATION);
		addStage(Stage._10_CI_TRAIN);
		addStage(Stage._20_UNTIED_CD_TRAIN);
		addStage(Stage._30_STATE_PRUNING);
		addStage(Stage._40_TIED_CD_TRAIN);
		addStage(Stage._90_CP_MODEL);
	}

	/**
	 * Add Stage to a list of stages.
	 * 
	 * @param stage
	 *            the Stage to add
	 */
	private void addStage(Stage stage) {
		StageList.add(stage);
		StageNames.add(stage.toString());
	}

	/**
	 * Process this stage.
	 * 
	 * @param context
	 *            this trainer's context
	 */
	private void processStages() {
		// private void processStages(String context) {
		if (!(StageNames.contains(initialStage) && StageNames
				.contains(finalStage))) {
			return;
		}
		for (Iterator iterator = StageList.iterator(); iterator.hasNext();) {
			Stage stage = (Stage) iterator.next();
			if (!isStageActive) {
				if (initialStage.equals(stage.toString())) {
					isStageActive = true;
				}
			}
			if (isStageActive) {
				/*
				 * Not sure of an elegant way to do it. For each stage, it
				 * should call a different method. Switch would be a good
				 * solution, but it works with int, and stage is of type Stage.
				 * 
				 * run();
				 */
				try {
					if (stage.equals(Stage._00_INITIALIZATION)) {
						System.out.println("00 - Initializing");
						trainManager.initializeModels("none");
						System.out.println("Saving initialization");
						trainManager.saveModels("none");
					} else if (stage.equals(Stage._10_CI_TRAIN)) {
						System.out.println("01 - CI train");
						trainManager.allocate();
						trainManager.trainContextIndependentModels();
						System.out.println("Saving CI train");
						trainManager.saveModels("none");
					} else if (stage.equals(Stage._20_UNTIED_CD_TRAIN)) {
						System.out.println("02 - Untied CD train");
					} else if (stage.equals(Stage._30_STATE_PRUNING)) {
						System.out.println("03 - State pruning");
					} else if (stage.equals(Stage._40_TIED_CD_TRAIN)) {
						System.out.println("04 - Tied CD train");
					} else if (stage.equals(Stage._90_CP_MODEL)) {
						System.out.println("trainer not Copying");
						// trainManager.copyModels();
					} else {
						System.out.println("trainer configuring");
					}
				} catch (IOException ioe) {
					ioe.printStackTrace();
					throw new Error("IOE: Can't finish trainer " + ioe, ioe);
				}

				if (finalStage.equals(stage.toString())) {
					isStageActive = false;
				}
			}
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
