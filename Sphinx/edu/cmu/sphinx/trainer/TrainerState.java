/*
 * 
 * Copyright 1999-2004 Carnegie Mellon University.  
 * Portions Copyright 2004 Sun Microsystems, Inc.  
 * Portions Copyright 2004 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */
package edu.cmu.sphinx.trainer;
/**
 * Defines the possible states of the trainer.
 */
public class TrainerState {
    private String name;
    /**
     * The trainer is in the deallocated state. No resources are in use by
     * this trainer.
     */
    public final static TrainerState DEALLOCATED = new TrainerState(
            "Deallocated");
    /**
     * The trainer is allocating resources
     */
    public final static TrainerState ALLOCATING = new TrainerState(
            "Allocating");
    /**
     * The trainer has been allocated
     */
    public final static TrainerState ALLOCATED = new TrainerState(
            "Allocated");
    
    /**
     * The trainer is ready to recognize
     */
    public final static TrainerState READY = new TrainerState(
            "Ready");
    /**
     * The trainer is recognizing speech
     */
    public final static TrainerState Training = new TrainerState(
            "Training");
    /**
     * The trainer is deallocating resources
     */
    public final static TrainerState DEALLOCATING = new TrainerState(
            "Deallocating");
    /**
     * The trainer is in an error state
     */
    public final static TrainerState ERROR = new TrainerState("Error");
    private TrainerState(String name) {
        this.name = name;
    }
    public String toString() {
        return name;
    }
}
