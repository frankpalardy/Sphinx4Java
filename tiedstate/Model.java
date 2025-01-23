// Decompiled by Jad v1.5.8g. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   Model.java

package edu.cmu.sphinx.linguist.acoustic.tiedstate;

import edu.cmu.sphinx.linguist.acoustic.*;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.*;
import edu.cmu.sphinx.util.Timer;
import edu.cmu.sphinx.util.props.*;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Model implements AcousticModel, Configurable {

	public Model() {
		useComposites = false;
		compositeSenoneSequenceCache = new HashMap();
		allocated = false;
	}

	public void register(String name, Registry registry)
			throws PropertyException {
		this.name = name;
		registry.register("loader", PropertyType.COMPONENT);
		registry.register("unitManager", PropertyType.COMPONENT);
		registry.register("useComposites", PropertyType.BOOLEAN);
	}

	public void newProperties(PropertySheet ps) throws PropertyException {
		loader = (Loader) ps.getComponent("loader",
				edu.cmu.sphinx.linguist.acoustic.tiedstate.Loader.class);
		unitManager = (UnitManager) ps.getComponent("unitManager",
				edu.cmu.sphinx.linguist.acoustic.UnitManager.class);
		useComposites = ps.getBoolean("useComposites", true);
		logger = ps.getLogger();
	}

	public void allocate() throws IOException {
		if (!allocated) {
			loadTimer = Timer.getTimer("AM_Load");
			loadTimer.start();
			loader.load();
			loadTimer.stop();
			logInfo();
			allocated = true;
		}
	}

	public void deallocate() {
	}

	public String getName() {
		return name;
	}

	private HMM getCompositeHMM(Unit unit, HMMPosition position) {
		Unit ciUnit = unitManager.getUnit(unit.getName(), unit.isFiller(),
				Context.EMPTY_CONTEXT);
		SenoneSequence compositeSequence = getCompositeSenoneSequence(unit,
				position);
		SenoneHMM contextIndependentHMM = (SenoneHMM) lookupNearestHMM(ciUnit,
				HMMPosition.UNDEFINED, true);
		float tmat[][] = contextIndependentHMM.getTransitionMatrix();
		return new SenoneHMM(unit, compositeSequence, tmat, position);
	}

	public HMM lookupNearestHMM(Unit unit, HMMPosition position,
			boolean exactMatch) {
		if (exactMatch)
			return lookupHMM(unit, position);
		HMMManager mgr = loader.getHMMManager();
		HMM hmm = mgr.get(position, unit);
		if (hmm != null)
			return hmm;
		if (useComposites && hmm == null && isComposite(unit)) {
			hmm = getCompositeHMM(unit, position);
			if (hmm != null)
				mgr.put(hmm);
		}
		if (hmm == null)
			hmm = getHMMAtAnyPosition(unit);
		if (hmm == null)
			hmm = getHMMInSilenceContext(unit, position);
		if (hmm == null) {
			Unit ciUnit = lookupUnit(unit.getName());
			if (!$assertionsDisabled && !unit.isContextDependent())
				throw new AssertionError();
			if (ciUnit == null)
				logger.severe("Can't find HMM for " + unit.getName());
			if (!$assertionsDisabled && ciUnit == null)
				throw new AssertionError();
			if (!$assertionsDisabled && ciUnit.isContextDependent())
				throw new AssertionError();
			hmm = mgr.get(HMMPosition.UNDEFINED, ciUnit);
		}
		if (!$assertionsDisabled && hmm == null)
			throw new AssertionError();
		else
			return hmm;
	}

	private boolean isComposite(Unit unit) {
		if (unit.isFiller())
			return false;
		Context context = unit.getContext();
		if (context instanceof LeftRightContext) {
			LeftRightContext lrContext = (LeftRightContext) context;
			if (lrContext.getRightContext() == null)
				return true;
			if (lrContext.getLeftContext() == null)
				return true;
		}
		return false;
	}

	private Unit lookupUnit(String name) {
		return (Unit) loader.getContextIndependentUnits().get(name);
	}

	public Iterator getHMMIterator() {
		return loader.getHMMManager().getIterator();
	}

	public Iterator getContextIndependentUnitIterator() {
		return loader.getContextIndependentUnits().values().iterator();
	}

	public SenoneSequence getCompositeSenoneSequence(Unit unit,
			HMMPosition position) {
		Context context = unit.getContext();
		SenoneSequence compositeSenoneSequence = null;
		compositeSenoneSequence = (SenoneSequence) compositeSenoneSequenceCache
				.get(unit.toString());
		if (logger.isLoggable(Level.FINE))
			logger.fine("getCompositeSenoneSequence: " + unit.toString()
					+ (compositeSenoneSequence == null ? "" : "Cached"));
		if (compositeSenoneSequence != null)
			return compositeSenoneSequence;
		List senoneSequenceList = new ArrayList();
		Iterator i = getHMMIterator();
		do {
			if (!i.hasNext())
				break;
			SenoneHMM hmm = (SenoneHMM) i.next();
			if (hmm.getPosition() == position) {
				Unit hmmUnit = hmm.getUnit();
				if (hmmUnit.isPartialMatch(unit.getName(), context)) {
					if (logger.isLoggable(Level.FINE))
						logger.fine("collected: " + hmm.getUnit().toString());
					senoneSequenceList.add(hmm.getSenoneSequence());
				}
			}
		} while (true);
		if (senoneSequenceList.size() == 0) {
			Unit ciUnit = unitManager.getUnit(unit.getName(), unit.isFiller());
			SenoneHMM baseHMM = lookupHMM(ciUnit, HMMPosition.UNDEFINED);
			senoneSequenceList.add(baseHMM.getSenoneSequence());
		}
		int longestSequence = 0;
		for (int i = 0; i < senoneSequenceList.size(); i++) {
			SenoneSequence ss = (SenoneSequence) senoneSequenceList.get(i);
			if (ss.getSenones().length > longestSequence)
				longestSequence = ss.getSenones().length;
		}

		List compositeSenones = new ArrayList();
		float logWeight = 0.0F;
		for (int i = 0; i < longestSequence; i++) {
			Set compositeSenoneSet = new HashSet();
			for (int j = 0; j < senoneSequenceList.size(); j++) {
				SenoneSequence senoneSequence = (SenoneSequence) senoneSequenceList
						.get(j);
				if (i < senoneSequence.getSenones().length) {
					edu.cmu.sphinx.linguist.acoustic.tiedstate.Senone senone = senoneSequence
							.getSenones()[i];
					compositeSenoneSet.add(senone);
				}
			}

			compositeSenones.add(CompositeSenone.create(compositeSenoneSet,
					logWeight));
		}

		compositeSenoneSequence = SenoneSequence.create(compositeSenones);
		compositeSenoneSequenceCache.put(unit.toString(),
				compositeSenoneSequence);
		if (logger.isLoggable(Level.FINE)) {
			logger.fine(unit.toString() + " consists of "
					+ compositeSenones.size() + " composite senones");
			if (logger.isLoggable(Level.FINEST))
				compositeSenoneSequence.dump("am");
		}
		return compositeSenoneSequence;
	}

	public int getLeftContextSize() {
		return loader.getLeftContextSize();
	}

	public int getRightContextSize() {
		return loader.getRightContextSize();
	}

	private SenoneHMM lookupHMM(Unit unit, HMMPosition position) {
		return (SenoneHMM) loader.getHMMManager().get(position, unit);
	}

	private String makeTag(Unit base, Context context) {
		StringBuffer sb = new StringBuffer();
		sb.append("(");
		sb.append(base.getName());
		sb.append("-");
		sb.append(context.toString());
		sb.append(")");
		return sb.toString();
	}

	protected void logInfo() {
		if (loader != null)
			loader.logInfo();
		logger.info("CompositeSenoneSequences: "
				+ compositeSenoneSequenceCache.size());
	}

	private SenoneHMM getHMMAtAnyPosition(Unit unit) {
		SenoneHMM hmm = null;
		HMMManager mgr = loader.getHMMManager();
		HMMPosition pos;
		for (Iterator i = HMMPosition.iterator(); hmm == null && i.hasNext(); hmm = (SenoneHMM) mgr
				.get(pos, unit))
			pos = (HMMPosition) i.next();

		return hmm;
	}

	private SenoneHMM getHMMInSilenceContext(Unit unit, HMMPosition position) {
		SenoneHMM hmm = null;
		HMMManager mgr = loader.getHMMManager();
		Context context = unit.getContext();
		if (context instanceof LeftRightContext) {
			LeftRightContext lrContext = (LeftRightContext) context;
			Unit lc[] = lrContext.getLeftContext();
			Unit rc[] = lrContext.getRightContext();
			Unit nlc[];
			if (hasNonSilenceFiller(lc))
				nlc = replaceNonSilenceFillerWithSilence(lc);
			else
				nlc = lc;
			Unit nrc[];
			if (hasNonSilenceFiller(rc))
				nrc = replaceNonSilenceFillerWithSilence(rc);
			else
				nrc = rc;
			if (nlc != lc || nrc != rc) {
				Context newContext = LeftRightContext.get(nlc, nrc);
				Unit newUnit = unitManager.getUnit(unit.getName(), unit
						.isFiller(), newContext);
				hmm = (SenoneHMM) mgr.get(position, newUnit);
				if (hmm == null)
					hmm = getHMMAtAnyPosition(newUnit);
			}
		}
		return hmm;
	}

	private void checkNull(String msg, Unit c[]) {
		for (int i = 0; i < c.length; i++)
			if (c[i] == null)
				System.out.println("null at index " + i + " of " + msg);

	}

	private boolean hasNonSilenceFiller(Unit units[]) {
		if (units == null)
			return false;
		for (int i = 0; i < units.length; i++)
			if (units[i].isFiller() && !units[i].equals(UnitManager.SILENCE))
				return true;

		return false;
	}

	private Unit[] replaceNonSilenceFillerWithSilence(Unit context[]) {
		Unit replacementContext[] = new Unit[context.length];
		for (int i = 0; i < context.length; i++)
			if (context[i].isFiller()
					&& !context[i].equals(UnitManager.SILENCE))
				replacementContext[i] = UnitManager.SILENCE;
			else
				replacementContext[i] = context[i];

		return replacementContext;
	}

	public Properties getProperties() {
		if (properties == null) {
			properties = new Properties();
			try {
				properties.load(getClass().getResource("model.props")
						.openStream());
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		return properties;
	}

	public static final String PROP_LOADER = "loader";

	public static final String PROP_UNIT_MANAGER = "unitManager";

	public static final String PROP_USE_COMPOSITES = "useComposites";

	public static final boolean PROP_USE_COMPOSITES_DEFAULT = true;

	protected static final String TIMER_LOAD = "AM_Load";

	protected String name;

	private Logger logger;

	protected Loader loader;

	protected UnitManager unitManager;

	private boolean useComposites;

	private Properties properties;

	protected transient Timer loadTimer;

	private transient Map compositeSenoneSequenceCache;

	private boolean allocated;

	static final boolean $assertionsDisabled; /* synthetic field */

	static {
		$assertionsDisabled = !(edu.cmu.sphinx.model.acoustic.TIDIGITS_8gau_13dCep_16k_40mel_130Hz_6800Hz.Model.class)
				.desiredAssertionStatus();
	}
}
