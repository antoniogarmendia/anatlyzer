package anatlyzer.atl.editor.quickfix.errors;

import org.eclipse.core.resources.IMarker;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;

import anatlyzer.atl.errors.atl_error.FeatureNotFound;
import anatlyzer.atl.model.TypeUtils;
import anatlyzer.atl.quickfixast.ASTUtils;
import anatlyzer.atl.quickfixast.QuickfixApplication;
import anatlyzer.atl.types.BooleanType;
import anatlyzer.atl.types.FloatType;
import anatlyzer.atl.types.IntegerType;
import anatlyzer.atl.types.Metaclass;
import anatlyzer.atl.types.PrimitiveType;
import anatlyzer.atl.types.StringType;
import anatlyzer.atl.types.Type;
import anatlyzer.atl.util.ATLUtils;
import anatlyzer.atl.util.Pair;
import anatlyzer.atlext.ATL.Binding;
import anatlyzer.atlext.OCL.NavigationOrAttributeCallExp;

public class FeatureNotFoundQuickFix_ChangeMetamodel extends AbstractMetamodelChangeQuickfix  {

	@Override
	public boolean isApplicable(IMarker marker) {
		setErrorMarker(marker);
		return checkProblemType(marker, FeatureNotFound.class) && findExpectedType() != null;
	}

	@Override public void resetCache() {}
	
	private Metaclass getSourceType() {
		Type t = null;
		if ( getProblematicElement() instanceof NavigationOrAttributeCallExp ) {		
			NavigationOrAttributeCallExp nav = (NavigationOrAttributeCallExp) getProblematicElement();
			t = nav.getSource().getInferredType();
		} else {
			Binding b = (Binding) getProblematicElement();
			t = b.getOutPatternElement().getInferredType();
		}
		
		if ( t instanceof Metaclass && ((Metaclass) t).getModel() != null ) {
			return (Metaclass) t;
		}
		return null;
	}

	@Override
	public boolean requiresUserIntervention() {
		return findExpectedType() != null;
	}
	
	/**
	 * Returns the type that will be used as the new feature type and
	 * the best upper bound.
	 * @return
	 */
	protected Pair<Type, Integer> findExpectedType() {
		// The binding case
		if ( getProblematicElement() instanceof Binding ) {
			Binding b = (Binding) getProblematicElement();
			int upperBound = TypeUtils.isCollection(b.getValue().getInferredType()) ? -1 : 1;
			if ( b.getLeftType() instanceof PrimitiveType ) {
				return new Pair<Type, Integer>(b.getLeftType(), upperBound);
			}
			// This could be smarter and look for compatible rules...
			return null;			
		}
		
		// The navigation case
		NavigationOrAttributeCallExp nav = (NavigationOrAttributeCallExp) getProblematicElement();		
		Type t = ASTUtils.findExpectedTypeInExpressionPosition(nav, false);
		return new Pair<Type, Integer>(t, TypeUtils.isCollection(t) ? -1 : 1);		
	}
	
	@Override
	public String getChangedMetamodel() {
		return getSourceType().getModel().getName();
	}
	
	@Override
	public QuickfixApplication getQuickfixApplication() {
		NavigationOrAttributeCallExp nav = (NavigationOrAttributeCallExp) getProblematicElement();

		Pair<Type, Integer> pair = findExpectedType();
		
		// This should be asked to the user!!
		if ( requiresUserIntervention() ) {
			throw new UnsupportedOperationException("Not implemented yet!");
		}
		
		QuickfixApplication qfa = new QuickfixApplication(this);
		qfa.mmModify(getSourceType().getKlass(), getSourceType().getModel().getName(), (klass) -> {
			Type featureType = TypeUtils.getUnderlyingType(pair._1);
			
			EStructuralFeature feat = null;
			if ( featureType instanceof PrimitiveType ) {
				feat = EcoreFactory.eINSTANCE.createEAttribute();
				if ( featureType instanceof BooleanType ) {
					feat.setEType(EcorePackage.Literals.EBOOLEAN);
				} else if ( featureType instanceof StringType ) {
					feat.setEType(EcorePackage.Literals.ESTRING);
				} else if ( featureType instanceof IntegerType ) {
					feat.setEType(EcorePackage.Literals.EINT);
				} else if ( featureType instanceof FloatType ) {
					feat.setEType(EcorePackage.Literals.EDOUBLE);
				} else {
					throw new UnsupportedOperationException();
				}
			} else if ( featureType instanceof Metaclass ) {
				feat = EcoreFactory.eINSTANCE.createEReference();
				feat.setEType(((Metaclass) featureType).getKlass());
			} else {
				throw new UnsupportedOperationException();
			}
			
			feat.setName(nav.getName());
			feat.setLowerBound(1);
			feat.setUpperBound(pair._2);
			
			klass.getEStructuralFeatures().add(feat);
		});
		
		return qfa;
	}

	@Override
	public String getDisplayString() {
		return "Create new feature (in the meta-model)";
	}

}
