// Decompiled by Jad v1.5.8g. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   ModelLoader.java

package edu.cmu.sphinx.linguist.acoustic.tiedstate;

import edu.cmu.sphinx.linguist.acoustic.*;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.*;
import edu.cmu.sphinx.util.*;
import edu.cmu.sphinx.util.props.*;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipException;

public class ModelLoader implements Loader {

	public static final String PROP_LOG_MATH = "logMath";

	public static final String PROP_UNIT_MANAGER = "unitManager";

	public static final String PROP_IS_BINARY = "isBinary";

	public static final boolean PROP_IS_BINARY_DEFAULT = true;

	public static final String PROP_MODEL = "modelDefinition";

	public static final String PROP_MODEL_DEFAULT = "model.mdef";

	public static final String PROP_DATA_LOCATION = "dataLocation";

	public static final String PROP_DATA_LOCATION_DEFAULT = "data";

	public static final String PROP_PROPERTIES_FILE = "propertiesFile";

	public static final String PROP_PROPERTIES_FILE_DEFAULT = "model.props";

	public static final String PROP_VECTOR_LENGTH = "vectorLength";

	public static final int PROP_VECTOR_LENGTH_DEFAULT = 39;

	public static final String PROP_SPARSE_FORM = "sparseForm";

	public static final boolean PROP_SPARSE_FORM_DEFAULT = true;

	public static final String PROP_USE_CD_UNITS = "useCDUnits";

	public static final boolean PROP_USE_CD_UNITS_DEFAULT = true;

	public static final String PROP_MC_FLOOR = "MixtureComponentScoreFloor";

	public static final float PROP_MC_FLOOR_DEFAULT = 0F;

	public static final String PROP_VARIANCE_FLOOR = "varianceFloor";

	public static final float PROP_VARIANCE_FLOOR_DEFAULT = 0.0001F;

	public static final String PROP_MW_FLOOR = "mixtureWeightFloor";

	public static final float PROP_MW_FLOOR_DEFAULT = 1E-007F;

	protected static final String NUM_SENONES = "num_senones";

	protected static final String NUM_GAUSSIANS_PER_STATE = "num_gaussians";

	protected static final String NUM_STREAMS = "num_streams";

	protected static final String FILLER = "filler";

	protected static final String SILENCE_CIPHONE = "SIL";

	protected static final int BYTE_ORDER_MAGIC = 0x11223344;

	public static final String MODEL_VERSION = "0.3";

	protected static final int CONTEXT_SIZE = 1;

	private Pool meansPool;

	private Pool variancePool;

	private Pool matrixPool;

	private Pool meanTransformationMatrixPool;

	private Pool meanTransformationVectorPool;

	private Pool varianceTransformationMatrixPool;

	private Pool varianceTransformationVectorPool;

	private Pool mixtureWeightsPool;

	private Pool senonePool;

	private Map contextIndependentUnits;

	private HMMManager hmmManager;

	private LogMath logMath;

	private UnitManager unitManager;

	private Properties properties;

	private boolean swap;

	protected static final String DENSITY_FILE_VERSION = "1.0";

	protected static final String MIXW_FILE_VERSION = "1.0";

	protected static final String TMAT_FILE_VERSION = "1.0";

	private String name;

	private Logger logger;

	private boolean binary;

	private boolean sparseForm;

	private int vectorLength;

	private String location;

	private String model;

	private String dataDir;

	private String propsFile;

	private float distFloor;

	private float mixtureWeightFloor;

	private float varianceFloor;

	private boolean useCDUnits;

	static final boolean $assertionsDisabled; /* synthetic field */

