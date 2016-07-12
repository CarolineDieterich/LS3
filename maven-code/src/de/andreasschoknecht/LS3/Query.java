/**
 * Part of the LS3 Similarity-based process model search package.
 * 
 * Licensed under the GNU General Public License v3.
 *
 * Copyright 2012 by Andreas Schoknecht <andreas_schoknecht@web.de>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Andreas Schoknecht
 */

package de.andreasschoknecht.LS3;

import java.io.IOException;
import java.util.LinkedHashSet;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jdom2.JDOMException;

/**
 * <p>
 * A Query finally represents a process model as a so called pseudo document. Therefore, the term term frequencies from the PNML file
 * are calculated and weighted afterwards with the log-entropy weighting scheme. The pseudo document is then created by calculating
 * the formula p^T * Uk * Sk^-1.
 * <p>
 * The values of the Latent Semantic Analysis Measure with respect to a DocumentCollection can then be calculated.
 */
public class Query extends LS3Document {

	/** The term frequencies for the terms contained in the document. Frequency for terms not appearing in the document are 0. */
	private double[] termFrequencies;
	
	/** The log-entropy weighted term frequencies for the terms contained in the document. */
	private double[] weightedTermFrequencies;
	
	/** The array representing a query model as a pseudo document. */
	private double[] pseudoDocument;
	
	/** The LSSM similarity values for the query model and each model in a document collection. */
	private double[] lssmValues;
	
	Query(String pnmlPath) {
		super(pnmlPath);
	}
	
	
	/**
	 * Extract terms of a query model and store them in as Bag-of-Words. Until now the term list contains only the terms of the 
	 * query model.
	 */
	void extractTerms() {
		PNMLReader pnmlReader = new PNMLReader();
		try {
			pnmlReader.processDocument(this);
		} catch (JDOMException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Calculate term frequencies for the query model. Thereby, the term frequencies for terms not contained in the query model
	 * but appearing in other models of the collection are set to 0. Terms which are only part of the query model are not represented.
	 * They are left out because the goal is to put the query model in the vector space of the Term-Document Matrix of a document
	 * collection.
	 *
	 * @param allTerms The list of all terms contained in the whole model collection
	 */
	void calculateTermFrequencies(LinkedHashSet<String> allTerms) {
		// allTermsArray contains all the terms of the Term-Document Matrix of the document collection
		String[] allTermsArray = allTerms.toArray(new String[allTerms.size()]);
		termFrequencies = new double[allTermsArray.length];
		
		for(int i = 0; i < allTermsArray.length; i++) {
			if( this.getTermCollection().contains(allTermsArray[i]) ) {
				String tmp = allTermsArray[i];
	        	double count = this.getTermCollection().count(tmp);
	        	termFrequencies[i] = count;
			} else {
				termFrequencies[i] = 0;
			}				
		}		
	}
		
	/**
	 * Calculate weighted frequencies for the terms of a query model based on the array of term frequencies. For the calculation of
	 * the weighted frequencies the absolute frequencies of terms (df and gf) in the document collection is not updated as the 
	 * query vector shall be inserted in the vector space generated by the Term-Document Matrix of the document collection. 
	 *
	 * @param tdMatrix The Term-Document Matrix containing all terms of a model collection
	 */
	void calculateWeightedFrequencies(TDMatrix tdMatrix) {
		weightedTermFrequencies = new double[termFrequencies.length];
		int termNumber = weightedTermFrequencies.length;

		// Get the arrays for global and document frequencies for the calculation of weighted term frequencies
		double[] dfArray = tdMatrix.getDf();
		double[] gfArray = tdMatrix.getGf();
		int documentNmber = tdMatrix.getColumnNumber();

		for (int i = 0; i < termNumber; i++) {
			if (termFrequencies[i] != 0){
				double pij = termFrequencies[i]/gfArray[i];
				weightedTermFrequencies[i] = calcLogBase2(termFrequencies[i] + 1) * 
						(1+(dfArray[i]*(pij * calcLogBase2(pij))/calcLogBase2(documentNmber)));
			}
		}
	}
	
	/**
	 * Calculate the logarithm of a number with base 2 (natural logarithm).
	 *
	 * @param number The number of which the logarithm shall be calculated
	 * @return The logarithm of number with base 2
	 */
	private static double calcLogBase2(double number){				
		return Math.log(number)/Math.log(2);
	}
	
	/**
	 * Calculate pseudo document by applying the formula p^T * Uk * Sk^-1.
	 *
	 * @param Uk The matrix of term vectors Uk
	 * @param Sk The matrix of singular values Sk
	 */
	void calculatePseudoDocument(RealMatrix Uk, RealMatrix Sk) {
		// temp = q^T * Uk: calculate transpose of q times Uk
		pseudoDocument = Uk.preMultiply(weightedTermFrequencies);
		
		// calculate inverse of Sk
		RealMatrix inverseSk = MatrixUtils.inverse(Sk);
		
		// pseudoDocument = temp * inverseSk: calculate pseudoDocument by multiplying temp and inverseSk.
		pseudoDocument = inverseSk.preMultiply(pseudoDocument);	
	}
	
	/**
	 * Calculate the LSSM values with respect to a document collection.
	 *
	 * @param Sk The matrix Sk of singular values
	 * @param Vtk The matrix Vtk of the singular value decomposition
	 */
	void calculateLSSMValues(RealMatrix Sk, RealMatrix Vtk) {
		// scale Vtk with singular value matrix Sk
		RealMatrix scaledVtk = Sk.multiply(Vtk);
		
		// the query model as vector
		ArrayRealVector queryVector = new ArrayRealVector(pseudoDocument);
		
		int docsNumber = scaledVtk.getColumnDimension();
		lssmValues = new double[docsNumber];
		
		for (int i = 0; i < docsNumber; i++){
			RealVector columnVector = scaledVtk.getColumnVector(i);
			lssmValues[i] = (queryVector.cosine(columnVector) + 1) / 2;	
		}		
	}
	
	double[] getTermFrequencies() {
		return termFrequencies;
	}

	double[] getWeightedTermFrequencies() {
		return weightedTermFrequencies;
	}

	double[] getPseudoDocument() {
		return pseudoDocument;
	}

	double[] getLSSMValues() {
		return lssmValues;
	}

}
