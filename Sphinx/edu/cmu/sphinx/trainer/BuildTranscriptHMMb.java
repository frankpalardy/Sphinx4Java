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

import java.util.Iterator;
import java.util.Map;

import edu.cmu.sphinx.linguist.acoustic.AcousticModel;
import edu.cmu.sphinx.linguist.acoustic.Context;
import edu.cmu.sphinx.linguist.acoustic.HMM;
import edu.cmu.sphinx.linguist.acoustic.HMMPosition;
import edu.cmu.sphinx.linguist.acoustic.UnitManager;
import edu.cmu.sphinx.linguist.acoustic.LeftRightContext;
import edu.cmu.sphinx.linguist.acoustic.Unit;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.Senone;
import edu.cmu.sphinx.linguist.acoustic.tiedstate.SenoneHMM;
import edu.cmu.sphinx.linguist.dictionary.Dictionary;
import edu.cmu.sphinx.linguist.dictionary.Pronunciation;
import edu.cmu.sphinx.util.LogMath;

/**
 * This class builds an HMM from a transcript, at increasing levels of details.
 */
public class BuildTranscriptHMM {
	private Graph wordGraph;

	private Graph phonemeGraph;

	private Graph contextDependentPhoneGraph;

	private Graph hmmGraph;

	private Map dictionaryMap;

	private String context;

	private TrainerDictionary dictionary;

	private AcousticModel acousticModel;

	UnitManager unitManager;

	/**
	 * Constructor for class BuildTranscriptHMM. When called, this method
	 * creates graphs for the transcript at several levels of detail,
	 * subsequently mapping from a word graph to a phone graph, to a state
	 * graph.
	 * 
	 * @param context
	 *            this object's context
	 * @param transcript
	 *            the transcript to be converted to HMM
	 * @param acousticModel
	 *            the acoustic model to be used
	 */
	public BuildTranscriptHMM(Transcript transcript,
			AcousticModel acousticModel, UnitManager unitManager) {

		this.unitManager = unitManager;
		this.acousticModel = acousticModel;
		wordGraph = buildWordGraph(transcript);
		if (wordGraph.validate() == false) {
			System.out.println("Word graph not validated");
		}
		phonemeGraph = buildPhonemeGraph(wordGraph);
		if (phonemeGraph.validate() == false) {
			System.out.println("Phone graph not validated");
		}
		hmmGraph = buildCDHMMGraph(phonemeGraph);
		if (hmmGraph.validate() == false) {
			System.out.println("Context graph not validated");
			//hmmGraph.printGraph();
		}
	}

	/**
	 * Returns the graph.
	 * 
	 * @return the graph.
	 */
	public Graph getGraph() {
		return hmmGraph;
	}

	/*
	 * Build a word graph from this transcript
	 */
	private Graph buildWordGraph(Transcript transcript) {
		Graph graph;
		Dictionary transcriptDict = transcript.getDictionary();
		// Make sure the dictionary is a TrainerDictionary before we cast
		assert transcriptDict.getClass().getName()
				.endsWith("TrainerDictionary");
		dictionary = (TrainerDictionary) transcriptDict;

		transcript.startWordIterator();
		int numWords = transcript.numberOfWords();
		/* Shouldn't node and edge be part of the graph class? */

		/* The wordgraph must always begin with the <s> */
		graph = new Graph();
		Node initialNode = new Node(NodeType.STATE, "SIL");
		graph.addNode(initialNode);
		graph.setInitialNode(initialNode);

		if (transcript.isExact()) {
			Node prevNode = initialNode;
			for (transcript.startWordIterator(); transcript.hasMoreWords();) {
				/* create a new node for the next word */
				Node wordNode = new Node(NodeType.WORD, transcript.nextWord());
				/* Link the new node into the graph */
				graph.linkNodes(prevNode, wordNode);

				prevNode = wordNode;
			}
			/* All words are done. Just add the </s> */
			Node wordNode = new Node(NodeType.STATE, "SIL");
			graph.linkNodes(prevNode, wordNode);
			graph.setFinalNode(wordNode);
		} else {
			/* Begin the utterance with a loopy silence */
			Node silLoopBack = new Node(NodeType.STATE, "SIL");
			graph.linkNodes(initialNode, silLoopBack);

			Node prevNode = initialNode;

			// Create links with words from the transcript
			for (transcript.startWordIterator(); transcript.hasMoreWords();) {
				String word = transcript.nextWord();
				Pronunciation[] pronunciations = dictionary.getWord(word)
						.getPronunciations(null);
				int numberOfPronunciations = pronunciations.length;

				Node[] pronNode = new Node[numberOfPronunciations];

				// Create node at the beginning of the word
				Node dummyWordBeginNode = new Node(NodeType.DUMMY);
				// Allow the silence to be skipped
				// TODO: don't link this, for debugging.
				// graph.linkNodes(prevNode, dummyWordBeginNode);
				// Link the latest silence to the dummy too
				graph.linkNodes(silLoopBack, dummyWordBeginNode);
				// Add word ending dummy node
				Node dummyWordEndNode = new Node(NodeType.DUMMY);
				// Update prevNode
				prevNode = dummyWordEndNode;
				for (int i = 0; i < numberOfPronunciations; i++) {
					String wordAlternate = pronunciations[i].getWord()
							.getSpelling();
					if (i > 0) {
						wordAlternate += "(" + i + ")";
					}
					pronNode[i] = new Node(NodeType.WORD, wordAlternate);
					graph.linkNodes(dummyWordBeginNode, pronNode[i]);
					graph.linkNodes(pronNode[i], dummyWordEndNode);
				}

				/* Add silence */
				silLoopBack = new Node(NodeType.STATE, "SIL");
				graph.linkNodes(dummyWordEndNode, silLoopBack);

			}
			Node wordNode = new Node(NodeType.STATE, "SIL");
			// Link previous node, a dummy word end node
			// TODO: disable this link for now.
			// graph.linkNodes(prevNode, wordNode);
			// Link also the previous silence node
			graph.linkNodes(silLoopBack, wordNode);
			graph.setFinalNode(wordNode);
		}
		return graph;
	}

