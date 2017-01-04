package anatlyzer.atl.analyser.batch.invariants;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.Assert;

import anatlyzer.atl.analyser.generators.CSPModel;
import anatlyzer.atl.analyser.generators.ErrorSlice;
import anatlyzer.atl.util.Pair;
import anatlyzer.atlext.ATL.MatchedRule;
import anatlyzer.atlext.ATL.OutPatternElement;
import anatlyzer.atlext.OCL.CollectionOperationCallExp;
import anatlyzer.atlext.OCL.Iterator;
import anatlyzer.atlext.OCL.LetExp;
import anatlyzer.atlext.OCL.OCLFactory;
import anatlyzer.atlext.OCL.OclExpression;
import anatlyzer.atlext.OCL.OperationCallExp;
import anatlyzer.atlext.OCL.OperatorCallExp;
import anatlyzer.atlext.OCL.SequenceExp;

public class SplitNodeOperation implements IInvariantNode {

	private List<IInvariantNode> paths;
	private OperationCallExp expr;

	public SplitNodeOperation(List<IInvariantNode> paths, OperationCallExp expr) {
		this.paths = paths;
		this.expr = expr;
	}
	
	@Override
	public void genErrorSlice(ErrorSlice slice) {
		paths.forEach(p -> p.genErrorSlice(slice));
	}
	
	@Override
	public OclExpression genExpr(CSPModel builder) {
		Assert.isTrue(paths.size() > 1);
		
		// Asume the operation is a boolean expression
		
		OclExpression result = paths.get(0).genExpr(builder);
		for(int i = 1; i < paths.size(); i++) {
			IInvariantNode node = paths.get(i);
			
			OperatorCallExp op = OCLFactory.eINSTANCE.createOperatorCallExp();
			op.setOperationName("or");

			op.setSource(result);
			op.getArguments().add(node.genExpr(builder));
			
			result = op;
		}
		
		return result;
	}

	@Override
	public MatchedRule getContext() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void getTargetObjectsInBinding(Set<OutPatternElement> elems) {  
		paths.forEach(n -> n.getTargetObjectsInBinding(elems));
	}

	@Override
	public Pair<LetExp, LetExp> genIteratorBindings(CSPModel builder, Iterator it) {
		// Do nothing
		return null;
	}
}
