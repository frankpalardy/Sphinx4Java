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

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;
import edu.cmu.sphinx.util.props.Configurable;

import edu.cmu.sphinx.linguist.acoustic.AcousticModel;
import edu.cmu.sphinx.linguist.acoustic.LeftRightContext;
import edu.cmu.sphinx.linguist.acoustic.Unit;
import edu.cmu.sphinx.linguist.acoustic.UnitManager;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.GaussianMixture;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.HMMManager;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Loader;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.MixtureComponent;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Pool;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Saver;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Senone;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.SenoneHMM;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.SenoneSequence;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.TiedStateAcousticModel;
import edu.cmu.sphinx.util.LogMath;
import edu.cmu.sphinx.util.SphinxProperties;
import edu.cmu.sphinx.util.StreamFactory;
import edu.cmu.sphinx.util.Utilities;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.PropertyType;
import edu.cmu.sphinx.util.props.Registry;

/**
 * An acoustic model saver that saves sphinx3 ascii data.
 * 
 * Mixture weights and transition probabilities are saved in linear scale.
 */
public class Sphinx3Saver implements Saver, Configurable{
	/**
	 * The log math component for the system.
	 */
	public final static String PROP_LOG_MATH = "logMath";

	private final static String NUM_SENONES = "num_senones";

	private final static String NUM_GAUSSIANS_PER_STATE = "num_gaussians";

	private final static String NUM_STREAMS = "num_streams";

	private final static String FILLER = "filler";

	private final static String SILENCE_CIPHONE = "SIL";

	private final static int BYTE_ORDER_MAGIC = 0x11223344;

	public final static String MODEL_VERSION = "0.3";

	private final static int CONTEXT_SIZE = 1;

	private String checksum;

	private boolean doCheckSum;

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

	private HMMManager hmmManager;

	private SphinxProperties acousticProperties;

	private boolean binary = true;

	private String location;

	private boolean swap;

	private final static String DENSITY_FILE_VERSION = "1.0";

	private final static String MIXW_FILE_VERSION = "1.0";

	private final static String TMAT_FILE_VERSION = "1.0";

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

	boolean useCDUnits;

	String PROP_USE_CD_UNITS = "useCDUnits";

	private String dataDir = "";

	String PROP_DATA_DIR = "dataDir";

	String PROP_DATA_LOCATION_DEFAULT = "edu/cmu/sphinx/model/trainer/an4/etc/";

	boolean sparseForm = false;

	String PROP_SPARSE_FORM = "sparseForm";

	String modelDef = "";

	String PROP_MODEL_DEF_DEFAULT = "training.mdef";
	
	String PROP_MODEL_DEF = "modelDef";

	public LogMath logMath;
	/**
	 * The logger for this class
	 */
	private static Logger logger = Logger
			.getLogger("edu.cmu.sphinx.linguist.acoustic.Sphinx3Saver");
	
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
		registry.register(PROP_MODEL_DEF, PropertyType.STRING);
		registry.register(PROP_MC_FLOOR, PropertyType.FLOAT);
		registry.register(PROP_MW_FLOOR, PropertyType.FLOAT);
		registry.register(PROP_TP_FLOOR, PropertyType.FLOAT);
		registry.register(PROP_VECTOR_LENGTH, PropertyType.INT);
		registry.register(PROP_VARIANCE_FLOOR, PropertyType.FLOAT);
		registry.register(PROP_USE_CD_UNITS, PropertyType.BOOLEAN);
		registry.register(PROP_SPARSE_FORM, PropertyType.BOOLEAN);
		registry.register(PROP_DATA_DIR, PropertyType.STRING);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#newProperties(edu.cmu.sphinx.util.props.PropertySheet)
	 */
	public void newProperties(PropertySheet ps) throws PropertyException {

	//	location = ps.getString(prefix + "location.save",TrainerAcousticModel.PROP_LOCATION_SAVE_DEFAULT);
		logMath = (LogMath) ps.getComponent(PROP_LOG_MATH, LogMath.class);
		modelDef = ps.getString(PROP_MODEL_DEF, PROP_MODEL_DEF_DEFAULT);
		dataDir = ps.getString(PROP_DATA_DIR, PROP_DATA_LOCATION_DEFAULT);
		vectorLength = ps
				.getInt(PROP_VECTOR_LENGTH, PROP_VECTOR_LENGTH_DEFAULT);
		float distFloor = ps.getFloat(PROP_MC_FLOOR, PROP_MC_FLOOR_DEFAULT);
		float mixtureWeightFloor = ps.getFloat(PROP_MW_FLOOR,
				PROP_MW_FLOOR_DEFAULT);
		float transitionProbabilityFloor = ps.getFloat(PROP_TP_FLOOR,
				PROP_TP_FLOOR_DEFAULT);
		float varianceFloor = ps.getFloat(PROP_VARIANCE_FLOOR,
				PROP_VARIANCE_FLOOR_DEFAULT);
		useCDUnits = ps.getBoolean(PROP_USE_CD_UNITS, false);
		sparseForm = ps.getBoolean(PROP_SPARSE_FORM, false);
	}