	/**
	 * Convert word graph to phoneme graph
	 */
	private Graph buildPhonemeGraph(Graph wordGraph) {
		Graph phonemeGraph = new Graph();
		phonemeGraph.copyGraph(wordGraph);

		Object[] nodeArray = phonemeGraph.nodeToArray();
		for (int i = 0; i < nodeArray.length; i++) {
			int index = phonemeGraph.indexOf((Node) nodeArray[i]);
			Node node = phonemeGraph.getNode(index);
			if (node.getType().equals(NodeType.WORD)) {
				String word = node.getID();
				// "false" means graph won't have additional dummy
				// nodes surrounding the word
				Graph pronunciationGraph = dictionary.getWordGraph(word, false);
				phonemeGraph.insertGraph(pronunciationGraph, node);
			}
		}
		return phonemeGraph;
	}

	/**
	 * Convert phoneme graph to a context sensitive phoneme graph. This graph
	 * expands paths out to have separate phoneme nodes for phonemes in
	 * different contexts.
	 * 
	 * @param phonemeGraph
	 *            the phoneme graph
	 * 
	 * @return a context dependendent phoneme graph
	 */
	public Graph buildContextDependentPhonemeGraph(Graph phonemeGraph) {
		// TODO: Dummy stub for now - return a copy of the original graph
		Graph cdGraph = new Graph();
		cdGraph.copyGraph(phonemeGraph);
		return cdGraph;
	}

	private Graph buildCIHMMGraph(Graph graph) {
		hmmGraph = new Graph();
		hmmGraph.copyGraph(graph);
		Graph modelGraph = new Graph();
		Unit unit;
		Node node = graph.getInitialNode();
		// this is how utterance is getting in
		unit = unitManager.getUnit("SIL", false);
		HMM hmm = acousticModel.lookupNearestHMM(unit, HMMPosition.UNDEFINED,
				false);
		node.setObject(hmm.getState(1));
		hmmGraph.addNode(node);
		hmmGraph.setInitialNode(node);

		// loop to add all the state nodes
		for (graph.startNodeIterator(); graph.hasMoreNodes();) {

			node = graph.nextNode();
			if (node.getType().equals(NodeType.PHONE)) {
				unit = unitManager.getUnit(node.getID(), false);
				// unit = new Unit(node.getID(), false, i);
				hmm = acousticModel.lookupNearestHMM(unit,
						HMMPosition.UNDEFINED, false);
				modelGraph = buildModelGraph((SenoneHMM) hmm);
				// node.setObject(hmm.getState(1));

			} else if (node.getType().equals(NodeType.SILENCE_WITH_LOOPBACK)) {
				unit = unitManager.getUnit(node.getID(), false);
				hmm = acousticModel.lookupNearestHMM(unit,
						HMMPosition.UNDEFINED, false);
				modelGraph = buildModelGraph((SenoneHMM) hmm);
				// node.setObject(hmm.getState(1));
			} else {
				continue;
			}
			// hmmGraph.addNode(node);
			hmmGraph.insertGraph(modelGraph, node);
		}
		// loop to add all the state edges
		for (graph.startEdgeIterator(); graph.hasMoreEdges();) {
			Edge edge = graph.nextEdge();
			// if (edge.getSource().getType().equals(NodeType.STATE)
			// && edge.getDestination().getType().equals(NodeType.STATE))
			hmmGraph.addEdge(edge);
		}
		// now the final nodes
		unit = unitManager.getUnit("SIL", false);
		// unit = new Unit(node.getID(), false, i);
		hmm = acousticModel
				.lookupNearestHMM(unit, HMMPosition.UNDEFINED, false);
		node.setObject(hmm.getState(1));
		hmmGraph.addNode(node);
		hmmGraph.setFinalNode(node);

		return hmmGraph;
	}