	public void register(String name, Registry registry)
			throws PropertyException {
		this.name = name;
		registry.register("logMath", PropertyType.COMPONENT);
		registry.register("unitManager", PropertyType.COMPONENT);
		registry.register("isBinary", PropertyType.BOOLEAN);
		registry.register("sparseForm", PropertyType.BOOLEAN);
		registry.register("vectorLength", PropertyType.INT);
		registry.register("modelDefinition", PropertyType.STRING);
		registry.register("dataLocation", PropertyType.STRING);
		registry.register("propertiesFile", PropertyType.STRING);
		registry.register("MixtureComponentScoreFloor", PropertyType.FLOAT);
		registry.register("mixtureWeightFloor", PropertyType.FLOAT);
		registry.register("varianceFloor", PropertyType.FLOAT);
		registry.register("useCDUnits", PropertyType.BOOLEAN);
	}

	public void newProperties(PropertySheet ps) throws PropertyException {
		logger = ps.getLogger();
		propsFile = ps.getString("propertiesFile", "model.props");
		logMath = (LogMath) ps.getComponent("logMath",
				edu.cmu.sphinx.util.LogMath.class);
		unitManager = (UnitManager) ps.getComponent("unitManager",
				edu.cmu.sphinx.linguist.acoustic.UnitManager.class);
		binary = ps.getBoolean("isBinary", getIsBinaryDefault());
		sparseForm = ps.getBoolean("sparseForm", getSparseFormDefault());
		vectorLength = ps.getInt("vectorLength", getVectorLengthDefault());
		model = ps.getString("modelDefinition", getModelDefault());
		dataDir = ps.getString("dataLocation", getDataLocationDefault()) + "/";
		distFloor = ps.getFloat("MixtureComponentScoreFloor", 0.0F);
		mixtureWeightFloor = ps.getFloat("mixtureWeightFloor", 1E-007F);
		varianceFloor = ps.getFloat("varianceFloor", 0.0001F);
		useCDUnits = ps.getBoolean("useCDUnits", true);
	}