	/**
	 * Saves the sphinx3 models.
	 * 
	 * @param modelName
	 *            the name of the model as specified in the props file.
	 * @param props
	 *            the SphinxProperties object
	 * @param binary
	 *            if <code>true</code> the file is saved in binary format
	 * @param loader
	 *            this acoustic model's loader
	 */
	public void init(String modelName, boolean binary, Loader loader)
			throws FileNotFoundException, IOException, ZipException {

		this.binary = binary;
		// logMath = LogMath.getLogMath(props.getContext());

		hmmManager = loader.getHMMManager();
		meansPool = loader.getMeansPool();
		variancePool = loader.getVariancePool();
		mixtureWeightsPool = loader.getMixtureWeightPool();
		matrixPool = loader.getTransitionMatrixPool();
		senonePool = loader.getSenonePool();
		contextIndependentUnits = new LinkedHashMap();
		// acousticProperties = loader.getModelProperties();
		// TODO: read checksum from props;
		checksum = "no";
		doCheckSum = (checksum != null && checksum.equals("yes"));
		swap = false;

		// do the actual acoustic model loading
		saveModelFiles(modelName);
	}

	/**
	 * Return the checksum string.
	 * 
	 * @return the checksum
	 */
	protected String getCheckSum() {
		return checksum;
	}

	/**
	 * Return whether to do the dochecksum. If true, checksum is performed.
	 * 
	 * @return the dochecksum
	 */
	protected boolean getDoCheckSum() {
		return doCheckSum;
	}

	/**
	 * Return the LogMath.
	 * 
	 * @return the logMath
	 */
	protected LogMath getLogMath() {
		return logMath;
	}

	/**
	 * Return the acousticProperties.
	 * 
	 * @return the acousticProperties
	 */
	protected SphinxProperties getAcousticProperties() {
		return acousticProperties;
	}

	/**
	 * Return the location.
	 * 
	 * @return the location
	 */
	protected String getLocation() {
		return location;
	}

