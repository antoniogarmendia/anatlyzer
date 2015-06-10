package anatlyzer.atl.analyser.generators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;

import analyser.atl.problems.IDetectedProblem;
import anatlyzer.atl.analyser.Analyser;
import anatlyzer.atl.analyser.namespaces.ClassNamespace;
import anatlyzer.atl.analyser.namespaces.GlobalNamespace;
import anatlyzer.atl.analyser.namespaces.ITypeNamespace;
import anatlyzer.atl.analyser.namespaces.MetamodelNamespace;
import anatlyzer.atl.model.TypeUtils;
import anatlyzer.atl.types.BooleanType;
import anatlyzer.atl.types.CollectionType;
import anatlyzer.atl.types.EnumType;
import anatlyzer.atl.types.FloatType;
import anatlyzer.atl.types.IntegerType;
import anatlyzer.atl.types.Metaclass;
import anatlyzer.atl.types.SequenceType;
import anatlyzer.atl.types.SetType;
import anatlyzer.atl.types.StringType;
import anatlyzer.atl.types.Type;
import anatlyzer.atl.types.Unknown;
import anatlyzer.atl.util.ATLCopier;
import anatlyzer.atl.util.ATLUtils;
import anatlyzer.atlext.ATL.ContextHelper;
import anatlyzer.atlext.ATL.Helper;
import anatlyzer.atlext.ATL.StaticHelper;
import anatlyzer.atlext.OCL.OclExpression;
import anatlyzer.footprint.IEffectiveMetamodelData;

public class ErrorSlice implements IEffectiveMetamodelData {
	private HashSet<EClass> explicitTypes = new HashSet<EClass>();
	private HashSet<EStructuralFeature> explicitFeatures = new HashSet<EStructuralFeature>();

	// private HashMap<String, java.util.Set<Helper>> helpers =
	// new HashMap<String, java.util.Set<Helper>>();
	private Set<Helper> helpers = new HashSet<Helper>();

	private Set<String> metamodelNames;
	private boolean retype = true; // retype by default

	private List<Helper> helpersNotSupported = new LinkedList<Helper>();
	private IDetectedProblem problem;

	public ErrorSlice(Set<String> sourceMetamodels, IDetectedProblem problem) {
		this.metamodelNames = sourceMetamodels;
		this.problem = problem;

		// Ugly hack
		// EClass thisModuleClass = EcoreFactory.eINSTANCE.createEClass();
		// thisModuleClass.setName(Analyser.USE_THIS_MODULE_CLASS);
		// addMetaclassNeededInError(thisModuleClass);
	}

	public void addExplicitMetaclass(Metaclass type) {
		// If no meta-model name is given, then all classes are added
		// if ( metamodelName == null ||
		// type.getModel().getName().equals(metamodelName) ) {
		
		if (metamodelNames.contains(type.getModel().getName())) {

			// The metaclass object could also include the information whether
			// it is
			// a target object or not
			explicitTypes.add(type.getKlass());
		}
	}

	public void addMetaclassNeededInError(EClass eClass) {
		explicitTypes.add(eClass);
	}

	public void addExplicitFeature(EStructuralFeature f) {
		explicitFeatures.add(f);
	}

	@Override
	public Set<EClass> getClasses() {
		return Collections.unmodifiableSet(this.explicitTypes);
	}

	@Override
	public Set<EStructuralFeature> getFeatures() {
		return Collections.unmodifiableSet(this.explicitFeatures);
	}

	public boolean hasHelpersNotSupported() {
		return helpersNotSupported.size() > 0;
	}

	public List<Helper> getHelpersNotSupported() {
		return Collections.unmodifiableList(helpersNotSupported);
	}

	public boolean addHelper(ContextHelper contextHelperAnn) {
		return addHelperAux(contextHelperAnn);
	}

	public boolean addHelper(StaticHelper staticHelper) {
		return addHelperAux(staticHelper);
	}

