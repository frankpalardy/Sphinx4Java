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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamCorruptedException;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;
import java.io.DataInputStream;
import java.util.Properties;
import java.io.BufferedInputStream;

import edu.cmu.sphinx.linguist.acoustic.Context;
import edu.cmu.sphinx.linguist.acoustic.AcousticModel;
import edu.cmu.sphinx.linguist.acoustic.HMM;
import edu.cmu.sphinx.linguist.acoustic.HMMPosition;
import edu.cmu.sphinx.linguist.acoustic.LeftRightContext;
import edu.cmu.sphinx.linguist.acoustic.Unit;
import edu.cmu.sphinx.linguist.acoustic.UnitManager;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.GaussianMixture;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.HMMManager;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Loader;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.MixtureComponent;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Pool;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Senone;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.SenoneHMM;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.SenoneSequence;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Sphinx3Loader;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.TiedStateAcousticModel;
import edu.cmu.sphinx.util.ExtendedStreamTokenizer;
import edu.cmu.sphinx.util.LogMath;
import edu.cmu.sphinx.util.SphinxProperties;
import edu.cmu.sphinx.util.StreamFactory;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.PropertyType;
import edu.cmu.sphinx.util.props.Registry;
import edu.cmu.sphinx.util.Utilities;

/**
 * an acoustic model loader that initializes models
 * 
 * Mixture weights and transition probabilities are maintained in logMath log
 * base,
 */
public class ModelInitializerLoader implements Loader {
	
	UnitManager unitManager;

	/**
	 * The SphinxProperty for the name of the acoustic properties file.
	 */
	public final static String PROP_PROPERTIES_FILE = "propertiesFile";

	/**
	 * Subdirectory where the acoustic model can be found
	 */
	public final static String PROP_DATA_LOCATION = "dataLocation";

	/**
	 * The default value of PROP_DATA_LOCATION.
	 */
	public final static String PROP_DATA_LOCATION_DEFAULT = "data";

	/**
	 * The default value of PROP_PROPERTIES_FILE.
	 */
	public final static String PROP_PROPERTIES_FILE_DEFAULT = "model.props";

	public final static String PROP_LOG_MATH = "logMath";

	private final static String NUM_SENONES = "num_senones";

	private final static String NUM_GAUSSIANS_PER_STATE = "num_gaussians";

	private final static String NUM_STREAMS = "num_streams";

	private final static String FILLER = "filler";

	private final static String SILENCE_CIPHONE = "SIL";

	private final static int BYTE_ORDER_MAGIC = 0x11223344;

	public final static String MODEL_VERSION = "0.3";

	/**
	 * The SphinxProperty specifying whether the transition matrices of the
	 * acoustic model is in sparse form, i.e., omitting the zeros of the
	 * non-transitioning states.
	 */
	public final static String PROP_SPARSE_FORM = "sparseForm";

	/**
	 * The default value of PROP_SPARSE_FORM.
	 */
	public final static boolean PROP_SPARSE_FORM_DEFAULT = true;

	private final static int CONTEXT_SIZE = 1;

	private Pool meansPool;

	private Pool variancePool;

	private Pool matrixPool;

	private Pool meanTransformationMatrixPool;

	private Pool meanTransformationVectorPool;

	private Pool varianceTransformationMatrixPool;

	private Pool varianceTransformationVectorPool;

	private Pool mixtureWeightsPool;

	private Pool senonePool;

	private int vectorLength;

	private Map contextIndependentUnits;

	private Map phoneList;

	private HMMManager hmmManager;

	private LogMath logMath;

	private SphinxProperties acousticProperties;

	private boolean binary = false;

	private String location;

	String prefix, phone, dataDir, propsFile;

	private boolean swap;

	boolean useCDUnits;

	private final static String DENSITY_FILE_VERSION = "1.0";

	private final static String MIXW_FILE_VERSION = "1.0";

	private final static String TMAT_FILE_VERSION = "1.0";

	private int numSenones;

	private int numStreams;

	private int numGaussiansPerState;

	float distFloor;

	float mixtureWeightFloor;

	float transitionProbabilityFloor;

	float varianceFloor;

	public final static String HMM_MANAGER = "hmmManager";

	String PROP_VECTOR_LENGTH = "vectorLength";

	int PROP_VECTOR_LENGTH_DEFAULT = 0;

	String PROP_MC_FLOOR = "MCFloor";

	float PROP_MC_FLOOR_DEFAULT = 0;

	String PROP_MW_FLOOR = "MWFloor";

	float PROP_MW_FLOOR_DEFAULT = 0;

	String PROP_TP_FLOOR = "TPFloor";

	float PROP_TP_FLOOR_DEFAULT = 0;

	String PROP_VARIANCE_FLOOR = "varianceFloor";

	float PROP_VARIANCE_FLOOR_DEFAULT = 0;

	String PROP_USE_CD_UNITS = "useCDUnits";
	
	String PROP_UNIT_MANAGER = "unitManager";

	private String name;

	protected boolean sparseForm;

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
        registry.register(PROP_UNIT_MANAGER, PropertyType.COMPONENT);
		registry.register(NUM_SENONES, PropertyType.INT);
		registry.register(NUM_STREAMS, PropertyType.INT);
		registry.register(NUM_GAUSSIANS_PER_STATE, PropertyType.INT);
		registry.register(PROP_VECTOR_LENGTH, PropertyType.INT);
		registry.register(PROP_DATA_LOCATION, PropertyType.STRING);
		registry.register(PROP_SPARSE_FORM, PropertyType.BOOLEAN);
		registry.register(PROP_MC_FLOOR, PropertyType.FLOAT);
		registry.register(PROP_MW_FLOOR, PropertyType.FLOAT);
		registry.register(PROP_TP_FLOOR, PropertyType.FLOAT);
		registry.register(PROP_VARIANCE_FLOOR, PropertyType.FLOAT);
		registry.register(PROP_USE_CD_UNITS, PropertyType.BOOLEAN);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#newProperties(edu.cmu.sphinx.util.props.PropertySheet)
	 */
	public void newProperties(PropertySheet ps) throws PropertyException {
		logMath = (LogMath) ps.getComponent(PROP_LOG_MATH, LogMath.class);
        unitManager = 
            (UnitManager) ps.getComponent(PROP_UNIT_MANAGER,
                                          UnitManager.class);
		numSenones = ps.getInt(NUM_SENONES, 0);
		numStreams = ps.getInt(NUM_STREAMS, 0);
		numGaussiansPerState = ps.getInt(NUM_GAUSSIANS_PER_STATE, 1);
		sparseForm = ps.getBoolean(PROP_SPARSE_FORM, PROP_SPARSE_FORM_DEFAULT);
		vectorLength = ps
				.getInt(PROP_VECTOR_LENGTH, PROP_VECTOR_LENGTH_DEFAULT);
		dataDir = ps.getString(PROP_DATA_LOCATION, PROP_DATA_LOCATION_DEFAULT);
		distFloor = ps.getFloat(PROP_MC_FLOOR, PROP_MC_FLOOR_DEFAULT);
		mixtureWeightFloor = ps.getFloat(PROP_MW_FLOOR, PROP_MW_FLOOR_DEFAULT);
		transitionProbabilityFloor = ps.getFloat(PROP_TP_FLOOR,
				PROP_TP_FLOOR_DEFAULT);
		varianceFloor = ps.getFloat(PROP_VARIANCE_FLOOR,
				PROP_VARIANCE_FLOOR_DEFAULT);
		useCDUnits = ps.getBoolean(PROP_USE_CD_UNITS, true);
	}
	   /*
     * (non-Javadoc)
     * 
     * @see edu.cmu.sphinx.util.props.Configurable#getName()
     */
    public String getName() {
        return name;
    }