	/**
	 * Saves the AcousticModel from a directory in the file system.
	 * 
	 * @param modelName
	 *            the name of the acoustic model; if null we just save from the
	 *            default location
	 * @param props
	 *            the SphinxProperties object to use
	 */
	private void saveModelFiles(String modelName) throws FileNotFoundException,
			IOException, ZipException {

		System.out.println("Saving acoustic model: " + modelName);
		System.out.println("    Path      : " + location);
		System.out.println("    modellName: " + modelDef);
		System.out.println("    dataDir   : " + dataDir);

		/*
		 * save the acoustic properties file (am.props), // create a different
		 * URL depending on the data format
		 * 
		 * URL url = null; String format = StreamFactory.resolve(location);
		 * 
		 * if (format.equals(StreamFactory.ZIP_FILE)) { url = new URL("jar:" +
		 * location + "!/" + propsFile); } else { File file = new File(location,
		 * propsFile); url = file.toURI().toURL(); }
		 * 
		 * if (modelName == null) { prefix = props.getContext() + ".acoustic"; }
		 * else { prefix = props.getContext() + ".acoustic." + modelName; }
		 * saveAcousticPropertiesFile(acousticProperties, propsFile, false);
		 */
		if (binary) {
			// First, overwrite the previous file
			saveDensityFileBinary(meansPool, dataDir + "means", true);
			// From now on, append to previous file
			saveDensityFileBinary(variancePool, dataDir + "variances", true);
			saveMixtureWeightsBinary(mixtureWeightsPool, dataDir
					+ "mixture_weights", true);
			saveTransitionMatricesBinary(matrixPool, dataDir
					+ "transition_matrices", true);

		} else {
			saveDensityFileAscii(meansPool, dataDir + "means.ascii", true);
			saveDensityFileAscii(variancePool, dataDir + "variances.ascii",
					true);
			saveMixtureWeightsAscii(mixtureWeightsPool, dataDir
					+ "mixture_weights.ascii", true);
			saveTransitionMatricesAscii(matrixPool, dataDir
					+ "transition_matrices.ascii", true);
		}

		// senonePool = createSenonePool(distFloor);

		saveHMMPool(useCDUnits, StreamFactory.getOutputStream(dataDir,
				modelDef, true), dataDir + File.separator + modelDef);
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
	 * Creates the senone pool from the rest of the pools.
	 * 
	 * @param distFloor
	 *            the lowest allowed score
	 * 
	 * @return the senone pool
	 */
	private Pool createSenonePool(float distFloor, float varianceFloor) {
		Pool pool = new Pool("senones");
		int numMixtureWeights = mixtureWeightsPool.size();

		int numMeans = meansPool.size();
		int numVariances = variancePool.size();
		int numGaussiansPerSenone = mixtureWeightsPool.getFeature(
				NUM_GAUSSIANS_PER_STATE, 0);
		int numSenones = mixtureWeightsPool.getFeature(NUM_SENONES, 0);
		int whichGaussian = 0;

		//logger.fine("NG " + numGaussiansPerSenone);
		//logger.fine("NS " + numSenones);
		//logger.fine("NMIX " + numMixtureWeights);
		//logger.fine("NMNS " + numMeans);
		//logger.fine("NMNS " + numVariances);

		// assert numGaussiansPerSenone > 0;
		// assert numMixtureWeights == numSenones;
		// assert numVariances == numSenones * numGaussiansPerSenone;
		// assert numMeans == numSenones * numGaussiansPerSenone;

		for (int i = 0; i < numSenones; i++) {
			MixtureComponent[] mixtureComponents = new MixtureComponent[numGaussiansPerSenone];
			for (int j = 0; j < numGaussiansPerSenone; j++) {
				mixtureComponents[j] = new MixtureComponent(logMath,
						(float[]) meansPool.get(whichGaussian),
						(float[][]) meanTransformationMatrixPool.get(0),
						(float[]) meanTransformationVectorPool.get(0),
						(float[]) variancePool.get(whichGaussian),
						(float[][]) varianceTransformationMatrixPool.get(0),
						(float[]) varianceTransformationVectorPool.get(0),
						distFloor, varianceFloor);

				whichGaussian++;
			}

			Senone senone = new GaussianMixture(logMath,
					(float[]) mixtureWeightsPool.get(i), mixtureComponents, i);

			pool.put(i, senone);
		}
		return pool;
	}

	/**
	 * Loads the Sphinx 3 acoustic model properties file, which is basically a
	 * normal system properties file.
	 * 
	 * @param property
	 *            the SphinxProperties object
	 * @param path
	 *            the path to the acoustic properties file
	 * @param append
	 *            if true, append to the current file, if ZIP file
	 * 
	 * @throws FileNotFoundException
	 *             if the file cannot be found
	 * @throws IOException
	 *             if an error occurs while saving the data
	 */
	private void saveAcousticPropertiesFile(SphinxProperties property,
			String path, boolean append) throws FileNotFoundException,
			IOException {
		logger.info("Saving acoustic properties file to:");
		logger.info(path);

		OutputStream outputStream = StreamFactory.getOutputStream(location,
				path, append);

		if (outputStream == null) {
			throw new IOException("Error trying to write file " + path);
		}
		PrintStream ps = new PrintStream(outputStream, true);
		property.list(ps);
		outputStream.close();
	}

	/**
	 * Saves the sphinx3 densityfile, a set of density arrays are created and
	 * placed in the given pool.
	 * 
	 * @param pool
	 *            the pool to be saved
	 * @param path
	 *            the name of the data
	 * @param append
	 *            is true, the file will be appended, useful if saving to a ZIP
	 *            or JAR file
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while saving the data
	 */
	private void saveDensityFileAscii(Pool pool, String path, boolean append)
			throws FileNotFoundException, IOException {
		int token_type;
		int numStates;
		int numStreams;
		int numGaussiansPerState;

		//logger.info("Saving density file to: ");
		//logger.info(path);

		OutputStream outputStream = StreamFactory.getOutputStream(location,
				path, append);

		if (outputStream == null) {
			throw new IOException("Error trying to write file " + location
					+ path);
		}
		PrintWriter pw = new PrintWriter(outputStream, true);

		pw.print("param ");
		numStates = pool.getFeature(NUM_SENONES, -1);
		pw.print(numStates + " ");
		numStreams = pool.getFeature(NUM_STREAMS, -1);
		pw.print(numStreams + " ");
		numGaussiansPerState = pool.getFeature(NUM_GAUSSIANS_PER_STATE, -1);
		pw.println(numGaussiansPerState);

		for (int i = 0; i < numStates; i++) {
			pw.println("mgau " + i);
			pw.println("feat " + 0);
			for (int j = 0; j < numGaussiansPerState; j++) {

				pw.print("density" + " \t" + j);

				int id = i * numGaussiansPerState + j;
				float[] density = (float[]) pool.get(id);
				for (int k = 0; k < vectorLength; k++) {
					pw.print(" " + density[k]);
					// System.out.println(" " + i + " " + j + " " + k +
					// " " + density[k]);
				}
				pw.println();
			}
		}
		outputStream.close();
	}

	/**
	 * Saves the sphinx3 densityfile, a set of density arrays are created and
	 * placed in the given pool.
	 * 
	 * @param pool
	 *            the pool to be saved
	 * @param path
	 *            the name of the data
	 * @param append
	 *            is true, the file will be appended, useful if saving to a ZIP
	 *            or JAR file
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while saving the data
	 */
	private void saveDensityFileBinary(Pool pool, String path, boolean append)
			throws FileNotFoundException, IOException {

		int token_type;
		int numStates;
		int numStreams;
		int numGaussiansPerState;

		int checkSum = 0;

		logger.info("Saving density file to: ");
		logger.info(path); 
		
		Properties props = new Properties();

	 	props.setProperty("version", DENSITY_FILE_VERSION);
	 	props.setProperty("chksum0", checksum);

		DataOutputStream dos = writeS3BinaryHeader(location, path, props,
				append);

		numStates = pool.getFeature(NUM_SENONES, -1);
		numStreams = pool.getFeature(NUM_STREAMS, -1);
		numGaussiansPerState = pool.getFeature(NUM_GAUSSIANS_PER_STATE, -1);

		writeInt(dos, numStates);
		writeInt(dos, numStreams);
		writeInt(dos, numGaussiansPerState);

		int rawLength = 0;
		int[] vectorLength = new int[numStreams];
		for (int i = 0; i < numStreams; i++) {
			vectorLength[i] = this.vectorLength;
			writeInt(dos, vectorLength[i]);
			rawLength += numGaussiansPerState * numStates * vectorLength[i];
		}

		// assert numStreams == 1;
		// assert rawLength == numGaussiansPerState * numStates *
		// this.vectorLength;
		writeInt(dos, rawLength);

		// System.out.println("Nstates " + numStates);
		// System.out.println("Nstreams " + numStreams);
		// System.out.println("NgaussiansPerState " + numGaussiansPerState);
		// System.out.println("vectorLength " + vectorLength.length);
		// System.out.println("rawLength " + rawLength);

		int r = 0;
		for (int i = 0; i < numStates; i++) {
			for (int j = 0; j < numStreams; j++) {
				for (int k = 0; k < numGaussiansPerState; k++) {
					int id = i * numStreams * numGaussiansPerState + j
							* numGaussiansPerState + k;
					float[] density = (float[]) pool.get(id);
					// Do checksum here?
					writeFloatArray(dos, density);
				}
			}
		}
		if (doCheckSum) {
			// assert doCheckSum = false: "Checksum not supported";
		}
		// S3 requires some number here....
		writeInt(dos, checkSum);
		// BUG: not checking the check sum yet.
		dos.close();
	}

	/**
	 * Writes the S3 binary header to the given location+path.
	 * 
	 * @param location
	 *            the location of the file
	 * @param path
	 *            the name of the file
	 * @param props
	 *            the properties
	 * @param append
	 *            is true, the file will be appended, useful if saving to a ZIP
	 *            or JAR file
	 * 
	 * @return the output stream positioned after the header
	 * 
	 * @throws IOException
	 *             on error
	 */

	protected DataOutputStream writeS3BinaryHeader(String location,
			String path, Properties props, boolean append) throws IOException {

		OutputStream outputStream = StreamFactory.getOutputStream(location,
				path, append);
		if (doCheckSum) {
			// assert false: "Checksum not supported";
		}
		DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(
				outputStream));

		writeWord(dos, "s3\n");

	 	for (Enumeration e = props.keys(); e.hasMoreElements();) {
	 		String name = (String) e.nextElement();
	 		String value = props.getProperty(name);
	 		writeWord(dos, name + " " + value + "\n");
	 	}
		writeWord(dos, "endhdr\n");

		writeInt(dos, BYTE_ORDER_MAGIC);

		return dos;
	}

