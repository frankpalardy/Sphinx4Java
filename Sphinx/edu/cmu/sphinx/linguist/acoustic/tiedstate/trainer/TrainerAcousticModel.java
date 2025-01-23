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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.cmu.sphinx.util.props.Configurable;
import edu.cmu.sphinx.linguist.acoustic.AcousticModel;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.CompositeSenone;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.HMMManager;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.SenoneSequence;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.SenoneHMM;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Senone;
import edu.cmu.sphinx.linguist.acoustic.HMM;
import edu.cmu.sphinx.linguist.acoustic.HMMPosition;
import edu.cmu.sphinx.linguist.acoustic.Unit;
import edu.cmu.sphinx.linguist.acoustic.Context;
import edu.cmu.sphinx.linguist.acoustic.LeftRightContext;
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
public class TrainerAcousticModel implements AcousticModel, Configurable {

    /**
     * The property that defines the unit manager 
     */    
    public final static String PROP_UNIT_MANAGER  = "unitManager";
    /**
     * Controls whether we generate composites or CI units when no
     * context is given during a lookup.
     */
    public final static String PROP_USE_COMPOSITES =  "useComposites";

    /**
     * The property that defines the component used to load the acoustic model
     */    
    public final static String PROP_MODELLOADER = "modelLoader";

	/**
	 * The property that defines the component used to save the acoustic model
	 */
	public final static String PROP_SAVER = "saveModel";

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

	
    transient private Map compositeSenoneSequenceCache = new HashMap();
	/**
	 * Flag indicating all models should be operated on.
	 */
	public final static int ALL_MODELS = -1;

	/**
	 * The logger for this class
	 */
	private static Logger logger = Logger.getLogger(PROP_PREFIX
			+ "TrainerAcousticModel");
    transient protected Timer loadTimer;

    /**
     * Model load timer
     */
    protected final static String TIMER_LOAD = "AM_Load";

	/**
	 * The pool manager
	 */
	private HMMPoolManager hmmPoolManager;

	private String format;
    protected ModelInitializerLoader modelLoader;
	private String phoneList = "";

	private String context;
	
	private boolean allocated = false;
    private boolean useComposites = false;
    
	public UnitManager unitManager;
	
    /**
     * The default value of PROP_USE_COMPOSITES.
     */
    public final static boolean PROP_USE_COMPOSITES_DEFAULT = true;
	
	String name;
	