    public void load() throws IOException {
        // TODO: what is this all about?
        hmmManager = new HMMManager();
        contextIndependentUnits = new LinkedHashMap();
        // dummy pools for these elements
        meanTransformationMatrixPool = 
            createDummyMatrixPool("meanTransformationMatrix");
        meanTransformationVectorPool = 
            createDummyVectorPool("meanTransformationMatrix");
        varianceTransformationMatrixPool = 
            createDummyMatrixPool("varianceTransformationMatrix");
        varianceTransformationVectorPool = 
            createDummyVectorPool("varianceTransformationMatrix");
        // do the actual acoustic model loading
        loadModelFiles("model");
    }
	/**
	 * Creates a dummy model.
	 * 
	 * @param modelName
	 *            the name of the model as specified in the props file.
	 * @param props
	 *            the SphinxProperties object public
	 *            ModelInitializerLoader(String modelName, SphinxProperties
	 *            props)
	 */

	public void initialize() {

		hmmManager = new HMMManager();
		
		contextIndependentUnits = new LinkedHashMap();
		phoneList = new LinkedHashMap();

		// dummy pools for these elements
		meanTransformationMatrixPool = createDummyMatrixPool("meanTransformationMatrix");
		meanTransformationVectorPool = createDummyVectorPool("meanTransformationMatrix");
		varianceTransformationMatrixPool = createDummyMatrixPool("varianceTransformationMatrix");
		varianceTransformationVectorPool = createDummyVectorPool("varianceTransformationMatrix");
	}

	/**
	 * Prints out a help message with format of phone list.
	 */
	public void printPhoneListHelp() {
		System.out.println("The format for the phone list file is:");
		System.out.println("\tversion 0.1");
		System.out.println("\tsame_sized_models yes");
		System.out.println("\tn_state 3");
		System.out.println("\ttmat_skip (no|yes)");
		System.out.println("\tAA");
		System.out.println("\tAE");
		System.out.println("\tAH");
		System.out.println("\t...");
		System.out.println("Or:");
		System.out.println("\tversion 0.1");
		System.out.println("\tsame_sized_models no");
		System.out.println("\ttmat_skip (no|yes)");
		System.out.println("\tAA 5");
		System.out.println("\tAE 3");
		System.out.println("\tAH 4");
		System.out.println("\t...");
	}
	/**
	 * Loads the AcousticModel from a directory in the file system.
	 * 
	 * @param modelName
	 *            the name of the acoustic model; if null we just load from the
	 *            default location
	 */
	private void loadModelFiles(String modelName) throws FileNotFoundException,
			IOException, ZipException {

		if (binary) {
			meansPool = loadDensityFileBinary("means",
					-Float.MAX_VALUE);
			variancePool = loadDensityFileBinary("variances",
					varianceFloor);
			mixtureWeightsPool = loadMixtureWeightsBinary("mixture_weights", mixtureWeightFloor);
			matrixPool = loadTransitionMatricesBinary("transition_matrices");
		} else {
			meansPool = loadDensityFileAscii("means.ascii",
					-Float.MAX_VALUE);
			variancePool = loadDensityFileAscii("variances.ascii",
					varianceFloor);
			mixtureWeightsPool = loadMixtureWeightsAscii("mixture_weights.ascii", mixtureWeightFloor);
			matrixPool = loadTransitionMatricesAscii("transition_matrices.ascii");
		}
		senonePool = createSenonePool(distFloor, varianceFloor);
		// load the HMM model file
		//InputStream modelStream = getClass().getResourceAsStream(model);
		InputStream modelStream = StreamFactory.getInputStream(dataDir, "mdef");
		if (modelStream == null) {
			throw new IOException("can't find model " + modelName);
		}
		loadHMMPool(useCDUnits, modelStream, location + File.separator + modelName);
		System.out.println("here");
	}


