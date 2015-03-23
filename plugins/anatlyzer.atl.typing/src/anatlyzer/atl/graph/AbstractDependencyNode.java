package anatlyzer.atl.graph;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import anatlyzer.atl.analyser.generators.ErrorSlice;
import anatlyzer.atl.analyser.generators.GraphvizBuffer;
import anatlyzer.atl.errors.Problem;
import anatlyzer.atl.errors.atl_error.LocalProblem;
import anatlyzer.atl.util.ATLUtils;
import anatlyzer.atlext.ATL.Callable;
import anatlyzer.atlext.ATL.ContextHelper;
import anatlyzer.atlext.ATL.RuleVariableDeclaration;
import anatlyzer.atlext.ATL.StaticHelper;
import anatlyzer.atlext.ATL.StaticRule;
import anatlyzer.atlext.OCL.OclExpression;
import anatlyzer.atlext.OCL.PropertyCallExp;

public abstract class AbstractDependencyNode implements DependencyNode {

	public LinkedList<DependencyNode> dependencies = new LinkedList<DependencyNode>();
	public LinkedList<DependencyNode> depending    = new LinkedList<DependencyNode>();
	public LinkedList<ConstraintNode> constraints = new LinkedList<ConstraintNode>();
	
	protected Problem	problem;
	protected boolean leadsToExecution = true;
	
	protected static boolean problemInExpression(LocalProblem lp, OclExpression expr) {
		EObject obj = lp.getElement();
		TreeIterator<EObject> it = expr.eAllContents();
		while ( it.hasNext() ) {
			EObject sub = it.next();
			if ( sub == obj ) {
				return true;
			}
			
			// Very similar to OclSlice, except that here static rules are considered
			if ( sub instanceof PropertyCallExp ) {
				PropertyCallExp pce = (PropertyCallExp) sub;
				if ( ! pce.isIsStaticCall() ) {
					EList<ContextHelper> resolvers = pce.getDynamicResolvers();
					for (ContextHelper contextHelper : resolvers) {
						OclExpression body = ATLUtils.getBody(contextHelper);
						if ( problemInExpression(lp, body))
							return true;
					}	
				} else {
					if ( pce.getStaticResolver() instanceof StaticHelper ) {
						StaticHelper moduleHelper = (StaticHelper) pce.getStaticResolver();
						OclExpression body = ATLUtils.getBody(moduleHelper);
						if ( problemInExpression(lp, body))
							return true; 
					} else if ( pce.getStaticResolver() instanceof StaticRule ) {
						EList<RuleVariableDeclaration> variables = ((StaticRule) pce.getStaticResolver()).getVariables();
						for (RuleVariableDeclaration v : variables) {
							if ( problemInExpression(lp, v.getInitExpression() )) {
								return true;
							}
						}
					} else {
						throw new UnsupportedOperationException(pce.getStaticResolver().getClass() + " not considered");
					}
				}
			}
		}
		
		return false;
	}

	protected boolean checkDependenciesAndConstraints(LocalProblem lp) {
		for (ConstraintNode constraintNode : constraints) {
			if ( constraintNode.isInPath(lp) ) 
				return true;
		}
		
		DependencyNode dep = getDepending();
		if ( dep != null && dep.isInPath(lp) )
			return true;
		
		return false;
	}

	
	@Override
	public void addDependency(DependencyNode node) {
		if ( node == this )
			throw new IllegalArgumentException();
		dependencies.add(node);
		node.addDepending(this);
	}

	public DependencyNode getDependency() {
		if 		( dependencies.size() == 0 ) return null;
		else if ( dependencies.size() == 1 ) return dependencies.get(0);
		
		throw new IllegalStateException("Only one dependency per node supported");
	}

	public DependencyNode getDepending() {
		if 		( depending.size() == 0 ) return null;
		else if ( depending.size() == 1 ) return depending.get(0);
		
		throw new IllegalStateException("Only one depending node supported");
	}

	public ConstraintNode getConstraint() {
		if 		( constraints.size() == 0 ) return null;
		else if ( constraints.size() == 1 ) return constraints.get(0);
		
		throw new IllegalStateException("Only one constraint per node supported");
	}
	
	@Override
	public void addDepending(DependencyNode node) {
		depending.add(node);
	}
	
	
	
	@Override
	public void addConstraint(ConstraintNode constraint) {
		this.constraints.add(constraint);
	}
	
	protected void generatedDependencies(ErrorSlice slice) {
		for(DependencyNode n : dependencies) {
			if ( n.leadsToExecution() )
				n.genErrorSlice(slice);
		}					
		for(ConstraintNode c : constraints) {
			c.genErrorSlice(slice);
		}					
		
	}

	public void setProblem(Problem p) {
		this.problem = p;
	}
	
	public Problem getProblem() {
		return problem;
	}
	
	protected List<DependencyNode> getDependencies() {
		return Collections.unmodifiableList(dependencies);
	}
	
	@Override
	public void genGraphviz(GraphvizBuffer gv) {
		for (DependencyNode n : dependencies) {
			n.genGraphviz(gv);
			gv.addEdge(this, n);
		}
		
		for (ConstraintNode c : constraints) {
			c.genGraphviz(gv);
			gv.addEdge(this, c);
		}
		
	}
	
	
	public boolean leadsToExecution() {
		return this.leadsToExecution;
	}
	
	public void setLeadsToExecution(boolean leadsToExecution) {
		this.leadsToExecution  = leadsToExecution;
	}
	
}
