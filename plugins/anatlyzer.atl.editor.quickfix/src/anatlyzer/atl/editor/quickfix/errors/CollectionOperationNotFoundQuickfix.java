package anatlyzer.atl.editor.quickfix.errors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import anatlyzer.atl.editor.quickfix.AbstractAtlQuickfix;
import anatlyzer.atl.errors.atl_error.BindingWithoutRule;
import anatlyzer.atl.errors.atl_error.CollectionOperationNotFound;
import anatlyzer.atl.errors.atl_error.ModelElement;
import anatlyzer.atlext.ATL.Binding;
import anatlyzer.atlext.ATL.Rule;
import anatlyzer.atlext.OCL.CollectionOperationCallExp;

public class CollectionOperationNotFoundQuickfix extends AbstractAtlQuickfix {
	private static final int threshold = 3;		// threshold distance to try an operation name with +1 or -1 params
	// number of parameters X operation name
	private static LinkedHashMap<Integer, List<String>> collectionOps = new LinkedHashMap<>();
	private static HashMap<String, List<PrimTypes>> primitiveParam = new HashMap<>();
	
	enum PrimTypes { 
		Integer ("0"), 
		String("''");
		
		private String defaultLiteral;
		
		PrimTypes(String dl) { this.defaultLiteral = dl; }		
		public String defaultLiteral() { return this.defaultLiteral;}
	}
	
	static {
	    collectionOps.put(0, Arrays.asList("first", "flatten", "last", "asBag", "asOrderedSet", "asSequence", "asSet", "isEmpty", "notEmpty", "size", "sum", "debug", "oclIsUndefined", "oclType", "refImmediateComposite", "toString"));
		collectionOps.put(1, Arrays.asList("append", "at", "indexOf", "prepend", "union", "count", "excludes", "excludesAll", "excluding", "includes", "includesAll", "including", "oclIsKindOf", "oclIsTypeOf", "refGetValue"));
		collectionOps.put(2, Arrays.asList("insertAt", "subsequence", "refInvokeOperation", "refSetValue", "refUnsetValue"));
	}
	
	static {
		primitiveParam.put("at", Collections.singletonList(PrimTypes.Integer));
		primitiveParam.put("subsequence", Arrays.asList(PrimTypes.Integer, PrimTypes.Integer));
		primitiveParam.put("refGetValue", Collections.singletonList(PrimTypes.String));
	}
	
	private int distance(String a, String b) { // Levenshtein distance
        a = a.toLowerCase();
        b = b.toLowerCase();
        // i == 0
        int [] costs = new int [b.length() + 1];
        for (int j = 0; j < costs.length; j++)
            costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            // j == 0; nw = lev(i - 1, j)
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }
	
	@Override
	public boolean isApplicable(IMarker marker) {
		return checkProblemType(marker, CollectionOperationNotFound.class);
	}
	
	private int getClosestDistance(String op, int numPar, List<Integer> distance) {
		
		for (String candidate : collectionOps.get(numPar))
			distance.add(this.distance(op, candidate));
		
		System.out.println(collectionOps.get(numPar)+"\n"+distance);
		
		return Collections.min(distance);
	}
	
	private String getClosest(String op, int numPar) {
		HashMap<Integer, List<Integer>> distances = new HashMap<>();
		distances.put(numPar, new ArrayList<Integer>());
		
		int minDistance = this.getClosestDistance(op, numPar, distances.get(numPar));
		
		if (minDistance >= CollectionOperationNotFoundQuickfix.threshold) {		
			List<Integer> pars2explore = new ArrayList<Integer>();
			switch (numPar) {
				case 1: pars2explore.addAll(Arrays.asList(0, 2)); break;
				case 0:
				case 2: pars2explore.add(1); break;
			}
			
			int minD = 10;
			int param = -1;
			
			for (int p : pars2explore) {
				distances.put(p, new ArrayList<Integer>());
				int currentD = this.getClosestDistance(op, p, distances.get(p)); 
				if (currentD < minD) {
					param = p;
					minD = currentD;
				}
			}
			if (minD < minDistance) {
				numPar = param;
				minDistance = minD;
			}
		}
			
		int closestIndex = distances.get(numPar).indexOf(minDistance);
		String closestOp = collectionOps.get(numPar).get(closestIndex);
		System.out.println("Closest is "+closestOp);
		return closestOp;			
		
	}

	private void fixParams(int numP, String closest, IDocument document, int startPos, int endPos) {  // a bit redundant that we calculate this again...
		int paramsClosest = 0;
		
		for (int par : Arrays.asList(0, 1, 2)) {
			if (collectionOps.get(par).contains(closest)) {
				paramsClosest = par;
				break;
			}
		}
		
		if (paramsClosest > numP) {
			System.out.println("You need to add "+(paramsClosest - numP )+" params");
			if (numP == 0) {	// We know how to do this: we just add a dummy var 'param' or the default literal in case of primitive types...
				try {
					int closeParIdx = document.get(startPos, endPos).indexOf(")");
					
					String param = primitiveParam.get(closest) == null ? "param" : primitiveParam.get(closest).get(0).defaultLiteral();
					
					document.replace(startPos+1, closeParIdx, param+")");
				}
				catch (BadLocationException e) {
					
				}
			}
		}
		else if (paramsClosest < numP) {
			System.out.println("You need to remove "+(numP - paramsClosest )+" params");
			if (numP == 1) {	// we know how to do this
				try {
					int closeParIdx = document.get(startPos, endPos).indexOf(")");
					document.replace(startPos+1, closeParIdx, ")");
				}
				catch (BadLocationException e) {
					
				}
			}
		}
		else System.out.println("number of params match.");
	}
	
	@Override
	public void apply(IDocument document) {

		try {
			CollectionOperationNotFound p = (CollectionOperationNotFound) getProblem();
			
			// p.getOperationName() is null at this point 
			CollectionOperationCallExp elm = (CollectionOperationCallExp)p.getElement();
			
			System.out.println("Collection operation "+elm.getOperationName()+"not found");
			
			String closest = this.getClosest(elm.getOperationName(), elm.getArguments().size());
			
			int[] sourceOffset = getElementOffset(elm.getSource(), document);

			int sourceOffsetEnd = sourceOffset[1];
			
			int offsetEnd   = getProblemEndOffset();
			String rest = document.get(sourceOffsetEnd, offsetEnd - sourceOffsetEnd);
			int parent = rest.indexOf("(");
			
			document.replace(sourceOffsetEnd, parent, "->"+closest);
			
			this.fixParams(elm.getArguments().size(), closest, document, sourceOffsetEnd+("->"+closest).length(), offsetEnd-parent+("->"+closest).length());
			
		} catch (CoreException e) {
			throw new RuntimeException(e);
		} catch (BadLocationException e) {
			throw new RuntimeException(e);
		}
		
	}

	@Override
	public Point getSelection(IDocument document) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getAdditionalProposalInfo() {
		return "Chooses a collection operation with similar name";
	}

	@Override
	public String getDisplayString() {
		return "Choose a valid collection operation name";
	}

	@Override
	public Image getImage() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IContextInformation getContextInformation() {
		// TODO Auto-generated method stub
		return null;
	}


}