	/**
	 * Convert the phoneme graph to an HMM.
	 * 
	 * @param cdGraph
	 *            a context dependent phoneme graph
	 * 
	 * @return an HMM graph for a context dependent phoneme graph
	 */
	public Graph buildCDHMMGraph(Graph cdGraph) {

		boolean notTraversed = false;
		Graph hmmGraph = new Graph();
		 hmmGraph.copyGraph(cdGraph);
		Node node = new Node(NodeType.STATE);
		Node leftNode = new Node(NodeType.STATE);
		Node rightNode = new Node(NodeType.STATE);
		Node previousNode = new Node(NodeType.DUMMY);
		int p = 0;
		Object[] nodeArray = cdGraph.nodeToArray();
		for (int i = 0; i < nodeArray.length; i++) {
 
			if (i > 0) {
				//System.out.println(((Node) nodeArray[i - 1]).getID());
				// this isn't working because it's not exactly the same node
				// somehow hmmGraph is not the same as cdGraph
				int indexLeft = cdGraph.indexOf((Node) nodeArray[i - 1]);
				leftNode.copyNode(cdGraph.getNode(indexLeft));
			} else {
				leftNode = new Node(NodeType.STATE, "SIL");
			}
			if (i < nodeArray.length - 1) {
				int indexRight = cdGraph.indexOf((Node) nodeArray[i + 1]);
				rightNode.copyNode(cdGraph.getNode(indexRight));
			} else {
				rightNode = new Node(NodeType.STATE, "SIL");
			}
			int index = cdGraph.indexOf((Node) nodeArray[i]);
			node.copyNode(cdGraph.getNode(index));
			Unit unit;
			Unit[] leftUnit = new Unit[1];
			Unit[] rightUnit = new Unit[1];
			Context context;
			// if (!unit.getContext().equals(Context.EMPTY_CONTEXT)){
			if (node.getType().equals(NodeType.STATE)) {
				if (leftNode.getType().equals(NodeType.STATE)) {
					leftUnit[0] = unitManager.getUnit(leftNode.getID(), false);
					SenoneHMM hmmLeft = (SenoneHMM) acousticModel
							.lookupNearestHMM(leftUnit[0],
									HMMPosition.UNDEFINED, false);
					Senone[] ssLeft = hmmLeft.getSenoneSequence().getSenones();
					leftUnit[0] = unitManager.getUnit(leftNode.getID(), false);
					// new Unit(leftNode.getID(), false, Long.valueOf(
					// ssLeft[0].getID()).intValue());
				} else {
					leftUnit[0] = unitManager.getUnit("SIL", false);
				}
				if (rightNode.getType().equals(NodeType.STATE)) {
					rightUnit[0] = unitManager
							.getUnit(rightNode.getID(), false);
					SenoneHMM hmmRight = (SenoneHMM) acousticModel
							.lookupNearestHMM(rightUnit[0],
									HMMPosition.UNDEFINED, false);
					Senone[] ssRight = hmmRight.getSenoneSequence()
							.getSenones();
					rightUnit[0] = unitManager
							.getUnit(rightNode.getID(), false);
					// new Unit(rightNode.getID(), false, Long.valueOf(
					// ssRight[0].getID()).intValue());
				} else {
					rightUnit[0] = unitManager.getUnit("SIL", false);
				}
				context = LeftRightContext.get(leftUnit, rightUnit);
				unit = unitManager.getUnit(node.getID(), false, context);

			} else {
				unit = unitManager.getUnit("SIL", true);
			}
			//Graph modelGraph = new Graph();
			HMM hmm = acousticModel.lookupNearestHMM(unit,
					HMMPosition.UNDEFINED, false);
			node.setObject(hmm.getState(1));
			hmmGraph.addNode(node);
			if (p > 0) {
				hmmGraph.linkNodes(previousNode, node);
				 
			} else {
				hmmGraph.setInitialNode(node);
			}
			hmmGraph.linkNodes(node, node);
			previousNode.copyNode(node);
			p++;
			hmmGraph.setFinalNode(node);
		}
		for (cdGraph.startEdgeIterator(); cdGraph.hasMoreEdges();) {
			Edge edge = cdGraph.nextEdge();
			// if (edge.getSource().getType().equals(NodeType.STATE)
			// && edge.getDestination().getType().equals(NodeType.STATE))
			hmmGraph.addEdge(edge);
		}
		return hmmGraph;
	}