	private HashSet<Helper> alreadyAdded = new HashSet<Helper>();

	private boolean addHelperAux(Helper helper) {
		if (alreadyAdded.contains(helper))
			return false;
		alreadyAdded.add(helper);

		if (!new USEValidityChecker(helper).perform().isValid()) {
			helpersNotSupported.add(helper);
		}

		helper = (Helper) ATLCopier.copySingleElement(helper, true);
		helpers.add(helper);

		// helpers.get(ctxTypeName).add(helper);
		return true;
	}

	public Set<Helper> getHelpers() {
		return helpers;
	}

	public String toOneLineString() {
		String classes = explicitTypes.stream().map(e -> e.getName())
				.collect(Collectors.joining(", "));

		String features = explicitFeatures
				.stream()
				.map(f -> f.getEContainingClass().getName() + "." + f.getName())
				.collect(Collectors.joining(", "));

		String helperList = helpers.stream().map(h -> {
			return getContextName(h) + "." + ATLUtils.getHelperName(h);
		}).collect(Collectors.joining(", "));

		return classes + " | " + features + "|" + helperList;
	}

	private String getContextName(Helper h) {
		if (h instanceof ContextHelper) {
			return TypeUtils.getNonQualifiedTypeName(((ContextHelper) h)
					.getContextType());
		} else {
			return Analyser.USE_THIS_MODULE_CLASS;
		}
	}

	public void loadFromString(String line, Analyser analyser) {
		String[] parts = line.split("\\|");

		String classes = parts[0];
		String features = parts.length == 2 ? parts[1] : "";
		String helpers = parts.length == 3 ? parts[2] : "";

		String[] classNames = classes.split(",");
		for (String cname : classNames) {
			cname = cname.trim();
			EClass k = findClass(analyser.getNamespaces(), cname);
			if (k != null) {
				this.explicitTypes.add(k);
			}
		}

		String[] featureNames = features.split(",");
		for (String f : featureNames) {
			String[] twoParts = f.split("\\.");
			if (twoParts.length != 2)
				continue;

			String klass = twoParts[0].trim();
			String name = twoParts[1].trim();
			EClass k = findClass(analyser.getNamespaces(), klass);
			if (k != null) {
				EStructuralFeature feature = k.getEStructuralFeature(name);
				if (feature != null)
					addExplicitFeature(feature);
			}
		}

		String[] helperNames = helpers.split(",");
		for (String h : helperNames) {
			String[] twoParts = h.split("\\.");
			if (twoParts.length != 2)
				continue;

			String klass = twoParts[0].trim();
			String name = twoParts[1].trim();
			List<Helper> allHelpers = ATLUtils.getAllHelpers(analyser
					.getATLModel());
			for (Helper helper : allHelpers) {
				// This is not completely accurate, not checking the actual
				// model (it is not serialized)
				if (ATLUtils.getHelperType(helper).getName().equals(klass)
						&& ATLUtils.getHelperName(helper).equals(name)) {
					if (helper instanceof StaticHelper) {
						addHelper((StaticHelper) helper);
					} else {
						addHelper((ContextHelper) helper);
					}
					break;
				}
			}

			EClass k = findClass(analyser.getNamespaces(), klass);
			if (k != null) {
				EStructuralFeature feature = k.getEStructuralFeature(name);
				if (feature != null)
					addExplicitFeature(feature);
			}
		}
	}

	private EClass findClass(GlobalNamespace namespaces, String cname) {
		List<MetamodelNamespace> mm = namespaces.getMetamodels();
		for (MetamodelNamespace metamodelNamespace : mm) {
			ITypeNamespace tn = metamodelNamespace.getClassifier(cname);
			if (tn != null && tn instanceof ClassNamespace) {
				return (((ClassNamespace) tn).getKlass());
			}
		}
		return null;
	}

	@Override
	public Collection<EAnnotation> getPackageAnnotations() {
		throw new UnsupportedOperationException();
	}
}