	Properties properties;

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#register(java.lang.String,
	 *      edu.cmu.sphinx.util.props.Registry)
	 */
	public void register(String name, Registry registry)
			throws PropertyException {
		//super.register(name, registry);
		this.name = name;
        registry.register(PROP_MODELLOADER, PropertyType.COMPONENT);
		registry.register(PROP_SAVER, PropertyType.COMPONENT);
		registry.register(PROP_HMMPOOLMANAGER, PropertyType.COMPONENT);
		registry.register(PROP_FORMAT_SAVE, PropertyType.STRING);
		registry.register(PROP_PHONE_LIST, PropertyType.STRING);
		registry.register(PROP_UNIT_MANAGER, PropertyType.COMPONENT);
	    registry.register(PROP_USE_COMPOSITES, PropertyType.BOOLEAN);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.cmu.sphinx.util.props.Configurable#newProperties(edu.cmu.sphinx.util.props.PropertySheet)
	 */
	public void newProperties(PropertySheet ps) throws PropertyException {
	   //super.newProperties(ps);
		modelLoader = (ModelInitializerLoader) ps.getComponent(PROP_MODELLOADER, ModelInitializerLoader.class);
		saverModel = (Sphinx3Saver) ps.getComponent(PROP_SAVER,
				Sphinx3Saver.class);
		hmmPoolManager = (HMMPoolManager) ps.getComponent(PROP_HMMPOOLMANAGER,
				HMMPoolManager.class);
		format = ps.getString(PROP_FORMAT_SAVE, PROP_FORMAT_SAVE_DEFAULT);
		phoneList = ps.getString(PROP_PHONE_LIST, PROP_PHONE_LIST_DEFAULT);
        unitManager = (UnitManager) ps.getComponent(PROP_UNIT_MANAGER,
                UnitManager.class);
        useComposites = 
            ps.getBoolean(PROP_USE_COMPOSITES, PROP_USE_COMPOSITES_DEFAULT);
	}

	/**
	 * Initializes the acoustic model
	 * 
	 * @throws IOException
	 *             if the model could not be created
	 */
	public void initialize(String name, String context) throws IOException {

		modelLoader.initialize();
		try {
			modelLoader.loadPhoneList(false, phoneList);
		} catch (Exception e) {
			e.printStackTrace();
		}
		hmmPoolManager.init(modelLoader);
		this.context = context;
	}
	/**
	 * initialize this acoustic model with the given name and context.
	 * 
	 * @throws IOException
	 *             if the model could not be loaded
	 * 
	 */
	public void allocate() throws IOException {
		if (!allocated) {
			this.loadTimer = Timer.getTimer(TIMER_LOAD);
			loadTimer.start();
			modelLoader.load();
			hmmPoolManager.init(modelLoader);
			loadTimer.stop();
			//logInfo();
			allocated = true;
		}
	}
	
    
    /* (non-Javadoc)
     * @see edu.cmu.sphinx.linguist.acoustic.AcousticModel#deallocate()
     */
    public void deallocate() {
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
	   /**
     * Returns the name of this AcousticModel, or null if it has no name.
     *
     * @return the name of this AcousticModel, or null if it has no name
     */
    public String getName() {
	return name;
    }
    
	public void save(String name) throws IOException, FileNotFoundException {
		Saver saver;
		if (format.equals("sphinx3.ascii")) {
			logger.info("Sphinx-3 ASCII format");
			saverModel.init(name, false, modelLoader);
		} else if (format.equals("sphinx3.binary")) {
			logger.info("Sphinx-3 binary format");
			saverModel.init(name, true, modelLoader);
		} else if (format.equals("sphinx4.ascii")) {
			logger.info("Sphinx-4 ASCII format");
			saver = new Sphinx4Saver(name, false, modelLoader);
		} else if (format.equals("sphinx4.binary")) {
			logger.info("Sphinx-4 binary format");
			saver = new Sphinx4Saver(name, true, modelLoader);
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
	 	loadTimer.start();
	 	modelLoader.load();
	 	loadTimer.stop();
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
	public float getLogLikelihood() {
		return hmmPoolManager.getLogLikelihood();
	}

	/**
	 * Normalize the buffers and update the models.
	 * 
	 * @return the log likelihood for the whole training set
	 */
	public void normalize() {
		hmmPoolManager.normalize();
		hmmPoolManager.update();
	}
	public Map getTriPhones(){
		return modelLoader.getTriPhones();
	}
	// this takes in all the triphones that are used
	// and makes context dependent units with them
	// @param phones 	used triphones
	public void addCDPools(Map phones, int size, boolean tmatSkip) throws IOException
	{
		LinkedHashMap triPhoneUnits = new LinkedHashMap();
		Iterator pl = phones.keySet().iterator();
		 Context context;
		while(pl.hasNext()) {
			String phone = (String)pl.next();
			 Unit [] left = new Unit[1];
			 Unit [] right = new Unit[1];
			int space1 = phone.indexOf(" ");
			int space2 = phone.lastIndexOf(" ");
			
			left [0]= unitManager.getUnit(phone.substring(0,space1), false);
			SenoneHMM hmmLeft = (SenoneHMM) lookupNearestHMM(left[0], HMMPosition.UNDEFINED, false);
			Senone[] ssLeft = hmmLeft.getSenoneSequence().getSenones();
			//left [0]= new Unit(phone.substring(0,space1), false, Long.valueOf(ssLeft[0].getID()).intValue());
			
			right [0]= unitManager.getUnit(phone.substring(space2+1,phone.length()), false);
			SenoneHMM hmmRight = (SenoneHMM) lookupNearestHMM(right[0], HMMPosition.UNDEFINED, false);
			Senone[] ssRight = hmmRight.getSenoneSequence().getSenones();
			//right [0]= new Unit(phone.substring(space2+1,phone.length()), false, Long.valueOf(ssRight[0].getID()).intValue());			
			
			context = LeftRightContext.get(left,right);	
			Unit base = unitManager.getUnit(phone.substring(space1 + 1, space2),false, context);
			SenoneHMM hmm = (SenoneHMM) lookupNearestHMM(base, HMMPosition.UNDEFINED, false);
			triPhoneUnits.put(base, hmm);
		}
		modelLoader.addCDPools(triPhoneUnits, size, tmatSkip);
		//hmmPoolManager.init(loader);
	}
	  /**
     * Gets a composite HMM for the given unit and context
     *
     * @param unit the unit for the hmm
     * @param position the position of the unit within the word
     *
     * @return a composite HMM
     */
    private HMM getCompositeHMM(Unit unit, HMMPosition position) {


        if (true) { // use a true composite
            Unit ciUnit = unitManager.getUnit(unit.getName(),
                    unit.isFiller(), Context.EMPTY_CONTEXT);

            SenoneSequence compositeSequence =
                getCompositeSenoneSequence(unit, position);

            SenoneHMM contextIndependentHMM = (SenoneHMM) 
                lookupNearestHMM(ciUnit,
                    HMMPosition.UNDEFINED, true);
            float[][] tmat = contextIndependentHMM.getTransitionMatrix();
            return new SenoneHMM(unit, compositeSequence, tmat, position);
        } else { // BUG: just a test. use CI units instead of composites
            Unit ciUnit = lookupUnit(unit.getName());

            assert unit.isContextDependent();
            if (ciUnit == null) {
                logger.severe("Can't find HMM for " + unit.getName());
            }
            assert ciUnit != null;
            assert !ciUnit.isContextDependent();

	     HMMManager mgr = modelLoader.getHMMManager();
            HMM hmm = mgr.get(HMMPosition.UNDEFINED, ciUnit);
            return hmm;
       }
    }

    /**
     * Given a unit, returns the HMM that best matches the given unit.
     * If exactMatch is false and an exact match is not found, 
     * then different word positions
     * are used. If any of the contexts are non-silence filler units.
     * a silence filler unit is tried instead.
     *
     * @param unit 		the unit of interest
     * @param position 	the position of the unit of interest
     * @param exactMatch 	if true, only an exact match is
     * 			acceptable.
     *
     * @return 	the HMM that best matches, or null if no match
     * 		could be found.
     */
    public HMM lookupNearestHMM(Unit unit, HMMPosition position,
	     boolean exactMatch) {

	 if (exactMatch) {
	     return lookupHMM(unit, position);
	 } else {
	     HMMManager mgr = modelLoader.getHMMManager();
	     HMM hmm = mgr.get(position, unit);

	     if (hmm != null) {
		 return hmm;
	     }
	     // no match, try a composite

	     if (useComposites && hmm == null) {
		 if (isComposite(unit)) {

		     hmm = getCompositeHMM(unit, position);
		     if (hmm != null) {
			 mgr.put(hmm);
		     }
		 }
	     }
	     // no match, try at other positions
	     if (hmm == null) {
		 hmm = getHMMAtAnyPosition(unit);
	     }
	     // still no match, try different filler 
	     if (hmm == null) {
		 hmm = getHMMInSilenceContext(unit, position);
	     }

	     // still no match, backoff to base phone
	     if (hmm == null) {
		 Unit ciUnit = lookupUnit(unit.getName());

		 assert unit.isContextDependent();
                if (ciUnit == null) {
                    logger.severe("Can't find HMM for " + unit.getName());
                }
		 assert ciUnit != null;
		 assert !ciUnit.isContextDependent();

		 hmm = mgr.get(HMMPosition.UNDEFINED, ciUnit);
	     }

	     assert hmm != null;

	 // System.out.println("PROX match for "  
	 // 	+ unit + " at " + position + ":" + hmm);

	     return hmm;
	}
    }

    /**
     * Determines if a unit is a composite unit
     *
     * @param unit the unit to test
     *
     * @return true if the unit is missing a right context
     */
    private boolean isComposite(Unit unit) {

	 if (unit.isFiller()) {
	     return false;
	 }

	 Context context = unit.getContext();
	 if (context instanceof LeftRightContext) {
	     LeftRightContext lrContext = (LeftRightContext) context;
	     if (lrContext.getRightContext() == null) {
		 return true;
	     }
	     if (lrContext.getLeftContext() == null) {
		 return true;
	     }
	 }
	 return false;
    }

    /**
     * Looks up the context independent unit given
     * the name
     *
     * @param name the name of the unit
     *
     * @return the unit or null if the unit was not found
     */
    private Unit lookupUnit(String name) {
	 return (Unit) modelLoader.getContextIndependentUnits().get(name);
    }

    /**
     * Returns an iterator that can be used to iterate through all
     * the HMMs of the acoustic model
     *
     * @return an iterator that can be used to iterate through all
     * HMMs in the model. The iterator returns objects of type
     * <code>HMM</code>.
     */
    public Iterator getHMMIterator() {
	 return modelLoader.getHMMManager().getIterator();
     }


    /**
     * Returns an iterator that can be used to iterate through all
     * the CI units in the acoustic model
     *
     * @return an iterator that can be used to iterate through all
     * CI units. The iterator returns objects of type
     * <code>Unit</code>
     */
    public Iterator getContextIndependentUnitIterator() {
	 return modelLoader.getContextIndependentUnits().values().iterator();
    }

    /**
     * Get a composite senone sequence given the unit
     * The unit should have a LeftRightContext, where one or two of
     * 'left' or 'right' may be null to indicate that the match
     * should succeed on any context.
     *
     * @param unit the unit
     *
     */
    public SenoneSequence getCompositeSenoneSequence(Unit unit,
            HMMPosition position) {
	 Context context = unit.getContext();
	 SenoneSequence compositeSenoneSequence = null;
	 compositeSenoneSequence = (SenoneSequence)
	     compositeSenoneSequenceCache.get(unit.toString());

	 if (logger.isLoggable(Level.FINE)) {
	    logger.fine("getCompositeSenoneSequence: " + unit.toString() 
		    + ((compositeSenoneSequence != null) ? "Cached" : ""));
	 }
	 if (compositeSenoneSequence != null) {
	     return compositeSenoneSequence;
	 }

	 // Iterate through all HMMs looking for
	 // a) An hmm with a unit that has the proper base
	 // b) matches the non-null context

	 List senoneSequenceList = new ArrayList();

	// collect all senone sequences that match the pattern
	 for (Iterator i = getHMMIterator(); i.hasNext(); ) {
	     SenoneHMM hmm = (SenoneHMM) i.next();
            if (hmm.getPosition() == position) {
                Unit hmmUnit = hmm.getUnit();
                if (hmmUnit.isPartialMatch(unit.getName(), context)) {
                    if (logger.isLoggable(Level.FINE)) {
                       logger.fine("collected: " + hmm.getUnit().toString());
                    }
                    senoneSequenceList.add(hmm.getSenoneSequence());
                }
           }
	 }

	 // couldn't find any matches, so at least include the CI unit
	 if (senoneSequenceList.size() == 0) {
	    Unit ciUnit = unitManager.getUnit(unit.getName(), unit.isFiller());
	     SenoneHMM baseHMM = lookupHMM(ciUnit, HMMPosition.UNDEFINED);
	     senoneSequenceList.add(baseHMM.getSenoneSequence());
	 }

	 // Add this point we have all of the senone sequences that
	 // match the base/context pattern collected into the list.
	 // Next we build a CompositeSenone consisting of all of the
	 // senones in each position of the list.

	 // First find the longest senone sequence

	 int longestSequence = 0;
	 for (int i = 0; i < senoneSequenceList.size(); i++) {
	     SenoneSequence ss = (SenoneSequence) senoneSequenceList.get(i);
	     if (ss.getSenones().length > longestSequence) {
		 longestSequence = ss.getSenones().length;
	     }
	 }

	 // now collect all of the senones at each position into
	 // arrays so we can create CompositeSenones from them
	 // QUESTION: is is possible to have different size senone
	 // sequences. For now lets assume the worst case.

	 List compositeSenones = new ArrayList();
        float logWeight = 0.0f;
	 for (int i = 0; i < longestSequence; i++) {
	     Set compositeSenoneSet = new HashSet();
	     for (int j = 0; j < senoneSequenceList.size(); j++) {
		 SenoneSequence senoneSequence = 
		     (SenoneSequence) senoneSequenceList.get(j);
		 if (i < senoneSequence.getSenones().length) {
		     Senone senone = senoneSequence.getSenones()[i];
		     compositeSenoneSet.add(senone);
		 }
	     }
	     compositeSenones.add(CompositeSenone.create(
                        compositeSenoneSet, logWeight));
	 }

	 compositeSenoneSequence = SenoneSequence.create(compositeSenones);
	 compositeSenoneSequenceCache.put(unit.toString(), 
		 compositeSenoneSequence);

	 if (logger.isLoggable(Level.FINE)) {
	    logger.fine(unit.toString() + " consists of " +
		    compositeSenones.size() + " composite senones");
	    if (logger.isLoggable(Level.FINEST)) {
		 compositeSenoneSequence.dump("am");
	    }
	 }
	 return compositeSenoneSequence;
    }


    /**
     * Returns the size of the left context for context dependent
     * units
     *
     * @return the left context size
     */
    public int getLeftContextSize() {
	 return modelLoader.getLeftContextSize();
    }

    /**
     * Returns the size of the right context for context dependent
     * units
     *
     * @return the left context size
     */
    public int getRightContextSize() {
	 return modelLoader.getRightContextSize();
    }


   /**
    * Given a unit, returns the HMM that exactly matches the given
    * unit.  
    *
    * @param unit 		the unit of interest
    * @param position 	the position of the unit of interest
    *
    * @return 	the HMM that exactly matches, or null if no match
    * 		could be found.
    */
   private SenoneHMM lookupHMM(Unit unit, HMMPosition position) {
       return (SenoneHMM) modelLoader.getHMMManager().get(position, unit);
   }
   
   
   /**
    * Creates a string useful for tagging a composite senone sequence
    *
    * @param base the base unit
    * @param context the context
    *
    * @return the tag associated with the composite senone sequence
    */
   private String makeTag(Unit base, Context context) {
       StringBuffer sb = new StringBuffer();
       sb.append("(");
       sb.append(base.getName());
       sb.append("-");
       sb.append(context.toString());
       sb.append(")");
       return sb.toString();
   }
   
   
  
   /**
    * Dumps information about this model to the logger
    */
   protected void logInfo() {
       if (modelLoader != null) {
           modelLoader.logInfo();
       }
       logger.info("CompositeSenoneSequences: " + 
                   compositeSenoneSequenceCache.size());
   }
   
   
   /**
    * Searches an hmm at any position
    *
    * @param unit the unit to search for
    *
    * @return hmm the hmm or null if it was not found
    */
   private SenoneHMM getHMMAtAnyPosition(Unit unit) {
       SenoneHMM hmm = null;
       HMMManager mgr = modelLoader.getHMMManager();
       for (Iterator i = HMMPosition.iterator(); 
            hmm == null && i.hasNext(); ) {
           HMMPosition pos = (HMMPosition) i.next();
           hmm = (SenoneHMM) mgr.get(pos, unit);
       }
       return hmm;
   }
   
   /**
    * Given a unit, search for the HMM associated with this unit by
    * replacing all non-silence filler contexts with the silence
    * filler context
    *
    * @param unit the unit of interest
    *
    * @return the associated hmm or null
    */
   private SenoneHMM getHMMInSilenceContext(Unit unit, HMMPosition position) {
       SenoneHMM hmm = null;
       HMMManager mgr = modelLoader.getHMMManager();
       Context context = unit.getContext();
       
       if (context instanceof LeftRightContext) {
           LeftRightContext lrContext = (LeftRightContext) context;
           
           Unit[] lc  = lrContext.getLeftContext();
           Unit[] rc  = lrContext.getRightContext();
           
           Unit[] nlc;
           Unit[] nrc;
           
           if (hasNonSilenceFiller(lc)) {
               nlc = replaceNonSilenceFillerWithSilence(lc);
           } else {
               nlc = lc;
           }
           
           if (hasNonSilenceFiller(rc)) {
               nrc = replaceNonSilenceFillerWithSilence(rc);
           } else {
               nrc = rc;
           }
           
           if (nlc != lc || nrc != rc) {
               Context newContext =  LeftRightContext.get(nlc, nrc);
               Unit newUnit = unitManager.getUnit(unit.getName(),
                       unit.isFiller(), newContext);
               hmm = (SenoneHMM) mgr.get(position, newUnit);
               if (hmm == null) {
                   hmm = getHMMAtAnyPosition(newUnit);
               }
           }
       }
       return hmm;
   }
   
   
   /**
    * Some debugging code that looks for illformed contexts
    * 
    * @param msg the message associated with the check
    * @param c the context to check
    */
   private void checkNull(String msg, Unit[] c) {
       for (int i = 0; i < c.length; i++) {
           if (c[i] == null) {
               System.out.println("null at index " + i + " of " + msg);
           }
       }
   }
   
   
   /**
    * Returns true if the array of units contains
    * a non-silence filler
    *
    * @param units the units to check
    *
    * @return true if the array contains a filler that is not the
    * silence filler
    */
   private boolean hasNonSilenceFiller(Unit[] units) {
	if (units == null) {
	    return false;
	}

       for (int i = 0; i < units.length; i++) {
           if (units[i].isFiller() &&
               !units[i].equals(UnitManager.SILENCE)) {
               return true;
           }
       }
       return false;
   }
   
   /**
    * Returns a unit array with all non-silence filler units replaced
    * with the silence filler
    * a non-silence filler
    *
    * @param context the context to check
    *
    * @return true if the array contains a filler that is not the
    * silence filler
    */
   private Unit[] replaceNonSilenceFillerWithSilence(Unit[] context) {
       Unit[] replacementContext = new Unit[context.length];
       for (int i = 0; i < context.length; i++) {
           if (context[i].isFiller() &&
                   !context[i].equals(UnitManager.SILENCE)) {
               replacementContext[i] = UnitManager.SILENCE;
           } else {
               replacementContext[i] = context[i];
           }
       }
       return replacementContext;
   }

   public Properties getProperties() {
       if (properties == null) {
           properties = new Properties();
           try {
               properties.load
                   (getClass().getResource("model.props").openStream());
           } catch (IOException ioe) {
               ioe.printStackTrace();
           }
       }
       return properties;
   }

}