	/**
	 * Loads the phone list
	 * 
	 * @param useCDUnits
	 *            if true, uses context dependent units
	 * @param inputStream
	 *            the open input stream to use
	 * @param path
	 *            the path to a density file
	 * @param props
	 *            the properties for this set of HMMs
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	public void loadPhoneList(boolean useCDUnits, String path)
			throws FileNotFoundException, IOException {
		int pivot = path.lastIndexOf('/') + 1;
		String location = path.substring(0, pivot);
		String file = path.substring(pivot, path.length());
		InputStream inputStream = StreamFactory.getInputStream(location, file);
		boolean sameSizedModels;
		int numState = 0;
		boolean tmatSkip;
		ExtendedStreamTokenizer est = new ExtendedStreamTokenizer(inputStream,
				'#', false);
		String version = "0.1";
		est.expectString("version");
		est.expectString(version);
		est.expectString("same_sized_models");
		sameSizedModels = est.getString().equals("yes");
		if (sameSizedModels) {
			est.expectString("n_state");
			numState = est.getInt("numBase");
		}
		est.expectString("tmat_skip");
		tmatSkip = est.getString().equals("yes");
		int size = 1;
		while (!est.isEOF()) {
			String phone = est.getString();
			if (!(phone == null)) {
				size = numState;
				if (!sameSizedModels) {
					size = est.getInt("ModelSize");
				}
				phoneList.put(phone, new Integer(size));
			}
		}
		est.close();
		addPools(phoneList, size, tmatSkip);
	}

	// this adds all the pools based on the phone list
	// @param phones the phone list
	public void addPools(Map phones, int size, boolean tmatSkip)
			throws IOException {

		int stateIndex = 0;
		int unitCount = 0;
		String attribute;
		// Initialize the pools we'll need.
		meansPool = new Pool("means");
		variancePool = new Pool("variances");
		mixtureWeightsPool = new Pool("mixtureweights");
		matrixPool = new Pool("transitionmatrices");
		senonePool = new Pool("senones");

		int[] stid = new int[size];
		String position = "-";
		Iterator pl = phones.keySet().iterator();
		while (pl.hasNext()) {

			String phone = (String) pl.next();
			for (int j = 0; j < size; j++, stateIndex++) {
				stid[j] = stateIndex;
			}
			// The first filler
			if (phone.equals(SILENCE_CIPHONE)) {
				attribute = FILLER;
			} else {
				attribute = "-";
			}
			Unit unit = unitManager.getUnit(phone, attribute.equals(FILLER));

			addModelToDensityPool(meansPool, stid, numStreams,
					numGaussiansPerState);
			addModelToDensityPool(variancePool, stid, numStreams,
					numGaussiansPerState);
			addModelToMixtureWeightPool(mixtureWeightsPool, stid, numStreams,
					numGaussiansPerState, mixtureWeightFloor);
			addModelToTransitionMatricesPool(matrixPool, unitCount,
					stid.length, transitionProbabilityFloor, tmatSkip);

			// After creating all pools, we create the senone pool.
			addModelToSenonePool(senonePool, stid, distFloor, varianceFloor);

			// With the senone pool in place, we go through all units, and
			// create the HMMs.

			// Create tmat
			float[][] transitionMatrix = (float[][]) matrixPool.get(unitCount);
			SenoneSequence ss = getSenoneSequence(stid);

			HMM hmm = new SenoneHMM(unit, ss, transitionMatrix, HMMPosition
					.lookup(position));
			hmmManager.put(hmm);
			unitCount++;
		}
	}

	// this returns map of all possible triphones
	// based on the phone list
	public Map getTriPhones() {
		Iterator m = phoneList.keySet().iterator();
		Map triphoneList = new LinkedHashMap();
		String result[] = new String[phoneList.keySet().size()];
		result[0] = "SIL";
		int r = 0;
		while (m.hasNext()) {
			result[r] = (String) m.next();
			r++;
		}
		String[] triPhones = new String[r * r * r];
		int t = 0;
		String temp = "";
		for (int i = 0; i < r; i++) {
			for (int j = 0; j < r; j++) {
				for (int k = 0; k < r; k++) {
					temp = result[i] + " " + result[j] + " " + result[k];
					triphoneList.put(temp, new Integer(0));
					t++;
				}
			}
		}
		return triphoneList;
	}

	// adds triphones to the pools by copying the base unit
	// @param phones the triphones

	public void addCDPools(Map phones, int size, boolean tmatSkip)
			throws IOException {
		Iterator pl = phones.keySet().iterator();
		while (pl.hasNext()) {
			int index = meansPool.size();
			Unit phone = (Unit) pl.next();
			SenoneHMM hmm = (SenoneHMM) phones.get(phone);
			Senone[] ss = hmm.getSenoneSequence().getSenones();
			int id = Long.valueOf(ss[0].getID()).intValue() + 1;
			meansPool.put(meansPool.size(), meansPool.get(id));
			variancePool.put(variancePool.size(), variancePool.get(id));
			mixtureWeightsPool.put(mixtureWeightsPool.size(),
					mixtureWeightsPool.get(id));
			// matrix pool is the transitions pool
			matrixPool.put(matrixPool.size(), matrixPool.get((id) / 3));
			senonePool.put(senonePool.size(), senonePool.get(id));
			float[][] transitionMatrix = (float[][]) matrixPool.get((id) / 3);
			int leftID = ((LeftRightContext) phone.getContext())
					.getLeftContext()[0].getBaseID();
			int rightID = ((LeftRightContext) phone.getContext())
					.getRightContext()[0].getBaseID();
			int[] stid = new int[size];
			stid[0] = leftID;
			stid[1] = id;
			// stid[1] = index;
			stid[2] = rightID;
			SenoneSequence ssn = getSenoneSequence(stid);
			HMM hmmN = new SenoneHMM(phone, ssn, transitionMatrix, HMMPosition
					.lookup("-"));
			hmmManager.put(hmmN);
		}
		meansPool.setFeature(NUM_SENONES, meansPool.size());
		variancePool.setFeature(NUM_SENONES, variancePool.size());
		mixtureWeightsPool.setFeature(NUM_SENONES, mixtureWeightsPool.size());
	}
	

	/**
	 * Loads the sphinx3 mdef 
	 * 
	 * @param useCDUnits
	 *            if true, loads also the context dependent units
	 * @param inputStream
	 *            the open input stream to use
	 * @param path
	 *            the path to a density file
	 * 
	 * @return a pool of loaded densities
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	protected Pool loadHMMPool(boolean useCDUnits, InputStream inputStream,
			String path) throws FileNotFoundException, IOException {
		int token_type;
		int numBase;
		int numTri;
		int numStateMap;
		int numTiedState;
		int numStatePerHMM;
		int numContextIndependentTiedState;
		int numTiedTransitionMatrices;

		ExtendedStreamTokenizer est = new ExtendedStreamTokenizer(inputStream,
				'#', false);
		Pool pool = new Pool(path);

		est.expectString(MODEL_VERSION);

		numBase = est.getInt("numBase");
		est.expectString("n_base");

		numTri = est.getInt("numTri");
		est.expectString("n_tri");

		numStateMap = est.getInt("numStateMap");
		est.expectString("n_state_map");

		numTiedState = est.getInt("numTiedState");
		est.expectString("n_tied_state");

		numContextIndependentTiedState = est
				.getInt("numContextIndependentTiedState");
		est.expectString("n_tied_ci_state");

		numTiedTransitionMatrices = est.getInt("numTiedTransitionMatrices");
		est.expectString("n_tied_tmat");

		numStatePerHMM = numStateMap / (numTri + numBase);

		// assert numTiedState == mixtureWeightsPool.getFeature(NUM_SENONES, 0);
		// assert numTiedTransitionMatrices == matrixPool.size();

		// Load the base phones
		for (int i = 0; i < numBase; i++) {
			String name = est.getString();
			String left = est.getString();
			String right = est.getString();
			String position = est.getString();
			String attribute = est.getString();
			int tmat = est.getInt("tmat");

			int[] stid = new int[numStatePerHMM - 1];

			for (int j = 0; j < numStatePerHMM - 1; j++) {
				stid[j] = est.getInt("j");
				// assert stid[j] >= 0 && stid[j] <
				// numContextIndependentTiedState;
			}
			est.expectString("N");

			// assert left.equals("-");
			// assert right.equals("-");
			// assert position.equals("-");
			// assert tmat < numTiedTransitionMatrices;

			Unit unit = unitManager.getUnit(name,attribute.equals(FILLER));
			//Unit unit = unitManager.getUnit(name, attribute.equals(FILLER));
			contextIndependentUnits.put(unit.getName(), unit);


			// The first filler
			if (unit.isFiller() && unit.getName().equals(SILENCE_CIPHONE)) {
				unit = UnitManager.SILENCE;
			}

			float[][] transitionMatrix = (float[][]) matrixPool.get(tmat);
			SenoneSequence ss = getSenoneSequence(stid);

			HMM hmm = new SenoneHMM(unit, ss, transitionMatrix, HMMPosition
					.lookup(position));
			hmmManager.put(hmm);
		}

		// Load the context dependent phones. If the useCDUnits
		// property is false, the CD phones will not be created, but
		// the values still need to be read in from the file.

		String lastUnitName = "";
		Unit lastUnit = null;
		int[] lastStid = null;
		SenoneSequence lastSenoneSequence = null;

		for (int i = 0; i < numTri; i++) {
			String name = est.getString();
			String left = est.getString();
			String right = est.getString();
			String position = est.getString();
			String attribute = est.getString();
			int tmat = est.getInt("tmat");

			int[] stid = new int[numStatePerHMM - 1];

			for (int j = 0; j < numStatePerHMM - 1; j++) {
				stid[j] = est.getInt("j");
				// assert stid[j] >= numContextIndependentTiedState &&
				// stid[j] < numTiedState;
			}
			est.expectString("N");

			// assert !left.equals("-");
			// assert !right.equals("-");
			// assert !position.equals("-");
			// assert attribute.equals("n/a");
			// assert tmat < numTiedTransitionMatrices;

			if (useCDUnits) {
				Unit unit = null;
				String unitName = (name + " " + left + " " + right);

				if (unitName.equals(lastUnitName)) {
					unit = lastUnit;
				} else {
					Unit[] leftContext = new Unit[1];
					leftContext[0] = (Unit) contextIndependentUnits.get(left);

					Unit[] rightContext = new Unit[1];
					rightContext[0] = (Unit) contextIndependentUnits.get(right);

					Context context = LeftRightContext.get(leftContext,
							rightContext);
					unit = unitManager.getUnit(name, false, context);
				}
				lastUnitName = unitName;
				lastUnit = unit;

				

				float[][] transitionMatrix = (float[][]) matrixPool.get(tmat);

				SenoneSequence ss = lastSenoneSequence;
				if (ss == null || !sameSenoneSequence(stid, lastStid)) {
					ss = getSenoneSequence(stid);
				}
				lastSenoneSequence = ss;
				lastStid = stid;

				HMM hmm = new SenoneHMM(unit, ss, transitionMatrix, HMMPosition
						.lookup(position));
				hmmManager.put(hmm);
			}
		}

		est.close();
		return pool;
	}
    /**
     * Gets the senone sequence representing the given senones
     * 
     * @param stateid
     *                is the array of senone state ids
     * 
     * @return the senone sequence associated with the states
     */
    protected SenoneSequence getSenoneSequence(int[] stateid) {
        Senone[] senones = new Senone[stateid.length];
        for (int i = 0; i < stateid.length; i++) {
            senones[i] = (Senone) senonePool.get(stateid[i]);
        }
        return new SenoneSequence(senones);
    }
	/**
	 * Adds a model to the senone pool.
	 * 
	 * @param pool
	 *            the senone pool
	 * @param stateID
	 *            vector with senone ID for an HMM
	 * @param distFloor
	 *            the lowest allowed score
	 * @param varianceFloor
	 *            the lowest allowed variance
	 * 
	 * @return the senone pool
	 */
	private void addModelToSenonePool(Pool pool, int[] stateID,
			float distFloor, float varianceFloor) {

		int numGaussiansPerSenone = mixtureWeightsPool.getFeature(
				NUM_GAUSSIANS_PER_STATE, 0);
		for (int i = 0; i < stateID.length; i++) {
			int state = stateID[i];
			MixtureComponent[] mixtureComponents = new MixtureComponent[numGaussiansPerSenone];
			for (int j = 0; j < numGaussiansPerSenone; j++) {
				int whichGaussian = state * numGaussiansPerSenone + j;
				mixtureComponents[j] = new MixtureComponent(logMath,
						(float[]) meansPool.get(whichGaussian),
						(float[][]) meanTransformationMatrixPool.get(0),
						(float[]) meanTransformationVectorPool.get(0),
						(float[]) variancePool.get(whichGaussian),
						(float[][]) varianceTransformationMatrixPool.get(0),
						(float[]) varianceTransformationVectorPool.get(0),
						distFloor, varianceFloor);
			}

			Senone senone = new GaussianMixture(logMath,
					(float[]) mixtureWeightsPool.get(state), mixtureComponents,
					state);

			pool.put(state, senone);
		}
	}