	/**
	 * Convert the phoneme graph to an HMM.
	 * 
	 * @param cdGraph
	 *            a context dependent phoneme graph
	 * 
	 * @return an HMM graph for a context dependent phoneme graph
	 */
	public Graph buildHMMGraph(Graph cdGraph) {
		boolean notTraversed = false;
		Graph hmmGraph = new Graph();

		hmmGraph.copyGraph(cdGraph);

		Object[] nodeArray = hmmGraph.nodeToArray();
		for (int i = 0; i < nodeArray.length; i++) {
			int index = hmmGraph.indexOf((Node) nodeArray[i]);
			Node node = hmmGraph.getNode(index);
			Unit unit;
			if (node.getType().equals(NodeType.PHONE)) {
				// unit = acousticModel.lookupUnit(node.getID());
				unit = unitManager.getUnit(node.getID(), false);
			} else if (node.getType().equals(NodeType.SILENCE_WITH_LOOPBACK)) {
				unit = unitManager.getUnit(node.getID(), false);
			} else {
				// if it's not a phone, and it's not silence, it's a
				// dummy node, and we don't care.
				continue;
			}
			HMM hmm = acousticModel.lookupNearestHMM(unit,
					HMMPosition.UNDEFINED, false);
			Graph modelGraph = buildModelGraph((SenoneHMM) hmm);
			modelGraph.validate();
			hmmGraph.insertGraph(modelGraph, node);
		}
		return hmmGraph;
	}

	/**
	 * Build a graph given an HMM. The graph will not be surrounded by dummy
	 * nodes. The number of nodes in the graph is the number of emitting states
	 * in the hmm plus one, to account for a final, non-emitting state.
	 * 
	 * @param hmm
	 *            the HMM
	 * 
	 * @return the graph
	 */
	private Graph buildModelGraph(SenoneHMM hmm) {
		Graph graph = new Graph();
		Node prevNode;
		Node stateNode = null;
		float[][] tmat = hmm.getTransitionMatrix();

		prevNode = new Node(NodeType.DUMMY);
		graph.addNode(prevNode);
		graph.setInitialNode(prevNode);

		// 'hmm.getOrder() + 1' to account for final, non-emitting state.
		for (int i = 0; i < hmm.getOrder() + 1; i++) {
			/* create a new node for the next hmmState */
			// LeftRightContext context = (LeftRightContext) hmm.getUnit()
			// .getContext();
			// context.getLeftContext();
			stateNode = new Node(NodeType.STATE, hmm.getUnit().getName());
			stateNode.setObject(hmm.getState(i));
			graph.addNode(stateNode);
			/* Link the new node into the graph */
			if (i == 0) {
				graph.linkNodes(prevNode, stateNode);
			}
			for (int j = 0; j <= i; j++) {
				// System.out.println("TMAT: " + j + " " + i + " " +
				// tmat[j][i]);
				if (tmat[j][i] != LogMath.getLogZero()) {
					// 'j + 1' to account for the initial dummy node
					graph.linkNodes(graph.getNode(j + 1), stateNode);
				}
			}
			prevNode = new Node(NodeType.DUMMY);
			prevNode.copyNode(stateNode);
		}
		/* All words are done. Just add the final dummy */
		// stateNode = new Node(NodeType.DUMMY);
		// graph.linkNodes(prevNode, stateNode);
		graph.setFinalNode(stateNode);

		return graph;
	}

}
