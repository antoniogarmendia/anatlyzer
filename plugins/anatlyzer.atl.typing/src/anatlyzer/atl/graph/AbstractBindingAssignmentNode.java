package anatlyzer.atl.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;

import anatlyzer.atl.analyser.generators.CSPModel;
import anatlyzer.atl.analyser.generators.OclGeneratorAST;
import anatlyzer.atl.errors.Problem;
import anatlyzer.atl.model.TypeUtils;
import anatlyzer.atl.util.ATLUtils;
import anatlyzer.atlext.ATL.Binding;
import anatlyzer.atlext.ATL.OutPatternElement;
import anatlyzer.atlext.ATL.RuleResolutionInfo;
import anatlyzer.atlext.ATL.StaticRule;
import anatlyzer.atlext.OCL.IteratorExp;
import anatlyzer.atlext.OCL.OCLFactory;
import anatlyzer.atlext.OCL.OclExpression;
import anatlyzer.atlext.OCL.OperationCallExp;
import anatlyzer.atlext.OCL.VariableExp;

public abstract class AbstractBindingAssignmentNode<P extends Problem> extends AbstractProblemNode<P> {

	public AbstractBindingAssignmentNode(P p) {
		super(p);
	}
	

	protected List<RuleResolutionInfo> sortRules(List<RuleResolutionInfo> resolvedBy) {
		return resolvedBy.stream().sorted((o1, o2) -> {
			return o1.getRule().getLocation().compareTo(o2.getRule().getLocation());
		}).collect(Collectors.toList());
	}
	
	protected OclExpression genBindingRightPart(CSPModel model, Binding binding) {
		OclExpression value = model.gen(binding.getValue(), new OclGeneratorAST.LazyRuleToDummyValue(model.getTargetDummyVariable()));

		if ( TypeUtils.isCollection(ATLUtils.getSourceType(binding)) ) {
			// In case it is not flatten... apply flatten() to respect ATL semantics and avoid errors
			value = model.createCollectionOperationCall(value, "flatten");
		}
		
		// Remove any possible use of the target dummy variable by filtering
		// Look out: I need to use the original expression, not the path expression
		if ( exprContainsTargetValue(binding.getValue()) ) {

			IteratorExp reject = model.createIterator(value, "reject");
			VariableExp itRef = OCLFactory.eINSTANCE.createVariableExp();
			itRef.setReferredVariable( reject.getIterators().get(0) );
			
			OperationCallExp body = model.createKindOf(itRef, null, model.getTargetDummyClass());
			reject.setBody(body);
			
			value = reject;
		}
		
		/*
		Old style, using oclIsUndefined
		// Could I check if there are lazy rule calls to avoid this??
		if ( TypeUtils.isCollection(ATLUtils.getSourceType(binding)) ) {
			IteratorExp select = model.createIterator(value, "select");
			VariableDeclaration vd = select.getIterators().get(0);

			VariableExp varRef = OCLFactory.eINSTANCE.createVariableExp();
			varRef.setReferredVariable(vd);
			OperatorCallExp notUndefined = model.negateExpression(model.createOperationCall(varRef, "oclIsUndefined"));

			select.setBody(notUndefined);
			
			value = select;
		}
		*/
		
		return value;
	}

	/**
	 * 
	 * TODO: This is not completely accurate because a helper could call a lazy
	 * rule. The analyzer should be able to indicate, for each sub-expression, it holds
	 * a source or target value.
	 * 
	 * @param value
	 * @return
	 */
	private boolean exprContainsTargetValue(OclExpression value) {
		TreeIterator<EObject> it = value.eAllContents();
		while ( it.hasNext() ) {
			EObject expr = (EObject) it.next();
			
			if ( expr instanceof VariableExp && ((VariableExp) expr).getReferredVariable() instanceof OutPatternElement ) {
				return true;
			} else if ( expr instanceof OperationCallExp ) {
				OperationCallExp call = (OperationCallExp) expr;
				if ( call.getStaticResolver() instanceof StaticRule ) {
					return true;
				}
			}
		
		}
		return false;
	}

}