	/**
	 * Adds a set of density arrays to a given pool.
	 * 
	 * @param pool
	 *            the pool to add densities to
	 * @param stateID
	 *            a vector with the senone id of the states in a model
	 * @param numStreams
	 *            the number of streams
	 * @param numGaussiansPerState
	 *            the number of Gaussians per state
	 * 
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	private void addModelToDensityPool(Pool pool, int[] stateID,
			int numStreams, int numGaussiansPerState) throws IOException {
		int token_type;
		int numStates;
		int numInPool;
		// assert pool != null;
		// assert stateID != null;
		numStates = stateID.length;
		numInPool = pool.getFeature(NUM_SENONES, 0);
		pool.setFeature(NUM_SENONES, numStates + numInPool);
		numInPool = pool.getFeature(NUM_STREAMS, -1);
		if (numInPool == -1) {
			pool.setFeature(NUM_STREAMS, numStreams);
		} else {
			// assert numInPool == numStreams;
		}
		numInPool = pool.getFeature(NUM_GAUSSIANS_PER_STATE, -1);
		if (numInPool == -1) {
			pool.setFeature(NUM_GAUSSIANS_PER_STATE, numGaussiansPerState);
		} else {
			// assert numInPool == numGaussiansPerState;
		}
		// TODO: numStreams should be any number > 0, but for now....
		// assert numStreams == 1;
		for (int i = 0; i < numStates; i++) {
			int state = stateID[i];
			for (int j = 0; j < numGaussiansPerState; j++) {
				// We're creating densities here, so it's ok if values
				// are all zero.
				float[] density = new float[vectorLength];
				int id = state * numGaussiansPerState + j;
				pool.put(id, density);
			}
		}
	}

	/**
	 * Adds model to the mixture weights
	 * 
	 * @param pool
	 *            the pool to add models to
	 * @param stateID
	 *            vector containing state ids for hmm
	 * @param numStreams
	 *            the number of streams
	 * @param numGaussiansPerState
	 *            the number of Gaussians per state
	 * @param floor
	 *            the minimum mixture weight allowed
	 * 
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	private void addModelToMixtureWeightPool(Pool pool, int[] stateID,
			int numStreams, int numGaussiansPerState, float floor)
			throws IOException {

		int numStates = stateID.length;
		int numInPool;
		if (pool == null) {
			System.out.println("empty pool" + pool.getName());
		}
		numInPool = pool.getFeature(NUM_SENONES, 0);
		pool.setFeature(NUM_SENONES, numStates + numInPool);
		numInPool = pool.getFeature(NUM_STREAMS, -1);
		if (numInPool == -1) {
			pool.setFeature(NUM_STREAMS, numStreams);
		} else {
			// assert numInPool == numStreams;
		}
		numInPool = pool.getFeature(NUM_GAUSSIANS_PER_STATE, -1);
		if (numInPool == -1) {
			pool.setFeature(NUM_GAUSSIANS_PER_STATE, numGaussiansPerState);
		} else {
			// assert numInPool == numGaussiansPerState;
		}
		// TODO: allow any number for numStreams
		// assert numStreams == 1;
		for (int i = 0; i < numStates; i++) {
			int state = stateID[i];
			float[] logMixtureWeight = new float[numGaussiansPerState];
			// Initialize the weights with the same value, e.g. floor
			logMixtureWeight = floorData(logMixtureWeight, floor);
			// Normalize, so the numbers are not all too low
			//logMixtureWeight = normalize(logMixtureWeight);
			logMixtureWeight = convertToLogMath(logMixtureWeight);
			pool.put(state, logMixtureWeight);
		}
	}

	/**
	 * Adds transition matrix to the transition matrices pool
	 * 
	 * @param pool
	 *            the pool to add matrix to
	 * @param hmmId
	 *            current HMM's id
	 * @param numEmittingStates
	 *            number of states in current HMM
	 * @param floor
	 *            the transition probability floor
	 * @param skip
	 *            if true, states can be skipped
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	private void addModelToTransitionMatricesPool(Pool pool, int hmmId,
			int numEmittingStates, float floor, boolean skip)
			throws IOException {
		// Add one to account for the last, non-emitting, state
		int numStates = numEmittingStates + 1;
		float[][] tmat = new float[numStates][numStates];

		for (int j = 0; j < numStates; j++) {
			for (int k = 0; k < numStates; k++) {
				// Just to be sure...
				tmat[j][k] = 0.0f;
				// the last row is just zeros, so we just do
				// the first (numStates - 1) rows
				// The value assigned could be anything, provided
				// we normalize it.
				if (j < numStates - 1) {
					// Usual case: state can transition to itself
					// or the next state.
					if (k == j || k == j + 1) {
						tmat[j][k] = floor;
					}
					// If we can skip, we can also transition to
					// the next state
					if (skip) {
						if (k == j + 2) {
							tmat[j][k] = floor;
						}
					}
				}
			}
			//normalize(tmat[j]);
			convertToLogMath(tmat[j]);
		}
		pool.put(hmmId, tmat);
	}

	/**
	 * Creates a pool with a single identity matrix in it.
	 * 
	 * @param name
	 *            the name of the pool
	 * 
	 * @return the pool with the matrix
	 */
	private Pool createDummyMatrixPool(String name) {
		Pool pool = new Pool(name);
		float[][] matrix = new float[vectorLength][vectorLength];
		// logger.fine("creating dummy matrix pool " + name);
		for (int i = 0; i < vectorLength; i++) {
			for (int j = 0; j < vectorLength; j++) {
				if (i == j) {
					matrix[i][j] = 1.0F;
				} else {
					matrix[i][j] = 0.0F;
				}
			}
		}
		pool.put(0, matrix);
		return pool;
	}