	private void loadProperties() {
		if (properties == null) {
			properties = new Properties();
			try {
				URL url = getClass().getResource(propsFile);
				properties.load(url.openStream());
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}

	private boolean getIsBinaryDefault() {
		loadProperties();
		String binary = (String) properties.get("isBinary");
		if (binary != null)
			return Boolean.valueOf(binary).equals(Boolean.TRUE);
		else
			return true;
	}

	private boolean getSparseFormDefault() {
		loadProperties();
		String sparse = (String) properties.get("sparseForm");
		if (sparse != null)
			return Boolean.valueOf(binary).equals(Boolean.TRUE);
		else
			return true;
	}

	private int getVectorLengthDefault() {
		loadProperties();
		String length = (String) properties.get("vectorLength");
		if (length != null)
			return Integer.parseInt(length);
		else
			return 39;
	}

	private String getModelDefault() {
		loadProperties();
		String mdef = (String) properties.get("modelDefinition");
		if (mdef != null)
			return mdef;
		else
			return "model.mdef";
	}

	private String getDataLocationDefault() {
		loadProperties();
		String location = (String) properties.get("dataLocation");
		if (location != null)
			return location;
		else
			return "data";
	}

	public String getName() {
		return name;
	}

	public void load() throws IOException {
		hmmManager = new HMMManager();
		contextIndependentUnits = new LinkedHashMap();
		meanTransformationMatrixPool = createDummyMatrixPool("meanTransformationMatrix");
		meanTransformationVectorPool = createDummyVectorPool("meanTransformationMatrix");
		varianceTransformationMatrixPool = createDummyMatrixPool("varianceTransformationMatrix");
		varianceTransformationVectorPool = createDummyVectorPool("varianceTransformationMatrix");
		loadModelFiles(model);
	}

	protected HMMManager getHmmManager() {
		return hmmManager;
	}

	protected Pool getMatrixPool() {
		return matrixPool;
	}

	protected Pool getMixtureWeightsPool() {
		return mixtureWeightsPool;
	}

	protected Properties getProperties() {
		if (properties == null)
			loadProperties();
		return properties;
	}

	protected String getLocation() {
		return location;
	}

	private void loadModelFiles(String modelName) throws FileNotFoundException,
			IOException, ZipException {
		logger.config("Loading Sphinx3 acoustic model: " + modelName);
		logger.config("    Path      : " + location);
		logger.config("    modellName: " + model);
		logger.config("    dataDir   : " + dataDir);
		if (binary) {
			meansPool = loadDensityFileBinary(dataDir + "means",
					-3.402823E+038F);
			variancePool = loadDensityFileBinary(dataDir + "variances",
					varianceFloor);
			mixtureWeightsPool = loadMixtureWeightsBinary(dataDir
					+ "mixture_weights", mixtureWeightFloor);
			matrixPool = loadTransitionMatricesBinary(dataDir
					+ "transition_matrices");
		} else {
			meansPool = loadDensityFileAscii(dataDir + "means.ascii",
					-3.402823E+038F);
			variancePool = loadDensityFileAscii(dataDir + "variances.ascii",
					varianceFloor);
			mixtureWeightsPool = loadMixtureWeightsAscii(dataDir
					+ "mixture_weights.ascii", mixtureWeightFloor);
			matrixPool = loadTransitionMatricesAscii(dataDir
					+ "transition_matrices.ascii");
		}
		senonePool = createSenonePool(distFloor, varianceFloor);
		InputStream modelStream = getClass().getResourceAsStream(model);
		if (modelStream == null) {
			throw new IOException("can't find model " + model);
		} else {
			loadHMMPool(useCDUnits, modelStream, location + File.separator
					+ model);
			return;
		}
	}

	public Map getContextIndependentUnits() {
		return contextIndependentUnits;
	}

	private Pool createSenonePool(float distFloor, float varianceFloor) {
		Pool pool = new Pool("senones");
		int numMixtureWeights = mixtureWeightsPool.size();
		int numMeans = meansPool.size();
		int numVariances = variancePool.size();
		int numGaussiansPerSenone = mixtureWeightsPool.getFeature(
				"num_gaussians", 0);
		int numSenones = mixtureWeightsPool.getFeature("num_senones", 0);
		int whichGaussian = 0;
		logger.fine("NG " + numGaussiansPerSenone);
		logger.fine("NS " + numSenones);
		logger.fine("NMIX " + numMixtureWeights);
		logger.fine("NMNS " + numMeans);
		logger.fine("NMNS " + numVariances);
		if (!$assertionsDisabled && numGaussiansPerSenone <= 0)
			throw new AssertionError();
		if (!$assertionsDisabled && numMixtureWeights != numSenones)
			throw new AssertionError();
		if (!$assertionsDisabled
				&& numVariances != numSenones * numGaussiansPerSenone)
			throw new AssertionError();
		if (!$assertionsDisabled
				&& numMeans != numSenones * numGaussiansPerSenone)
			throw new AssertionError();
		for (int i = 0; i < numSenones; i++) {
			MixtureComponent mixtureComponents[] = new MixtureComponent[numGaussiansPerSenone];
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

	private SphinxProperties loadAcousticPropertiesFile(URL url)
			throws FileNotFoundException, IOException {
		String context = "acoustic." + getName() + "." + url;
		SphinxProperties.initContext(context, url);
		return SphinxProperties.getSphinxProperties(context);
	}

	private Pool loadDensityFileAscii(String path, float floor)
			throws FileNotFoundException, IOException {
		InputStream inputStream = getClass().getResourceAsStream(path);
		if (inputStream == null)
			throw new FileNotFoundException("Error trying to read file "
					+ location + path);
		ExtendedStreamTokenizer est = new ExtendedStreamTokenizer(inputStream,
				35, false);
		Pool pool = new Pool(path);
		logger.fine("Loading density file from: " + path);
		est.expectString("param");
		int numStates = est.getInt("numStates");
		int numStreams = est.getInt("numStreams");
		int numGaussiansPerState = est.getInt("numGaussiansPerState");
		pool.setFeature("num_senones", numStates);
		pool.setFeature("num_streams", numStreams);
		pool.setFeature("num_gaussians", numGaussiansPerState);
		for (int i = 0; i < numStates; i++) {
			est.expectString("mgau");
			est.expectInt("mgau index", i);
			est.expectString("feat");
			est.expectInt("feat index", 0);
			for (int j = 0; j < numGaussiansPerState; j++) {
				est.expectString("density");
				est.expectInt("densityValue", j);
				float density[] = new float[vectorLength];
				for (int k = 0; k < vectorLength; k++) {
					density[k] = est.getFloat("val");
					if (density[k] < floor)
						density[k] = floor;
				}

				int id = i * numGaussiansPerState + j;
				pool.put(id, density);
			}

		}

		est.close();
		return pool;
	}

	private Pool loadDensityFileBinary(String path, float floor)
			throws FileNotFoundException, IOException {
		Properties props = new Properties();
		int blockSize = 0;
		DataInputStream dis = readS3BinaryHeader(location, path, props);
		String version = props.getProperty("version");
		if (version == null || !version.equals("1.0"))
			throw new IOException("Unsupported version in " + path);
		String checksum = props.getProperty("chksum0");
		boolean doCheckSum = checksum != null && checksum.equals("yes");
		int numStates = readInt(dis);
		int numStreams = readInt(dis);
		int numGaussiansPerState = readInt(dis);
		int vectorLength[] = new int[numStreams];
		for (int i = 0; i < numStreams; i++)
			vectorLength[i] = readInt(dis);

		int rawLength = readInt(dis);
		for (int i = 0; i < numStreams; i++)
			blockSize += vectorLength[i];

		if (!$assertionsDisabled
				&& rawLength != numGaussiansPerState * blockSize * numStates)
			throw new AssertionError();
		if (!$assertionsDisabled && numStreams != 1)
			throw new AssertionError();
		Pool pool = new Pool(path);
		pool.setFeature("num_senones", numStates);
		pool.setFeature("num_streams", numStreams);
		pool.setFeature("num_gaussians", numGaussiansPerState);
		int r = 0;
		for (int i = 0; i < numStates; i++) {
			for (int j = 0; j < numStreams; j++) {
				for (int k = 0; k < numGaussiansPerState; k++) {
					float density[] = readFloatArray(dis, vectorLength[j]);
					floorData(density, floor);
					pool.put(i * numGaussiansPerState + k, density);
				}

			}

		}

		int checkSum = readInt(dis);
		dis.close();
		return pool;
	}

	protected DataInputStream readS3BinaryHeader(String location, String path,
			Properties props) throws IOException {
		InputStream inputStream = getClass().getResourceAsStream(path);
		if (inputStream == null)
			throw new IOException("Can't open " + path);
		DataInputStream dis = new DataInputStream(new BufferedInputStream(
				inputStream));
		String id = readWord(dis);
		if (!id.equals("s3"))
			throw new IOException("Not proper s3 binary file " + location
					+ path);
		String name;
		String value;
		for (; (name = readWord(dis)) != null && !name.equals("endhdr"); props
				.setProperty(name, value))
			value = readWord(dis);

		int byteOrderMagic = dis.readInt();
		if (byteOrderMagic == 0x11223344)
			swap = false;
		else if (byteSwap(byteOrderMagic) == 0x11223344)
			swap = true;
		else
			throw new IOException("Corrupt S3 file " + location + path);
		return dis;
	}

	String readWord(DataInputStream dis) throws IOException {
		StringBuffer sb = new StringBuffer();
		char c;
		do
			c = readChar(dis);
		while (Character.isWhitespace(c));
		do {
			sb.append(c);
			c = readChar(dis);
		} while (!Character.isWhitespace(c));
		return sb.toString();
	}

	private char readChar(DataInputStream dis) throws IOException {
		return (char) dis.readByte();
	}

	private int byteSwap(int val) {
		return 0xff & val >> 24 | 0xff00 & val >> 8 | 0xff0000 & val << 8
				| 0xff000000 & val << 24;
	}

	protected int readInt(DataInputStream dis) throws IOException {
		if (swap)
			return Utilities.readLittleEndianInt(dis);
		else
			return dis.readInt();
	}

	protected float readFloat(DataInputStream dis) throws IOException {
		float val;
		if (swap)
			val = Utilities.readLittleEndianFloat(dis);
		else
			val = dis.readFloat();
		return val;
	}

	protected void nonZeroFloor(float data[], float floor) {
		for (int i = 0; i < data.length; i++)
			if ((double) data[i] != 0.0D && data[i] < floor)
				data[i] = floor;

	}

	private void floorData(float data[], float floor) {
		for (int i = 0; i < data.length; i++)
			if (data[i] < floor)
				data[i] = floor;

	}

	protected void normalize(float data[]) {
		float sum = 0.0F;
		for (int i = 0; i < data.length; i++)
			sum += data[i];

		if (sum != 0.0F) {
			for (int i = 0; i < data.length; i++)
				data[i] = data[i] / sum;

		}
	}

	private void dumpData(String name, float data[]) {
		System.out.println(" ----- " + name + " -----------");
		for (int i = 0; i < data.length; i++)
			System.out.println(name + " " + i + ": " + data[i]);

	}

	protected void convertToLogMath(float data[]) {
		for (int i = 0; i < data.length; i++)
			data[i] = logMath.linearToLog(data[i]);

	}

	protected float[] readFloatArray(DataInputStream dis, int size)
			throws IOException {
		float data[] = new float[size];
		for (int i = 0; i < size; i++)
			data[i] = readFloat(dis);

		return data;
	}

	protected Pool loadHMMPool(boolean useCDUnits, InputStream inputStream,
			String path) throws FileNotFoundException, IOException {
		ExtendedStreamTokenizer est = new ExtendedStreamTokenizer(inputStream,
				35, false);
		Pool pool = new Pool(path);
		logger.fine("Loading HMM file from: " + path);
		est.expectString("0.3");
		int numBase = est.getInt("numBase");
		est.expectString("n_base");
		int numTri = est.getInt("numTri");
		est.expectString("n_tri");
		int numStateMap = est.getInt("numStateMap");
		est.expectString("n_state_map");
		int numTiedState = est.getInt("numTiedState");
		est.expectString("n_tied_state");
		int numContextIndependentTiedState = est
				.getInt("numContextIndependentTiedState");
		est.expectString("n_tied_ci_state");
		int numTiedTransitionMatrices = est.getInt("numTiedTransitionMatrices");
		est.expectString("n_tied_tmat");
		int numStatePerHMM = numStateMap / (numTri + numBase);
		if (!$assertionsDisabled
				&& numTiedState != mixtureWeightsPool.getFeature("num_senones",
						0))
			throw new AssertionError();
		if (!$assertionsDisabled
				&& numTiedTransitionMatrices != matrixPool.size())
			throw new AssertionError();
		for (int i = 0; i < numBase; i++) {
			String name = est.getString();
			String left = est.getString();
			String right = est.getString();
			String position = est.getString();
			String attribute = est.getString();
			int tmat = est.getInt("tmat");
			int stid[] = new int[numStatePerHMM - 1];
			for (int j = 0; j < numStatePerHMM - 1; j++) {
				stid[j] = est.getInt("j");
				if (!$assertionsDisabled
						&& (stid[j] < 0 || stid[j] >= numContextIndependentTiedState))
					throw new AssertionError();
			}

			est.expectString("N");
			if (!$assertionsDisabled && !left.equals("-"))
				throw new AssertionError();
			if (!$assertionsDisabled && !right.equals("-"))
				throw new AssertionError();
			if (!$assertionsDisabled && !position.equals("-"))
				throw new AssertionError();
			if (!$assertionsDisabled && tmat >= numTiedTransitionMatrices)
				throw new AssertionError();
			Unit unit = unitManager.getUnit(name, attribute.equals("filler"));
			contextIndependentUnits.put(unit.getName(), unit);
			if (logger.isLoggable(Level.FINE))
				logger.fine("Loaded " + unit);
			if (unit.isFiller() && unit.getName().equals("SIL"))
				unit = UnitManager.SILENCE;
			float transitionMatrix[][] = (float[][]) matrixPool.get(tmat);
			SenoneSequence ss = getSenoneSequence(stid);
			edu.cmu.sphinx.linguist.acoustic.HMM hmm = new SenoneHMM(unit, ss,
					transitionMatrix, HMMPosition.lookup(position));
			hmmManager.put(hmm);
		}

		String lastUnitName = "";
		Unit lastUnit = null;
		int lastStid[] = null;
		SenoneSequence lastSenoneSequence = null;
		for (int i = 0; i < numTri; i++) {
			String name = est.getString();
			String left = est.getString();
			String right = est.getString();
			String position = est.getString();
			String attribute = est.getString();
			int tmat = est.getInt("tmat");
			int stid[] = new int[numStatePerHMM - 1];
			for (int j = 0; j < numStatePerHMM - 1; j++) {
				stid[j] = est.getInt("j");
				if (!$assertionsDisabled
						&& (stid[j] < numContextIndependentTiedState || stid[j] >= numTiedState))
					throw new AssertionError();
			}

			est.expectString("N");
			if (!$assertionsDisabled && left.equals("-"))
				throw new AssertionError();
			if (!$assertionsDisabled && right.equals("-"))
				throw new AssertionError();
			if (!$assertionsDisabled && position.equals("-"))
				throw new AssertionError();
			if (!$assertionsDisabled && !attribute.equals("n/a"))
				throw new AssertionError();
			if (!$assertionsDisabled && tmat >= numTiedTransitionMatrices)
				throw new AssertionError();
			if (!useCDUnits)
				continue;
			Unit unit = null;
			String unitName = name + " " + left + " " + right;
			if (unitName.equals(lastUnitName)) {
				unit = lastUnit;
			} else {
				Unit leftContext[] = new Unit[1];
				leftContext[0] = (Unit) contextIndependentUnits.get(left);
				Unit rightContext[] = new Unit[1];
				rightContext[0] = (Unit) contextIndependentUnits.get(right);
				edu.cmu.sphinx.linguist.acoustic.Context context = LeftRightContext
						.get(leftContext, rightContext);
				unit = unitManager.getUnit(name, false, context);
			}
			lastUnitName = unitName;
			lastUnit = unit;
			if (logger.isLoggable(Level.FINE))
				logger.fine("Loaded " + unit);
			float transitionMatrix[][] = (float[][]) matrixPool.get(tmat);
			SenoneSequence ss = lastSenoneSequence;
			if (ss == null || !sameSenoneSequence(stid, lastStid))
				ss = getSenoneSequence(stid);
			lastSenoneSequence = ss;
			lastStid = stid;
			edu.cmu.sphinx.linguist.acoustic.HMM hmm = new SenoneHMM(unit, ss,
					transitionMatrix, HMMPosition.lookup(position));
			hmmManager.put(hmm);
		}

		est.close();
		return pool;
	}

	protected boolean sameSenoneSequence(int ssid1[], int ssid2[]) {
		if (ssid1.length == ssid2.length) {
			for (int i = 0; i < ssid1.length; i++)
				if (ssid1[i] != ssid2[i])
					return false;

			return true;
		} else {
			return false;
		}
	}

	protected SenoneSequence getSenoneSequence(int stateid[]) {
		Senone senones[] = new Senone[stateid.length];
		for (int i = 0; i < stateid.length; i++)
			senones[i] = (Senone) senonePool.get(stateid[i]);

		return new SenoneSequence(senones);
	}

	private Pool loadMixtureWeightsAscii(String path, float floor)
			throws FileNotFoundException, IOException {
		logger.fine("Loading mixture weights from: " + path);
		InputStream inputStream = StreamFactory.getInputStream(location, path);
		Pool pool = new Pool(path);
		ExtendedStreamTokenizer est = new ExtendedStreamTokenizer(inputStream,
				35, false);
		est.expectString("mixw");
		int numStates = est.getInt("numStates");
		int numStreams = est.getInt("numStreams");
		int numGaussiansPerState = est.getInt("numGaussiansPerState");
		pool.setFeature("num_senones", numStates);
		pool.setFeature("num_streams", numStreams);
		pool.setFeature("num_gaussians", numGaussiansPerState);
		for (int i = 0; i < numStates; i++) {
			est.expectString("mixw");
			est.expectString("[" + i);
			est.expectString("0]");
			float total = est.getFloat("total");
			float logMixtureWeight[] = new float[numGaussiansPerState];
			for (int j = 0; j < numGaussiansPerState; j++) {
				float val = est.getFloat("mixwVal");
				if (val < floor)
					val = floor;
				logMixtureWeight[j] = val;
			}

			convertToLogMath(logMixtureWeight);
			pool.put(i, logMixtureWeight);
		}

		est.close();
		return pool;
	}

	private Pool loadMixtureWeightsBinary(String path, float floor)
			throws FileNotFoundException, IOException {
		logger.fine("Loading mixture weights from: " + path);
		Properties props = new Properties();
		DataInputStream dis = readS3BinaryHeader(location, path, props);
		String version = props.getProperty("version");
		if (version == null || !version.equals("1.0"))
			throw new IOException("Unsupported version in " + path);
		String checksum = props.getProperty("chksum0");
		boolean doCheckSum = checksum != null && checksum.equals("yes");
		Pool pool = new Pool(path);
		int numStates = readInt(dis);
		int numStreams = readInt(dis);
		int numGaussiansPerState = readInt(dis);
		int numValues = readInt(dis);
		if (!$assertionsDisabled
				&& numValues != numStates * numStreams * numGaussiansPerState)
			throw new AssertionError();
		if (!$assertionsDisabled && numStreams != 1)
			throw new AssertionError();
		pool.setFeature("num_senones", numStates);
		pool.setFeature("num_streams", numStreams);
		pool.setFeature("num_gaussians", numGaussiansPerState);
		for (int i = 0; i < numStates; i++) {
			float logMixtureWeight[] = readFloatArray(dis, numGaussiansPerState);
			normalize(logMixtureWeight);
			floorData(logMixtureWeight, floor);
			convertToLogMath(logMixtureWeight);
			pool.put(i, logMixtureWeight);
		}

		dis.close();
		return pool;
	}

	protected Pool loadTransitionMatricesAscii(String path)
			throws FileNotFoundException, IOException {
		InputStream inputStream = StreamFactory.getInputStream(location, path);
		logger.fine("Loading transition matrices from: " + path);
		Pool pool = new Pool(path);
		ExtendedStreamTokenizer est = new ExtendedStreamTokenizer(inputStream,
				35, false);
		est.expectString("tmat");
		int numMatrices = est.getInt("numMatrices");
		int numStates = est.getInt("numStates");
		logger.fine("with " + numMatrices + " and " + numStates
				+ " states, in " + (sparseForm ? "sparse" : "dense") + " form");
		for (int i = 0; i < numMatrices; i++) {
			est.expectString("tmat");
			est.expectString("[" + i + "]");
			float tmat[][] = new float[numStates][numStates];
			for (int j = 0; j < numStates; j++) {
				for (int k = 0; k < numStates; k++) {
					if (j < numStates - 1)
						if (sparseForm) {
							if (k == j || k == j + 1)
								tmat[j][k] = est.getFloat("tmat value");
						} else {
							tmat[j][k] = est.getFloat("tmat value");
						}
					tmat[j][k] = logMath.linearToLog(tmat[j][k]);
					if (logger.isLoggable(Level.FINE))
						logger.fine("tmat j " + j + " k " + k + " tm "
								+ tmat[j][k]);
				}

			}

			pool.put(i, tmat);
		}

		est.close();
		return pool;
	}

	protected Pool loadTransitionMatricesBinary(String path)
			throws FileNotFoundException, IOException {
		logger.fine("Loading transition matrices from: " + path);
		Properties props = new Properties();
		DataInputStream dis = readS3BinaryHeader(location, path, props);
		String version = props.getProperty("version");
		if (version == null || !version.equals("1.0"))
			throw new IOException("Unsupported version in " + path);
		String checksum = props.getProperty("chksum0");
		boolean doCheckSum = checksum != null && checksum.equals("yes");
		Pool pool = new Pool(path);
		int numMatrices = readInt(dis);
		int numRows = readInt(dis);
		int numStates = readInt(dis);
		int numValues = readInt(dis);
		if (!$assertionsDisabled
				&& numValues != numStates * numRows * numMatrices)
			throw new AssertionError();
		for (int i = 0; i < numMatrices; i++) {
			float tmat[][] = new float[numStates][];
			tmat[numStates - 1] = new float[numStates];
			convertToLogMath(tmat[numStates - 1]);
			for (int j = 0; j < numRows; j++) {
				tmat[j] = readFloatArray(dis, numStates);
				nonZeroFloor(tmat[j], 0.0F);
				normalize(tmat[j]);
				convertToLogMath(tmat[j]);
			}

			pool.put(i, tmat);
		}

		dis.close();
		return pool;
	}

	private Pool createDummyMatrixPool(String name) {
		Pool pool = new Pool(name);
		float matrix[][] = new float[vectorLength][vectorLength];
		logger.fine("creating dummy matrix pool " + name);
		for (int i = 0; i < vectorLength; i++) {
			for (int j = 0; j < vectorLength; j++)
				if (i == j)
					matrix[i][j] = 1.0F;
				else
					matrix[i][j] = 0.0F;

		}

		pool.put(0, matrix);
		return pool;
	}

	private Pool createDummyVectorPool(String name) {
		logger.fine("creating dummy vector pool " + name);
		Pool pool = new Pool(name);
		float vector[] = new float[vectorLength];
		for (int i = 0; i < vectorLength; i++)
			vector[i] = 0.0F;

		pool.put(0, vector);
		return pool;
	}

	public Pool getMeansPool() {
		return meansPool;
	}

	public Pool getMeansTransformationMatrixPool() {
		return meanTransformationMatrixPool;
	}

	public Pool getMeansTransformationVectorPool() {
		return meanTransformationVectorPool;
	}

	public Pool getVariancePool() {
		return variancePool;
	}

	public Pool getVarianceTransformationMatrixPool() {
		return varianceTransformationMatrixPool;
	}

	public Pool getVarianceTransformationVectorPool() {
		return varianceTransformationVectorPool;
	}

	public Pool getMixtureWeightPool() {
		return mixtureWeightsPool;
	}

	public Pool getTransitionMatrixPool() {
		return matrixPool;
	}

	public Pool getSenonePool() {
		return senonePool;
	}

	public int getLeftContextSize() {
		return 1;
	}

	public int getRightContextSize() {
		return 1;
	}

	public HMMManager getHMMManager() {
		return hmmManager;
	}

	public void logInfo() {
		logger.info("ModelLoader");
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

	static {
		$assertionsDisabled = !(edu.cmu.sphinx.model.acoustic.TIDIGITS_8gau_13dCep_16k_40mel_130Hz_6800Hz.ModelLoader.class)
				.desiredAssertionStatus();
	}
}
