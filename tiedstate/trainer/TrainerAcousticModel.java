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

package edu.cmu.sphinx.linguist.acoustic.tiedstate.trainer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;

import edu.cmu.sphinx.linguist.acoustic.UnitManager;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Loader;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Saver;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.TiedStateAcousticModel;
import edu.cmu.sphinx.util.SphinxProperties;
import edu.cmu.sphinx.util.Timer;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.PropertyType;
import edu.cmu.sphinx.util.props.Registry;

/**
 * Represents the generic interface to the Acoustic Model for sphinx4
 */
public class TrainerAcousticModel extends TiedStateAcousticModel {

	/**
	 * The property that defines the component used to load the acoustic model
	 */
	public final static String PROP_INITIAL_LOADER = "initialLoader";

	/**
	 * The property that defines the component used to save the acoustic model
	 */
	public final static String PROP_SAVER = "saveModel";

	protected Loader initialLoader;

	protected Sphinx3Saver saverModel;

	/**
	 * fp added this The property that defines the component used to load the
	 */
	public final static String PROP_HMMPOOLMANAGER = "hmmPoolManager";

	/**
	 * Prefix for acoustic model SphinxProperties.
	 */
	public final static String PROP_PREFIX = "edu.cmu.sphinx.linguist.acoustic.";

	/**
	 * The directory where the acoustic model data can be found.
	 */
	public final static String PROP_LOCATION_SAVE = PROP_PREFIX
			+ "location.save";

	/**
	 * The default value of PROP_LOCATION_SAVE.
	 */
	public final static String PROP_LOCATION_SAVE_DEFAULT = ".";

	/**
	 * The save format for the acoustic model data. Current supported formats
	 * are:
	 * 
	 * sphinx3.ascii sphinx3.binary sphinx4.ascii sphinx4.binary
	 */
	public final static String PROP_FORMAT_SAVE = PROP_PREFIX + "formatSave";
	/**
	 * The default value of PROP_FORMAT_SAVE.
	 */
	public final static String PROP_FORMAT_SAVE_DEFAULT = "sphinx3.binary";

	/**
	 * The file containing the phone list.
	 */
	public final static String PROP_PHONE_LIST = PROP_PREFIX + "phoneList";

	/**
	 * The default value of PROP_PHONE_LIST.
	 */
	public final static String PROP_PHONE_LIST_DEFAULT = "phonelist";

	/**
	 * Flag indicating all models should be operated on.
	 */
	public final static int ALL_MODELS = -1;

	/**
	 * The logger for this class
	 */
	private static Logger logger = Logger.getLogger(PROP_PREFIX
			+ "TrainerAcousticModel");

	/**
	 * The pool manager
	 */
	private HMMPoolManager hmmPoolManager;

	private String format;

	private String phoneList = "";

