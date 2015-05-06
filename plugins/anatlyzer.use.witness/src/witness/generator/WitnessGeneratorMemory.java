package witness.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;

import transML.exceptions.transException;
import transML.utils.transMLProperties;
import transML.utils.solver.FactorySolver;
import transML.utils.solver.SolverWrapper;
import transML.utils.solver.use.Solver_use;

// Just for the moment
public class WitnessGeneratorMemory extends WitnessGenerator {
	protected EPackage errorMM;
	protected MetaModel effectiveMM;
	protected MetaModel languageMM;
	protected String oclConstraint;
	private String projectPath;
	private boolean forceOnceInstancePerClass;
	private int minScope = 1;
	private int maxScope = 5;
	
	private static Integer index = 0;
	
	public WitnessGeneratorMemory(EPackage errorSliceMetamodel,  EPackage effectiveMetamodel, EPackage languageMetamodel, String oclConstraint) {
		this.effectiveMM = new MetaModel(effectiveMetamodel);
		this.errorMM = errorSliceMetamodel;

		this.languageMM = new MetaModel(languageMetamodel);
		this.oclConstraint = oclConstraint;
	}
	
	public boolean generate() throws transException {		
		adaptMetamodels();
		
		synchronized (index) {
			WitnessGeneratorMemory.index += 1;			
		}
		
		String witness = generateWitness(getTempDirectoryPath(), errorMM, oclConstraint, index);
		return witness != null;
	}

	private ArrayList<String> additionalConstraints = new ArrayList<String>();
	public void addAdditionaConstraint(String constraint) {
		this.additionalConstraints.add(constraint);
	}
	
	
	protected void adaptMetamodels() {
		// extend error meta-model with mandatory classes in effective meta-model 
		extendMetamodelWithMandatory(errorMM, effectiveMM, languageMM);
	
		// extend error meta-model with concrete children classes of abstract leaf classes
		extendMetamodelWithConcreteLeaves(errorMM, effectiveMM);
		extendMetamodelWithConcreteLeaves(errorMM, languageMM);
	
		// [KM3 meta-models] add instance type name to user-defined data types
		extendMetamodelWithInstanceTypeNames4DataTypes(errorMM);
	
		// copy uri/name/prefix from the language meta-model to the extended error meta-model
		errorMM.setName    (languageMM.getName());
		errorMM.setNsPrefix(languageMM.getNsPrefix());
		errorMM.setNsURI   (languageMM.getNsURI()!=null?languageMM.getNsURI():effectiveMM.getNsURI());

		addBaseObject(errorMM);
		changeNamesToResolveConflicts(errorMM, new USENameModifyier());
		
		if ( forceOnceInstancePerClass ) {
			applyForceOnceInstancePerClass();
		}
		
		loadOclOperations(errorMM);
	}

	public void setMinScope(int minScope) {
		this.minScope = minScope;
	}
	
	public void setMaxScope(int maxScope) {
		this.maxScope = maxScope;
	}
	
	public void forceOnceInstancePerClass() {
		this.forceOnceInstancePerClass = true;	
	}
	
	private void applyForceOnceInstancePerClass() {
		
		for(EClassifier c : errorMM.getEClassifiers()) {
			if ( c instanceof EClass && ! ((EClass) c).isAbstract() ) {
				// transMLProperties.setProperty(property, value);
				// See Solver_use#genPropertiesFile
				// It is not possible to set only the lowerbound...
				
				// So, modifying the constraint which is possible more inneficient for the solver
				oclConstraint += " and " + c.getName() + ".allInstances()->notEmpty()";				
			}
		}
	
	}
	
	/**
	 * Needed to have a common type different from OclAny which is not accepted by the validator...
	 * @param errorMM2
	 */
	private void addBaseObject(EPackage errorMM2) {
		EClass eclass = EcoreFactory.eINSTANCE.createEClass();
		eclass.setName("BaseObject_");
		eclass.setAbstract(true);
		errorMM2.getEClassifiers().add(eclass);
		for(EClassifier c : errorMM2.getEClassifiers()) {
			if ( c instanceof EClass && c != eclass ) {
				if ( ((EClass) c).getESuperTypes().size() == 0 ) {
					((EClass) c).getESuperTypes().add(eclass);
				}
			}
		}
	}

	private void changeNamesToResolveConflicts(EPackage errorMM2, USENameModifyier mod) {
		for(EClassifier c : errorMM2.getEClassifiers()) {
			if ( c instanceof EClass ) {
				mod.adapt((EClass) c, true);

				for(EStructuralFeature f : ((EClass) c).getEStructuralFeatures()) {
					mod.adapt(f, true);
				}
			}
			
		}
	}

	private void changeConflictingClassNames(EPackage errorMM2) {
		// TODO Auto-generated method stub
		
	}

	public String getTempDirectoryPath() {
		return projectPath == null ? "." : projectPath;
	}

	public void setTempDirectoryPath(String projectPath) {
		this.projectPath = projectPath;
	}

	/**
	 * The same as the original one, but it allows scope configuration.
	 */
	@Override
	protected String generateWitness (String path, EPackage metamodel, String ocl_constraint, int index) throws transException {
		return generateWitnessStatic(path, metamodel, ocl_constraint, index, minScope, maxScope, this.additionalConstraints);
	}
	
	public static String generateWitnessStatic(String path, EPackage metamodel, String ocl_constraint, int index, int minScope, int maxScope) throws transException {
		return generateWitnessStatic(path, metamodel, ocl_constraint, index, minScope, maxScope, new ArrayList<String>());
	}
		
	public static String generateWitnessStatic(String path, EPackage metamodel, String ocl_constraint, int index, int minScope, int maxScope, List<String> additionalConstraints) throws transException {

		// load properties file (it requires full path)
		/*
		 * jesusc: Does not work for me in Linux...
		if (path.startsWith("/") || path.startsWith("\\")) {
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			path = workspace.getRoot().getLocation().toString() + path;
		}
		*/
		transMLProperties.loadPropertiesFile(path);
		
		// programmatically select USE solver
		transMLProperties.setProperty("solver", "use");
		
		// generate witness 
		List<String> ocl_constraints = new ArrayList<String>(Arrays.asList(ocl_constraint));
		String metamodelName = metamodel.getName();
		metamodel.setName("metamodel_"+index);		
		
		SolverWrapper solver = FactorySolver.getInstance().createSolverWrapper();
		// Solver_use solver = (Solver_use) FactorySolver.getInstance().createSolverWrapper();
		
		for (String string : additionalConstraints) {
			ocl_constraints.add(string);
		}
		
		
		String model = null;
		//root = metamodel.getEClassifier("SubAction");
		try {
			// use increasing scope (1,2,3...) to obtain smaller models
			for (int scope=minScope; scope<=maxScope && model==null; scope++) {
				transMLProperties.setProperty("solver.scope", ""+scope);
				model = solver.find(metamodel, ocl_constraints);
			}
			metamodel.setName(metamodelName);

			// console.println("generated model: " + ( model!=null? model : "NONE" ));
			System.out.println("generated model: " + ( model!=null? model : "NONE" ));
		}
		catch (transException e) {
			String error = e.getDetails().length>0? e.getDetails()[0] : e.getMessage();
			if (error.endsWith("\n")) error = error.substring(0, error.lastIndexOf("\n"));
			
			// console.println("[ERROR] " + error);
			System.out.println("[ERROR] " + error); 
			throw e;
		}
		
		// return path of generated model
		return model;
	}	

}

