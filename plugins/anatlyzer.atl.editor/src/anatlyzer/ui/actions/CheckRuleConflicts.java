package anatlyzer.ui.actions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.m2m.atl.adt.ui.editor.AtlEditor;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorPart;

import witness.generator.WitnessGeneratorMemory;
import anatlyzer.atl.analyser.batch.RuleConflictAnalysis;
import anatlyzer.atl.analyser.batch.RuleConflictAnalysis.OverlappingRules;
import anatlyzer.atl.analyser.generators.USESerializer;
import anatlyzer.atl.editor.builder.AnalyserExecutor;
import anatlyzer.atl.editor.builder.AnalyserExecutor.AnalyserData;
import anatlyzer.atl.footprint.TrafoMetamodelData;
import anatlyzer.atl.util.AnalyserUtils.CannotLoadMetamodel;
import anatlyzer.atlext.OCL.OclExpression;
import anatlyzer.footprint.EffectiveMetamodelBuilder;
import anatlyzer.ui.util.WorkbenchUtil;

public class CheckRuleConflicts implements IEditorActionDelegate {

	private AtlEditor editor;
	private EPackage language;
	private EPackage effective;

	@Override
	public void run(IAction action) {
		// new RuleConflictAnalysis();
		
		IResource resource = editor.getUnderlyingResource();
		try {
			AnalyserData data = new AnalyserExecutor().exec(resource);
			performAction(data);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (CoreException e) {
			e.printStackTrace();
		} catch (CannotLoadMetamodel e) {
			e.printStackTrace();
		}
	}
	
	public List<OverlappingRules> performAction(AnalyserData data) {
		List<OverlappingRules> overlaps = data.getAnalyser().ruleConflictAnalysis();
		List<OverlappingRules> result = new ArrayList<RuleConflictAnalysis.OverlappingRules>();
		for (OverlappingRules overlap : overlaps) {
			// if ( processOverlap(overlap, data) ) {
			processOverlap(overlap, data);
			result.add(overlap);
			//}
		}			
		
		return result;
	}

	/**
	 * Check whether a set of rules provoke a rule conflict. Return true if so.
     *
	 * @param overlap It is modified to indicate the result of the constraint solving process if needed.
	 * @param data
	 * @return
	 */
	private boolean processOverlap(OverlappingRules overlap, AnalyserData data) {
		if ( ! overlap.requireConstraintSolving() ) {
			overlap.setAnalysisResult(OverlappingRules.ANALYSIS_STATIC_CONFIRMED);
			return true;
		}
			
		// Error meta-model
		XMIResourceImpl r1 =  new XMIResourceImpl(URI.createURI("overlap_error"));
		EPackage errorSlice = new EffectiveMetamodelBuilder(overlap.getErrorSlice(data.getAnalyser())).extractSource(r1, "overlap", "http://overlap", "overlap", "overlap");
		
		// Effective meta-model
		if ( effective == null ) {
			XMIResourceImpl r2 =  new XMIResourceImpl(URI.createURI("overlap_effective"));
			TrafoMetamodelData trafoData = new TrafoMetamodelData(data.getAnalyser().getATLModel(), null);
			
			String logicalName = "effective_mm";
			effective = new EffectiveMetamodelBuilder(trafoData).extractSource(r2, logicalName, logicalName, logicalName, logicalName);
		}
		
		// Language meta-model
		if ( language == null )
			language  = data.getSourceMetamodel();

		String projectPath = WorkbenchUtil.getProjectPath();
		
		OclExpression constraint = overlap.getWitnessCondition();
		String constraintStr = USESerializer.retypeAndGenerate( constraint);
		
		System.out.println("Constraint: " + constraintStr);
		
		WitnessGeneratorMemory generator = new WitnessGeneratorMemory(errorSlice, effective, language, constraintStr);
		generator.setTempDirectoryPath(projectPath);
		try {
			if ( ! generator.generate() ) {
				System.out.println("Not witness found!");
				overlap.setAnalysisResult(OverlappingRules.ANALYSIS_SOLVER_DISCARDED);
				return false;
			}
			
			overlap.setAnalysisResult(OverlappingRules.ANALYSIS_SOLVER_CONFIRMED);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			overlap.setAnalysisResult(OverlappingRules.ANALYSIS_SOLVER_FAILED);
			return true; // So that it is included
		}
		
		
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) { }

	@Override
	public void setActiveEditor(IAction action, IEditorPart targetEditor) { 
		if ( targetEditor instanceof AtlEditor ) {
			this.editor = (AtlEditor) targetEditor;			
		}
	}

}