	private String context;

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#register(java.lang.String,
	 *      edu.cmu.sphinx.util.props.Registry)
	 */
	public void register(String name, Registry registry)
			throws PropertyException {
		super.register(name, registry);
		this.name = name;
		registry.register(PROP_INITIAL_LOADER, PropertyType.COMPONENT);
		registry.register(PROP_SAVER, PropertyType.COMPONENT);
		registry.register(PROP_HMMPOOLMANAGER, PropertyType.COMPONENT);
		registry.register(PROP_FORMAT_SAVE, PropertyType.STRING);
		registry.register(PROP_PHONE_LIST, PropertyType.STRING);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#newProperties(edu.cmu.sphinx.util.props.PropertySheet)
	 */
	public void newProperties(PropertySheet ps) throws PropertyException {
		super.newProperties(ps);
		initialLoader = (Loader) ps.getComponent(PROP_INITIAL_LOADER, Loader.class);
		saverModel = (Sphinx3Saver) ps.getComponent(PROP_SAVER,
				Sphinx3Saver.class);
		hmmPoolManager = (HMMPoolManager) ps.getComponent(PROP_HMMPOOLMANAGER,
				HMMPoolManager.class);
		format = ps.getString(PROP_FORMAT_SAVE, PROP_FORMAT_SAVE_DEFAULT);
		phoneList = ps.getString(PROP_PHONE_LIST, PROP_PHONE_LIST_DEFAULT);
	}

	/**
	 * Initializes the acoustic model
	 * 
	 * @throws IOException
	 *             if the model could not be created
	 */
	public void initialize(String name, String context) throws IOException {
		// initialLoader = new ModelInitializerLoader(name, props);
		// hmmPoolManager = new HMMPoolManager(initialLoader, props);
		initialLoader.load();
		try {
			initialLoader.loadPhoneList(false, phoneList);
		} catch (Exception e) {
			e.printStackTrace();
		}
		hmmPoolManager.init(initialLoader);
		// this.name = name;
		this.context = context;
		// this.props = SphinxProperties.getSphinxProperties(context);
		// this.loadTimer = Timer.getTimer(context, TIMER_LOAD);
		//logInfo();
	}

	/**
	 * Saves the acoustic model with a given name and format
	 * 
	 * @param name
	 *            the name of the acoustic model
	 * 
	 * @throws IOException
	 *             if the model could not be loaded
	 * @throws FileNotFoundException
	 *             if the model does not exist
	 */

	public void save(String name) throws IOException, FileNotFoundException {
		Saver saver;
		if (name != null) {
			// String format = props.getString(formatProp,
			// PROP_FORMAT_SAVE_DEFAULT);
			format = PROP_PREFIX + name + ".format.save";
		}
		if (format.equals("sphinx3.ascii")) {
			logger.info("Sphinx-3 ASCII format");
			// saver = new Sphinx3Saver(name, false, initialLoader);
			saverModel.init(name, false, initialLoader);
		} else if (format.equals("sphinx3.binary")) {
			logger.info("Sphinx-3 binary format");
			// saver = new Sphinx3Saver(name, true, initialLoader);
			saverModel.init(name, true, initialLoader);
		} else if (format.equals("sphinx4.ascii")) {
			logger.info("Sphinx-4 ASCII format");
			saver = new Sphinx4Saver(name, false, initialLoader);
		} else if (format.equals("sphinx4.binary")) {
			logger.info("Sphinx-4 binary format");
			saver = new Sphinx4Saver(name, true, initialLoader);
		} else { // add new saving code here.
			saver = null;
			logger.severe("Unsupported acoustic model format " + format);
		}
	}

	/**
	 * Loads the acoustic models. This has to be explicitly requested in this
	 * class.
	 * 
	 * @throws IOException
	 *             if the model could not be loaded
	 * @throws FileNotFoundException
	 *             if the model does not exist
	 */
	public void load() throws IOException, FileNotFoundException {
		//loadTimer.start();
		initialLoader.load();
		//loadTimer.stop();
		//logInfo();
	}

	/**
	 * Resets the buffers.
	 */
	public void resetBuffers() {
		// Resets the buffers and associated variables.
		hmmPoolManager.resetBuffers();
	}

	/**
	 * Accumulate the current TrainerScore into the buffers.
	 * 
	 * @param index
	 *            the current index into the TrainerScore vector
	 * @param trainerScore
	 *            the TrainerScore in the current frame
	 * @param nextTrainerScore
	 *            the TrainerScore in the next frame
	 */
	public void accumulate(int index, TrainerScore[] trainerScore,
			TrainerScore[] nextTrainerScore) {
		hmmPoolManager.accumulate(index, trainerScore, nextTrainerScore);
	}

	/**
	 * Accumulate the current TrainerScore into the buffers.
	 * 
	 * @param index
	 *            the current index into the TrainerScore vector
	 * @param trainerScore
	 *            the TrainerScore
	 */
	public void accumulate(int index, TrainerScore[] trainerScore) {
		hmmPoolManager.accumulate(index, trainerScore);
	}

	/**
	 * Update the log likelihood. This should be called at the end of each
	 * utterance.
	 */
	public void updateLogLikelihood() {
		hmmPoolManager.updateLogLikelihood();
	}

	/**
	 * Normalize the buffers and update the models.
	 * 
	 * @return the log likelihood for the whole training set
	 */
	public float normalize() {
		float logLikelihood = hmmPoolManager.normalize();
		hmmPoolManager.update();
		return logLikelihood;
	}

}