	/**
	 * Creates a pool with a single zero vector in it.
	 * 
	 * @param name
	 *            the name of the pool
	 * 
	 * @return the pool with the vector
	 */
	private Pool createDummyVectorPool(String name) {
		// logger.fine("creating dummy vector pool " + name);
		Pool pool = new Pool(name);
		float[] vector = new float[vectorLength];
		for (int i = 0; i < vectorLength; i++) {
			vector[i] = 0.0f;
		}
		pool.put(0, vector);
		return pool;
	}
    /**
     * If a data point is below 'floor' make it equal to floor.
     * 
     * @param data
     *                the data to floor
     * @param floor
     *                the floored value
     */
    private float[] floorData(float[] data, float floor) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] < floor) {
                data[i] = floor;
            }
        }
        return data;
    }
    /**
     * Convert to log math
     * 
     * @param data
     *                the data to normalize
     */
    // linearToLog returns a float, so zero values in linear scale
    // should return -Float.MAX_VALUE.
    protected float[] convertToLogMath(float[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] = logMath.linearToLog(data[i]);
        }
        return data;
    }

    /**
     * Gets the pool of means for this loader
     * 
     * @return the pool
     */
    public Pool getMeansPool() {
        return meansPool;
    }
    /**
     * Gets the pool of means transformation matrices for this loader
     * 
     * @return the pool
     */
    public Pool getMeansTransformationMatrixPool() {
        return meanTransformationMatrixPool;
    }
    /**
     * Gets the pool of means transformation vectors for this loader
     * 
     * @return the pool
     */
    public Pool getMeansTransformationVectorPool() {
        return meanTransformationVectorPool;
    }
    /*
     * Gets the variance pool
     * 
     * @return the pool
     */
    public Pool getVariancePool() {
        return variancePool;
    }
    /**
     * Gets the variance transformation matrix pool
     * 
     * @return the pool
     */
    public Pool getVarianceTransformationMatrixPool() {
        return varianceTransformationMatrixPool;
    }
    /**
     * Gets the pool of variance transformation vectors for this loader
     * 
     * @return the pool
     */
    public Pool getVarianceTransformationVectorPool() {
        return varianceTransformationVectorPool;
    }
    /*
     * Gets the mixture weight pool
     * 
     * @return the pool
     */
    public Pool getMixtureWeightPool() {
        return mixtureWeightsPool;
    }
    /*
     * Gets the transition matrix pool
     * 
     * @return the pool
     */
    public Pool getTransitionMatrixPool() {
        return matrixPool;
    }
    /*
     * Gets the senone pool for this loader
     * 
     * @return the pool
     */
    public Pool getSenonePool() {
        return senonePool;
    }
    /**
     * Returns the size of the left context for context dependent units
     * 
     * @return the left context size
     */
    public int getLeftContextSize() {
        return CONTEXT_SIZE;
    }
    /**
     * Returns the size of the right context for context dependent units
     * 
     * @return the left context size
     */
    public int getRightContextSize() {
        return CONTEXT_SIZE;
    }
    /**
     * Returns the map of context indepent units. The map can be accessed by
     * unit name.
     * 
     * @return the map of context independent units.
     */
    public Map getContextIndependentUnits() {
        return contextIndependentUnits;
    }
    /**
     * Returns the hmm manager associated with this loader
     * 
     * @return the hmm Manager
     */
    public HMMManager getHMMManager() {
        return hmmManager;
    }
    /**
     * Log info about this loader
     */
    public void logInfo() {
  
    }
    public float getMixtureWeightFloor(){
    	return mixtureWeightFloor;
    
    }
    public float getTransitionProbabilityFloor(){
    	return transitionProbabilityFloor;
    
    }

	/**
	 * Loads the mixture weights
	 * 
	 * @param path
	 *            the path to the mixture weight file
	 * @param floor
	 *            the minimum mixture weight allowed
	 * 
	 * @return a pool of mixture weights
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	private Pool loadMixtureWeightsAscii(String path, float floor)
			throws FileNotFoundException, IOException {
		 
		int numStates;
		int numStreams;
		int numGaussiansPerState;
		InputStream inputStream = StreamFactory.getInputStream(dataDir, path);
		Pool pool = new Pool(path);
		ExtendedStreamTokenizer est = new ExtendedStreamTokenizer(inputStream,
				'#', false);
		est.expectString("mixw");
		numStates = est.getInt("numStates");
		numStreams = est.getInt("numStreams");
		numGaussiansPerState = est.getInt("numGaussiansPerState");
		pool.setFeature(NUM_SENONES, numStates);
		pool.setFeature(NUM_STREAMS, numStreams);
		pool.setFeature(NUM_GAUSSIANS_PER_STATE, numGaussiansPerState);
		for (int i = 0; i < numStates; i++) {
			est.expectString("mixw");
			est.expectString("[" + i);
			est.expectString("0]");
			float total = est.getFloat("total");
			float[] logMixtureWeight = new float[numGaussiansPerState];
			for (int j = 0; j < numGaussiansPerState; j++) {
				float val = est.getFloat("mixwVal");
				if (val < floor) {
					val = floor;
				}
				logMixtureWeight[j] = val;
			}
			logMixtureWeight = normalize(logMixtureWeight);
			logMixtureWeight = floorData(logMixtureWeight, floor);
			logMixtureWeight = convertToLogMath(logMixtureWeight);
			pool.put(i, logMixtureWeight);
		}
		est.close();
		return pool;
	}

	/**
	 * Loads the mixture weights (Binary)
	 * 
	 * @param path
	 *            the path to the mixture weight file
	 * @param floor
	 *            the minimum mixture weight allowed
	 * 
	 * @return a pool of mixture weights
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	private Pool loadMixtureWeightsBinary(String path, float floor)
			throws FileNotFoundException, IOException {

		int numStates;
		int numStreams;
		int numGaussiansPerState;
		int numValues;
		Properties props = new Properties();

		DataInputStream dis = readS3BinaryHeader(location, path, props);

		String version = props.getProperty("version");
		boolean doCheckSum;

		if (version == null || !version.equals(MIXW_FILE_VERSION)) {
			throw new IOException("Unsupported version in " + path);
		}

		String checksum = props.getProperty("chksum0");
		doCheckSum = (checksum != null && checksum.equals("yes"));

		Pool pool = new Pool(path);

		numStates = readInt(dis);
		numStreams = readInt(dis);
		numGaussiansPerState = readInt(dis);
		numValues = readInt(dis);

		// assert numValues == numStates * numStreams * numGaussiansPerState;
		// assert numStreams == 1;

		pool.setFeature(NUM_SENONES, numStates);
		pool.setFeature(NUM_STREAMS, numStreams);
		pool.setFeature(NUM_GAUSSIANS_PER_STATE, numGaussiansPerState);

		for (int i = 0; i < numStates; i++) {
			float[] logMixtureWeight = readFloatArray(dis, numGaussiansPerState);
			logMixtureWeight = normalize(logMixtureWeight);
			logMixtureWeight = floorData(logMixtureWeight, floor);
			logMixtureWeight = convertToLogMath(logMixtureWeight);
			pool.put(i, logMixtureWeight);
		}
		dis.close();
		return pool;
	}


	/**
	 * Loads the transition matrices
	 * 
	 * @param path
	 *            the path to the transitions matrices
	 * 
	 * @return a pool of transition matrices
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	protected Pool loadTransitionMatricesAscii(String path)
			throws FileNotFoundException, IOException {
		InputStream inputStream = StreamFactory.getInputStream(dataDir, path);
		 
		int numMatrices;
		int numStates;
		Pool pool = new Pool(path);
		ExtendedStreamTokenizer est = new ExtendedStreamTokenizer(inputStream,
				'#', false);
		est.expectString("tmat");
		numMatrices = est.getInt("numMatrices");
		numStates = est.getInt("numStates");

		// read in the matrices
		for (int i = 0; i < numMatrices; i++) {
			est.expectString("tmat");
			est.expectString("[" + i + "]");
			float[][] tmat = new float[numStates][numStates];
			//System.out.println("are incoming files in log form already why are they negative?");
			for (int j = 0; j < numStates; j++) {
				for (int k = 0; k < numStates; k++) {
					// the last row is just zeros, so we just do
					// the first (numStates - 1) rows
					if (j < numStates - 1) {
						if (sparseForm) {
							if (k == j || k == j + 1) {
								tmat[j][k] = est.getFloat("tmat value");
							}
						} else {
							tmat[j][k] = est.getFloat("tmat value");
						}
					}
					if (tmat[j][k] < distFloor) {// I added this 
						tmat[j][k] = distFloor;
					}
					 tmat[j][k] = logMath.linearToLog(tmat[j][k]);
				}
			}
			pool.put(i, tmat);
		}
		est.close();
		return pool;
	}

	/**
	 * Loads the transition matrices (Binary)
	 * 
	 * @param path
	 *            the path to the transitions matrices
	 * 
	 * @return a pool of transition matrices
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	protected Pool loadTransitionMatricesBinary(String path)
			throws FileNotFoundException, IOException {
		 
		int numMatrices;
		int numStates;
		int numRows;
		int numValues;

		Properties props = new Properties();
		DataInputStream dis = readS3BinaryHeader(location, path, props);

		String version = props.getProperty("version");
		boolean doCheckSum;

		if (version == null || !version.equals(TMAT_FILE_VERSION)) {
			throw new IOException("Unsupported version in " + path);
		}

		String checksum = props.getProperty("chksum0");
		doCheckSum = (checksum != null && checksum.equals("yes"));

		Pool pool = new Pool(path);

		numMatrices = readInt(dis);
		numRows = readInt(dis);
		numStates = readInt(dis);
		numValues = readInt(dis);

		// assert numValues == numStates * numRows * numMatrices;

		for (int i = 0; i < numMatrices; i++) {
			float[][] tmat = new float[numStates][];
			// last row should be zeros
			tmat[numStates - 1] = new float[numStates];
		//	tmat[numStates - 1] = convertToLogMath(tmat[numStates - 1]);

			for (int j = 0; j < numRows; j++) {
				tmat[j] = readFloatArray(dis, numStates);
				tmat[j] = nonZeroFloor(tmat[j], 0f);
				// tmat[j] = normalize(tmat[j]);// I commented out fp
				tmat[j] = convertToLogMath(tmat[j]);
			}
			pool.put(i, tmat);
		}
		dis.close();
		return pool;
	}


    /**
	 * Loads the sphinx3 densityfile, a set of density arrays are created and
	 * placed in the given pool.
	 * 
	 * @param path
	 *            the name of the data
	 * @param floor
	 *            the minimum density allowed
	 * 
	 * @return a pool of loaded densities
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	private Pool loadDensityFileAscii(String path, float floor)
			throws FileNotFoundException, IOException {
		int token_type;
		int numStates;
		int numStreams;
		int numGaussiansPerState;

		InputStream inputStream = StreamFactory.getInputStream(dataDir, path);

		if (inputStream == null) {
			throw new FileNotFoundException("Error trying to read file "
					+  path);
		}
		// 'false' argument refers to EOL is insignificant
		ExtendedStreamTokenizer est = new ExtendedStreamTokenizer(inputStream,
				'#', false);
		Pool pool = new Pool(path);
		 
		est.expectString("param");
		numStates = est.getInt("numStates");
		numStreams = est.getInt("numStreams");
		numGaussiansPerState = est.getInt("numGaussiansPerState");
		pool.setFeature(NUM_SENONES, numStates);
		pool.setFeature(NUM_STREAMS, numStreams);
		pool.setFeature(NUM_GAUSSIANS_PER_STATE, numGaussiansPerState);
		for (int i = 0; i < numStates; i++) {
			est.expectString("mgau");
			est.expectInt("mgau index", i);
			est.expectString("feat");
			est.expectInt("feat index", 0);
			for (int j = 0; j < numGaussiansPerState; j++) {
				est.expectString("density");
				est.expectInt("densityValue", j);
				float[] density = new float[vectorLength];
				for (int k = 0; k < vectorLength; k++) {
					density[k] = est.getFloat("val");
					if (density[k] < floor) {
						density[k] = floor;
					}
					// System.out.println(" " + i + " " + j + " " + k +
					// " " + density[k]);
				}
				int id = i * numGaussiansPerState + j;
				pool.put(id, density);
			}
		}
		est.close();
		return pool;
	}

	/**
	 * Loads the sphinx3 densityfile, a set of density arrays are created and
	 * placed in the given pool.
	 * 
	 * @param path
	 *            the name of the data
	 * @param floor
	 *            the minimum density allowed
	 * 
	 * @return a pool of loaded densities
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while loading the data
	 */
	private Pool loadDensityFileBinary(String path, float floor)
			throws FileNotFoundException, IOException {
		int token_type;
		int numStates;
		int numStreams;
		int numGaussiansPerState;
		Properties props = new Properties();
		int blockSize = 0;

		DataInputStream dis = readS3BinaryHeader(location, path, props);

		String version = props.getProperty("version");
		boolean doCheckSum;

		if (version == null || !version.equals(DENSITY_FILE_VERSION)) {
			throw new IOException("Unsupported version in " + path);
		}

		String checksum = props.getProperty("chksum0");
		doCheckSum = (checksum != null && checksum.equals("yes"));

		numStates = readInt(dis);
		numStreams = readInt(dis);
		numGaussiansPerState = readInt(dis);

		int[] vectorLength = new int[numStreams];
		for (int i = 0; i < numStreams; i++) {
			vectorLength[i] = readInt(dis);
		}

		int rawLength = readInt(dis);

		// System.out.println("Nstates " + numStates);
		// System.out.println("Nstreams " + numStreams);
		// System.out.println("NgaussiansPerState " + numGaussiansPerState);
		// System.out.println("vectorLength " + vectorLength.length);
		// System.out.println("rawLength " + rawLength);

		for (int i = 0; i < numStreams; i++) {
			blockSize += vectorLength[i];
		}

		// assert rawLength == numGaussiansPerState * blockSize * numStates;
		// assert numStreams == 1;

		Pool pool = new Pool(path);
		pool.setFeature(NUM_SENONES, numStates);
		pool.setFeature(NUM_STREAMS, numStreams);
		pool.setFeature(NUM_GAUSSIANS_PER_STATE, numGaussiansPerState);

		int r = 0;
		for (int i = 0; i < numStates; i++) {
			for (int j = 0; j < numStreams; j++) {
				for (int k = 0; k < numGaussiansPerState; k++) {
					float[] density = readFloatArray(dis, vectorLength[j]);
					density = floorData(density, floor);
					pool.put(i * numGaussiansPerState + k, density);
				}
			}
		}

		int checkSum = readInt(dis);
		// BUG: not checking the check sum yet.
		dis.close();
		return pool;
	}


    /**
     * Creates the senone pool from the rest of the pools.
     * 
     * @param distFloor
     *                the lowest allowed score
     * @param varianceFloor
     *                the lowest allowed variance
     * 
     * @return the senone pool
     */
    private Pool createSenonePool(float distFloor, float varianceFloor) {
    	Pool pool = new Pool("senones");
	int numMixtureWeights = mixtureWeightsPool.size();

	int numMeans = meansPool.size();
	int numVariances = variancePool.size();
	int numGaussiansPerSenone = 
            mixtureWeightsPool.getFeature(NUM_GAUSSIANS_PER_STATE, 0);
	int numSenones = mixtureWeightsPool.getFeature(NUM_SENONES, 0);
	int whichGaussian = 0;

	assert numGaussiansPerSenone > 0;
	assert numMixtureWeights == numSenones;
	assert numVariances == numSenones * numGaussiansPerSenone;
	assert numMeans == numSenones * numGaussiansPerSenone;

	for (int i = 0; i < numSenones; i++) {
	    MixtureComponent[] mixtureComponents = new
		MixtureComponent[numGaussiansPerSenone];
	    for (int j = 0; j <  numGaussiansPerSenone; j++) {
		mixtureComponents[j] = new MixtureComponent(
		    logMath,
		    (float[]) meansPool.get(whichGaussian),
		    (float[][]) meanTransformationMatrixPool.get(0),
		    (float[]) meanTransformationVectorPool.get(0),
		    (float[]) variancePool.get(whichGaussian),
		    (float[][]) varianceTransformationMatrixPool.get(0),
		    (float[]) varianceTransformationVectorPool.get(0),
		    distFloor,
		    varianceFloor);

		whichGaussian++;
	    }

	    Senone senone = new GaussianMixture( 
	      logMath, (float[]) mixtureWeightsPool.get(i), 
              mixtureComponents, i);

	    pool.put(i, senone);
	}
	return pool;
    }   
	/**
	 * Normalize the given data
	 * 
	 * @param data
	 *            the data to normalize
	 */
	protected float[] normalize(float[] data) {
		float sum = 0;
		for (int i = 0; i < data.length; i++) {
			sum += data[i];
		}
		if (sum != 0.0f) {
			for (int i = 0; i < data.length; i++) {
				data[i] = data[i] / sum;
			}
		}
		return data;
	}

	/**
	 * Read an integer from the input stream, byte-swapping as necessary
	 * 
	 * @param dis
	 *            the inputstream
	 * 
	 * @return an integer value
	 * 
	 * @throws IOException
	 *             on error
	 */
	protected int readInt(DataInputStream dis) throws IOException {
		if (swap) {
			return Utilities.readLittleEndianInt(dis);
		} else {
			return dis.readInt();
		}
	}
	/**
	 * Reads the given number of floats from the stream and returns them in an
	 * array of floats
	 * 
	 * @param dis
	 *            the stream to read data from
	 * @param size
	 *            the number of floats to read
	 * 
	 * @return an array of size float elements
	 * 
	 * @throws IOException
	 *             if an exception occurs
	 */
	protected float[] readFloatArray(DataInputStream dis, int size)
			throws IOException {
		float[] data = new float[size];
		for (int i = 0; i < size; i++) {
			data[i] = readFloat(dis);
		}
		return data;
	}
	/**
	 * Read a float from the input stream, byte-swapping as necessary
	 * 
	 * @param dis
	 *            the inputstream
	 * 
	 * @return a floating pint value
	 * 
	 * @throws IOException
	 *             on error
	 */
	protected float readFloat(DataInputStream dis) throws IOException {
		float val;
		if (swap) {
			val = Utilities.readLittleEndianFloat(dis);
		} else {
			val = dis.readFloat();
		}
		return val;
	}

	/**
	 * Reads the S3 binary hearder from the given location+path. Adds header
	 * information to the given set of properties.
	 * 
	 * @param location
	 *            the location of the file
	 * @param path
	 *            the name of the file
	 * @param props
	 *            the properties
	 * 
	 * @return the input stream positioned after the header
	 * 
	 * @throws IOException
	 *             on error
	 */
	protected DataInputStream readS3BinaryHeader(String location, String path,
			Properties props) throws IOException {

		 System.out.println("resource: " + path + ", " + getClass());
		//InputStream inputStream = getClass().getResourceAsStream(path);
		InputStream inputStream = StreamFactory.getInputStream(dataDir, path);


		if (inputStream == null) {
			throw new IOException("Can't open " + path);
		}
		DataInputStream dis = new DataInputStream(new BufferedInputStream(
				inputStream));
		String id = readWord(dis);
		if (!id.equals("s3")) {
			throw new IOException("Not proper s3 binary file " + location
					+ path);
		}
		String name;
		while ((name = readWord(dis)) != null) {
			if (!name.equals("endhdr")) {
				String value = readWord(dis);
				props.setProperty(name, value);
			} else {
				break;
			}
		}
		int byteOrderMagic = dis.readInt();
		if (byteOrderMagic == BYTE_ORDER_MAGIC) {
			// System.out.println("Not swapping " + path);
			swap = false;
		} else if (byteSwap(byteOrderMagic) == BYTE_ORDER_MAGIC) {
			// System.out.println("SWAPPING " + path);
			swap = true;
		} else {
			throw new IOException("Corrupt S3 file " + location + path);
		}
		return dis;
	}
	/**
	 * If a data point is non-zero and below 'floor' make it equal to floor
	 * (don't floor zero values though).
	 * 
	 * @param data
	 *            the data to floor
	 * @param floor
	 *            the floored value
	 */
	protected float [] nonZeroFloor(float[] data, float floor) {
		for (int i = 0; i < data.length; i++) {
			if (data[i] != 0.0 && data[i] < floor) {
				data[i] = floor;
			}
		}
		return data;
	}

	/**
	 * Reads the next word (text separated by whitespace) from the given stream
	 * 
	 * @param dis
	 *            the input stream
	 * 
	 * @return the next word
	 * 
	 * @throws IOException
	 *             on error
	 */
	String readWord(DataInputStream dis) throws IOException {
		StringBuffer sb = new StringBuffer();
		char c;
		// skip leading whitespace
		do {
			c = readChar(dis);
		} while (Character.isWhitespace(c));
		// read the word
		do {
			sb.append(c);
			c = readChar(dis);
		} while (!Character.isWhitespace(c));
		return sb.toString();
	}
	/**
	 * Reads a single char from the stream
	 * 
	 * @param dis
	 *            the stream to read
	 * @return the next character on the stream
	 * 
	 * @throws IOException
	 *             if an error occurs
	 */
	private char readChar(DataInputStream dis) throws IOException {
		return (char) dis.readByte();
	}
	/**
	 * swap a 32 bit word
	 * 
	 * @param val
	 *            the value to swap
	 * 
	 * @return the swapped value
	 */
	private int byteSwap(int val) {
		return ((0xff & (val >> 24)) | (0xff00 & (val >> 8))
				| (0xff0000 & (val << 8)) | (0xff000000 & (val << 24)));
	}

	/**
	 * Returns true if the given senone sequence IDs are the same.
	 * 
	 * @return true if the given senone sequence IDs are the same, false
	 *         otherwise
	 */
	protected boolean sameSenoneSequence(int[] ssid1, int[] ssid2) {
		if (ssid1.length == ssid2.length) {
			for (int i = 0; i < ssid1.length; i++) {
				if (ssid1[i] != ssid2[i]) {
					return false;
				}
			}
			return true;
		} else {
			return false;
		}
	}
}