	/**
	 * Writes the next word (without surrounding white spaces) to the given
	 * stream.
	 * 
	 * @param dos
	 *            the output stream
	 * @param word
	 *            the next word
	 * 
	 * @throws IOException
	 *             on error
	 */
	void writeWord(DataOutputStream dos, String word) throws IOException {
		dos.writeBytes(word);
	}

	/**
	 * Writes a single char to the stream
	 * 
	 * @param dos
	 *            the stream to read
	 * @param character
	 *            the next character on the stream
	 * 
	 * @throws IOException
	 *             if an error occurs
	 */
	private void writeChar(DataOutputStream dos, char character)
			throws IOException {
		dos.writeByte(character);
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
	 * Writes an integer to the output stream, byte-swapping as necessary
	 * 
	 * @param dos
	 *            the outputstream
	 * @param val
	 *            an integer value
	 * 
	 * @throws IOException
	 *             on error
	 */
	protected void writeInt(DataOutputStream dos, int val) throws IOException {
		if (swap) {
			dos.writeInt(Utilities.swapInteger(val));
		} else {
			dos.writeInt(val);
		}
	}

	/**
	 * Writes a float to the output stream, byte-swapping as necessary
	 * 
	 * @param dos
	 *            the inputstream
	 * @param val
	 *            a float value
	 * 
	 * @throws IOException
	 *             on error
	 */

	protected void writeFloat(DataOutputStream dos, float val)
			throws IOException {
		if (swap) {
			dos.writeFloat(Utilities.swapFloat(val));
		} else {
			dos.writeFloat(val);
			if (val != 0) System.out.print(val);
		}
	}

	// Do we need the method nonZeroFloor??
	/**
	 * If a data point is non-zero and below 'floor' make it equal to floor
	 * (don't floor zero values though).
	 * 
	 * @param data
	 *            the data to floor
	 * @param floor
	 *            the floored value
	 */
	private void nonZeroFloor(float[] data, float floor) {
		for (int i = 0; i < data.length; i++) {
			if (data[i] != 0.0 && data[i] < floor) {
				data[i] = floor;
			}
		}
	}

	/**
	 * If a data point is below 'floor' make it equal to floor.
	 * 
	 * @param data
	 *            the data to floor
	 * @param floor
	 *            the floored value
	 */
	private void floorData(float[] data, float floor) {
		for (int i = 0; i < data.length; i++) {
			if (data[i] < floor) {
				data[i] = floor;
			}
		}
	}

	/**
	 * Normalize the given data
	 * 
	 * @param data
	 *            the data to normalize
	 */
	private void normalize(float[] data) {
		float sum = 0;
		for (int i = 0; i < data.length; i++) {
			sum += data[i];
		}

		if (sum != 0.0f) {
			for (int i = 0; i < data.length; i++) {
				data[i] = data[i] / sum;
			}
		}
	}

	/**
	 * Dump the data
	 * 
	 * @param name
	 *            the name of the data
	 * @param data
	 *            the data itself
	 * 
	 */
	private void dumpData(String name, float[] data) {
		System.out.println(" ----- " + name + " -----------");
		for (int i = 0; i < data.length; i++) {
			System.out.println(name + " " + i + ": " + data[i]);
		}
	}

	/**
	 * Convert to log math
	 * 
	 * @param data
	 *            the data to normalize
	 */
	// linearToLog returns a float, so zero values in linear scale
	// should return -Float.MAX_VALUE.
	private void convertToLogMath(float[] data) {
		for (int i = 0; i < data.length; i++) {
			data[i] = logMath.linearToLog(data[i]);
		}
	}

	/**
	 * Convert from log math
	 * 
	 * @param in
	 *            the data in log scale
	 * @param out
	 *            the data in linear scale
	 */
	protected float[] convertFromLogMath(float[] in, float[] out) {
		// assert in.length == out.length;
		for (int i = 0; i < in.length; i++) {
			out[i] = (float) logMath.logToLinear(in[i]);
			//System.out.println(in[i]+" "+out[i]);
		}
		return out;
	}

	/**
	 * Writes the given number of floats from an array of floats to a stream.
	 * 
	 * @param dos
	 *            the stream to write the data to
	 * @param data
	 *            the array of floats to write to the stream
	 * 
	 * @throws IOException
	 *             if an exception occurs
	 */
	protected void writeFloatArray(DataOutputStream dos, float[] data)
			throws IOException {

		for (int i = 0; i < data.length; i++) {
			writeFloat(dos, data[i]);
		}
	}

	/**
	 * Saves the sphinx3 densityfile, a set of density arrays are created and
	 * placed in the given pool.
	 * 
	 * @param useCDUnits
	 *            if true, uses context dependent units
	 * @param outputStream
	 *            the open output stream to use
	 * @param path
	 *            the path to a density file
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while saving the data
	 */
	private void saveHMMPool(boolean useCDUnits, OutputStream outputStream,
			String path) throws FileNotFoundException, IOException {
		int token_type;
		int numBase;
		int numTri;
		int numStateMap;
		int numTiedState;
		int numStatePerHMM;
		int numContextIndependentTiedState;
		int numTiedTransitionMatrices;

		System.out.println("Saving HMM file to: ");
		System.out.println(path);

		if (outputStream == null) {
			throw new IOException("Error trying to write file " + location
					+ path);
		}
		PrintWriter pw = new PrintWriter(outputStream, true);

		/*
		 * ExtendedStreamTokenizer est = new ExtendedStreamTokenizer
		 * (outputStream, '#', false); Pool pool = new Pool(path);
		 */

		// First, count the HMMs
		numBase = 0;
		numTri = 0;
		numContextIndependentTiedState = 0;
		numStateMap = 0;
		for (Iterator i = hmmManager.getIterator(); i.hasNext();) {
			SenoneHMM hmm = (SenoneHMM) i.next();
			numStateMap += hmm.getOrder() + 1;
			if (hmm.isContextDependent()) {
				numTri++;
			} else {
				numBase++;
				numContextIndependentTiedState += hmm.getOrder();
			}
		}
		pw.println(MODEL_VERSION);
		pw.println(numBase + " n_base");
		pw.println(numTri + " n_tri");
		pw.println(numStateMap + " n_state_map");
		numTiedState = mixtureWeightsPool.getFeature(NUM_SENONES, 0);
		pw.println(numTiedState + " n_tied_state");
		pw.println(numContextIndependentTiedState + " n_tied_ci_state");
		numTiedTransitionMatrices = numBase;
		// assert numTiedTransitionMatrices == matrixPool.size();
		pw.println(numTiedTransitionMatrices + " n_tied_tmat");

		pw.println("#");
		pw.println("# Columns definitions");
		pw.println("#base lft  rt p attrib tmat      ... state id's ...");

		numStatePerHMM = numStateMap / (numTri + numBase);

		// Save the base phones
		for (Iterator i = hmmManager.getIterator(); i.hasNext();) {
			SenoneHMM hmm = (SenoneHMM) i.next();
			if (hmm.isContextDependent()) {
				continue;
			}

			Unit unit = hmm.getUnit();

			String name = unit.getName();
			pw.print(name + "\t");
			String left = "-";
			pw.print(left + "   ");
			String right = "-";
			pw.print(right + " ");
			String position = hmm.getPosition().toString();
			pw.print(position + "\t");
			String attribute;
			if (unit.isFiller()) {
				attribute = FILLER;
			} else {
				attribute = "n/a";
			}
			pw.print(attribute + "\t");
			int tmat = matrixPool.indexOf(hmm.getTransitionMatrix());
			// assert tmat < numTiedTransitionMatrices;
			pw.print(tmat + "\t");

			SenoneSequence ss = hmm.getSenoneSequence();
			Senone[] senones = ss.getSenones();
			for (int j = 0; j < senones.length; j++) {
				int index = senonePool.indexOf(senones[j]);
				// assert index >= 0 && index < numContextIndependentTiedState;
				pw.print(index + "\t");
			}
			pw.println("N");

			 if (logger.isLoggable(Level.FINE)) {
				logger.fine("Saved " + unit);
			}

		}

		// Save the context dependent phones.

		for (Iterator i = hmmManager.getIterator(); i.hasNext();) {
			SenoneHMM hmm = (SenoneHMM) i.next();
			if (!hmm.isContextDependent()) {
				continue;
			}

			Unit unit = hmm.getUnit();
			LeftRightContext context = (LeftRightContext) unit.getContext();
			Unit[] leftContext = context.getLeftContext();
			Unit[] rightContext = context.getRightContext();
			// assert leftContext.length == 1 && rightContext.length == 1;

			String name = unit.getName();
			pw.print(name + "\t");
			String left = leftContext[0].getName();
			pw.print(left + "   ");
			String right = rightContext[0].getName();
			pw.print(right + " ");
			String position = hmm.getPosition().toString();
			pw.print(position + "\t");
			String attribute;
			if (unit.isFiller()) {
				attribute = FILLER;
			} else {
				attribute = "n/a";
			}
			// assert attribute.equals("n/a");
			pw.print(attribute + "\t");
			int tmat = matrixPool.indexOf(hmm.getTransitionMatrix());
			// assert tmat < numTiedTransitionMatrices;
			pw.print(tmat + "\t");

			SenoneSequence ss = hmm.getSenoneSequence();
			Senone[] senones = ss.getSenones();
			for (int j = 0; j < senones.length; j++) {
				int index = senonePool.indexOf(senones[j]);
				// assert index >= 0 && index < numTiedState;
				pw.print(index + "\t");
			}
			pw.println("N");

			if (logger.isLoggable(Level.FINE)) {
				logger.fine("Saved " + unit);
			}

		}

		outputStream.close();
	}

	/**
	 * Gets the senone sequence representing the given senones
	 * 
	 * @param stateid
	 *            is the array of senone state ids
	 * 
	 * @return the senone sequence associated with the states
	 */

	private SenoneSequence getSenoneSequence(int[] stateid) {
		Senone[] senones = new Senone[stateid.length];

		for (int i = 0; i < stateid.length; i++) {
			senones[i] = (Senone) senonePool.get(stateid[i]);
		}

		// TODO: Is there any advantage in trying to pool these?
		return new SenoneSequence(senones);
	}

	/**
	 * Saves the mixture weights
	 * 
	 * @param pool
	 *            the mixture weight pool
	 * @param path
	 *            the path to the mixture weight file
	 * @param append
	 *            is true, the file will be appended, useful if saving to a ZIP
	 *            or JAR file
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while saving the data
	 */
	private void saveMixtureWeightsAscii(Pool pool, String path, boolean append)
			throws FileNotFoundException, IOException {
		logger.info("Saving mixture weights to: ");
		logger.info(path);

		int numStates;
		int numStreams;
		int numGaussiansPerState;

		OutputStream outputStream = StreamFactory.getOutputStream(location,
				path, append);
		if (outputStream == null) {
			throw new IOException("Error trying to write file " + location
					+ path);
		}
		PrintWriter pw = new PrintWriter(outputStream, true);

		pw.print("mixw ");
		numStates = pool.getFeature(NUM_SENONES, -1);
		pw.print(numStates + " ");
		numStreams = pool.getFeature(NUM_STREAMS, -1);
		pw.print(numStreams + " ");
		numGaussiansPerState = pool.getFeature(NUM_GAUSSIANS_PER_STATE, -1);
		pw.println(numGaussiansPerState);

		for (int i = 0; i < numStates; i++) {
			pw.print("mixw [" + i + " 0] ");
			float[] mixtureWeight = new float[numGaussiansPerState];
			float[] logMixtureWeight = (float[]) pool.get(i);
			// there's no pointers in java
			convertFromLogMath(logMixtureWeight, mixtureWeight);
			mixtureWeight = convertFromLogMath(logMixtureWeight, mixtureWeight);
			
			float sum = 0.0f;
			for (int j = 0; j < numGaussiansPerState; j++) {
				sum += mixtureWeight[j];
			}
			pw.println(sum);
			pw.print("\n\t");
			for (int j = 0; j < numGaussiansPerState; j++) {
				pw.print(" " + mixtureWeight[j]);
			}
			pw.println();
		}
		outputStream.close();
	}

	/**
	 * Saves the mixture weights (Binary)
	 * 
	 * @param pool
	 *            the mixture weight pool
	 * @param path
	 *            the path to the mixture weight file
	 * @param append
	 *            is true, the file will be appended, useful if saving to a ZIP
	 *            or JAR file
	 * 
	 * @return a pool of mixture weights
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while saving the data
	 */
	private void saveMixtureWeightsBinary(Pool pool, String path, boolean append)
			throws FileNotFoundException, IOException {
		logger.info("Saving mixture weights to: ");
		logger.info(path);

		int numStates;
		int numStreams;
		int numGaussiansPerState;
		Properties props = new Properties();
		int checkSum = 0;

		props.setProperty("version", MIXW_FILE_VERSION);
		if (doCheckSum) {
			props.setProperty("chksum0", checksum);
		}

		DataOutputStream dos = writeS3BinaryHeader(location, path, props,
				append);

		numStates = pool.getFeature(NUM_SENONES, -1);
		numStreams = pool.getFeature(NUM_STREAMS, -1);
		numGaussiansPerState = pool.getFeature(NUM_GAUSSIANS_PER_STATE, -1);

		writeInt(dos, numStates);
		writeInt(dos, numStreams);
		writeInt(dos, numGaussiansPerState);

		// assert numStreams == 1;

		int rawLength = numGaussiansPerState * numStates * numStreams;
		writeInt(dos, rawLength);

		for (int i = 0; i < numStates; i++) {
			float[] logMixtureWeight = (float[]) pool.get(i);
			float[] mixtureWeight = new float[logMixtureWeight.length];
		  //  mixtureWeight =	convertFromLogMath(logMixtureWeight, mixtureWeight);

			writeFloatArray(dos, logMixtureWeight);
		}
		if (doCheckSum) {
			// assert doCheckSum = false: "Checksum not supported";
			// writeInt(dos, checkSum);
		}

		dos.close();
	}

	/**
	 * Saves the transition matrices
	 * 
	 * @param pool
	 *            the transition matrices pool
	 * @param path
	 *            the path to the transitions matrices
	 * @param append
	 *            is true, the file will be appended, useful if saving to a ZIP
	 *            or JAR file
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while saving the data
	 */
	protected void saveTransitionMatricesAscii(Pool pool, String path,
			boolean append) throws FileNotFoundException, IOException {
		OutputStream outputStream = StreamFactory.getOutputStream(location,
				path, append);
		if (outputStream == null) {
			throw new IOException("Error trying to write file " + location
					+ path);
		}
		PrintWriter pw = new PrintWriter(outputStream, true);

		logger.info("Saving transition matrices to: ");
		logger.info(path);
		int numMatrices = pool.size();
		int numStates;
		float[][] tmat;

		// assert numMatrices > 0;
		tmat = (float[][]) pool.get(0);
		numStates = tmat[0].length;

		pw.println("tmat " + numMatrices + " " + numStates);

		for (int i = 0; i < numMatrices; i++) {
			pw.println("tmat [" + i + "]");
			tmat = (float[][]) pool.get(i);
			for (int j = 0; j < numStates; j++) {
				for (int k = 0; k < numStates; k++) {
					// the last row is just zeros, so we just do
					// the first (numStates - 1) rows
					if (j < numStates - 1) {
						if (sparseForm) {
							if (k < j) {
								pw.print("\t");
							}
							if (k == j || k == j + 1) {
								pw.print((float) logMath
										.logToLinear(tmat[j][k]));
							}
						} else {
							pw.print((float) logMath.logToLinear(tmat[j][k]));
						}
						if (numStates - 1 == k) {
							pw.println();
						} else {
							pw.print(" ");
						}

					}

					if (logger.isLoggable(Level.FINE)) {
						logger.fine("tmat j " + j + " k " + k + " tm "
								+ tmat[j][k]);
					}
				}
			}
		}
		outputStream.close();
	}

	/**
	 * Saves the transition matrices (Binary)
	 * 
	 * @param pool
	 *            the transition matrices pool
	 * @param path
	 *            the path to the transitions matrices
	 * @param append
	 *            is true, the file will be appended, useful if saving to a ZIP
	 *            or JAR file
	 * 
	 * @return a pool of transition matrices
	 * 
	 * @throws FileNotFoundException
	 *             if a file cannot be found
	 * @throws IOException
	 *             if an error occurs while saving the data
	 */
	protected void saveTransitionMatricesBinary(Pool pool, String path,
			boolean append) throws FileNotFoundException, IOException {

		logger.info("Saving transition matrices to: ");
		logger.info(path);
		int numMatrices;
		int numStates;
		int numRows;
		int numValues;
		Properties props = new Properties();

		int checkSum = 0;

		props.setProperty("version", TMAT_FILE_VERSION);
		if (doCheckSum) {
			props.setProperty("chksum0", checksum);
		}

		DataOutputStream dos = writeS3BinaryHeader(location, path, props,
				append);

		numMatrices = pool.size();
		// assert numMatrices > 0;
		writeInt(dos, numMatrices);

		float[][] tmat = (float[][]) pool.get(0);
		numStates = tmat[0].length;
		numRows = numStates - 1;

		writeInt(dos, numRows);
		writeInt(dos, numStates);
		numValues = numStates * numRows * numMatrices;
		writeInt(dos, numValues);

		for (int i = 0; i < numMatrices; i++) {
			float[] logTmatRow;
			float[] tmatRow;

			tmat = (float[][]) pool.get(i);

			// Last row should be all zeroes
			logTmatRow = tmat[numStates - 1];
			tmatRow = new float[logTmatRow.length];

			for (int j = 0; j < numStates; j++) {
				// assert tmatRow[j] == 0.0f;
			}

			for (int j = 0; j < numRows; j++) {
				logTmatRow = tmat[j];
				tmatRow = new float[logTmatRow.length];
				//tmatRow = convertFromLogMath(logTmatRow, tmatRow);
				writeFloatArray(dos, logTmatRow);
			}
		}
		if (doCheckSum) {
			// assert doCheckSum = false: "Checksum not supported";
			// writeInt(dos, checkSum);
		}
		dos.close();
	}

	/**
	 * Returns the properties of the saved AcousticModel.
	 * 
	 * @return the properties of the saved AcousticModel, or null if it has no
	 *         properties
	 */
	public SphinxProperties getModelProperties() {
		return acousticProperties;
	}

	/**
	 * Gets the pool of means for this saver
	 * 
	 * @return the pool
	 */
	public Pool getMeansPool() {
		return meansPool;
	}

	/**
	 * Gets the pool of means transformation matrices for this saver
	 * 
	 * @return the pool
	 */
	public Pool getMeansTransformationMatrixPool() {
		return meanTransformationMatrixPool;
	}

	/**
	 * Gets the pool of means transformation vectors for this saver
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
	 * Gets the pool of variance transformation vectors for this saver
	 * 
	 * @return the pool
	 */
	public Pool getVarianceTransformationVectorPool() {
		return varianceTransformationVectorPool;
	}

	/*
	 * Gets the senone pool for this saver
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
	 * Returns the hmm manager associated with this saver
	 * 
	 * @return the hmm Manager
	 */
	public HMMManager getHMMManager() {
		return hmmManager;
	}

	/**
	 * Log info about this saver
	 */
	public void logInfo() {
		logger.info("Sphinx3Saver");
		meansPool.logInfo(logger);
		variancePool.logInfo(logger);
		matrixPool.logInfo(logger);
		senonePool.logInfo(logger);
		meanTransformationMatrixPool.logInfo(logger);
		meanTransformationVectorPool.logInfo(logger);
		varianceTransformationMatrixPool.logInfo(logger);
		varianceTransformationVectorPool.logInfo(logger);
		mixtureWeightsPool.logInfo(logger);
		senonePool.logInfo(logger);
		logger.info("Context Independent Unit Entries: "
				+ contextIndependentUnits.size());
		hmmManager.logInfo(logger);
	}
    /**
     * Returns the name of this AcousticModel, or null if it has no name.
     *
     * @return the name of this AcousticModel, or null if it has no name
     */
    public String getName() {
	return name;
    }
}
