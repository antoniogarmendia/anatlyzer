package anatlyzer.experiments.typing;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2m.atl.core.emf.EMFModel;

import transML.utils.transMLProperties;
import anatlyzer.atl.analyser.AnalysisResult;
import anatlyzer.atl.analyser.batch.RuleConflictAnalysis.OverlappingRules;
import anatlyzer.atl.editor.builder.AnalyserExecutor;
import anatlyzer.atl.editor.builder.AnalyserExecutor.AnalyserData;
import anatlyzer.atl.editor.witness.EclipseUseWitnessFinder;
import anatlyzer.atl.errors.Problem;
import anatlyzer.atl.errors.ProblemStatus;
import anatlyzer.atl.errors.atl_error.AtlErrorFactory;
import anatlyzer.atl.errors.atl_error.ConflictingRuleSet;
import anatlyzer.atl.errors.atl_error.RuleConflict;
import anatlyzer.atl.model.ATLModel;
import anatlyzer.atl.util.AnalyserUtils.CannotLoadMetamodel;
import anatlyzer.atl.witness.IWitnessFinder;
import anatlyzer.atl.witness.UseWitnessFinder;
import anatlyzer.atlext.ATL.Module;
import anatlyzer.experiments.extensions.IExperiment;
import anatlyzer.ui.actions.CheckRuleConflicts;
import anatlyzer.ui.util.AtlEngineUtils;

public abstract class AbstractATLExperiment  implements IExperiment {
	
	protected IFile experimentFile;
	protected HashMap<String, Object> options;

	@Override
	public void setOptions(HashMap<String, Object> options) {
		this.options = options;
	}
	
	
	@Override
	public void setExperimentConfiguration(IFile file) {
		this.experimentFile = file;
	}
	
	@Override
	public void projectDone(IProject p) {
		
	}
	
	@Override
	public void perform(IResource resource, IProgressMonitor monitor) {
		perform(resource);
	}
	
	protected abstract void perform(IResource resource);

	protected AnalyserData executeAnalyser(IResource resource)
			throws IOException, CoreException, CannotLoadMetamodel {
		IFile file = (IFile) resource;
		EMFModel atlEMFModel = AtlEngineUtils.loadATLFile(file);
		ATLModel  atlModel = new ATLModel(atlEMFModel.getResource(), file.getFullPath().toPortableString());
		if ( !( atlModel.getRoot() instanceof Module) ) {
			return null; 
		}

		return executeAnalyser(resource, atlModel);
	}
	
	protected ExpAnalyserData copyData(AnalyserData data) { 
		return new ExpAnalyserData(data);
	}
	
	protected AnalyserData executeAnalyser(IResource resource, ATLModel atlModel)
			throws IOException, CoreException, CannotLoadMetamodel {

		return new AnalyserExecutor().exec(resource, atlModel, false);
	}

	protected void confirmProblems(List<Problem> problems, AnalysisResult r) {
		for (Problem p : problems) {
			if ( p.getStatus() == ProblemStatus.WITNESS_REQUIRED ) {				
				System.out.println("--------------------------");
				
				System.out.println("Confirming: " + new ExpProblem(p).getLocation());
				System.out.println("--------------------------");
				
				removeTempFile();
				
				ProblemStatus result = null;
				try {
					result = createWitnessFinder().find(p, r);
				} catch ( Exception e ) {
					result = ProblemStatus.IMPL_INTERNAL_ERROR;
				}
				
				p.setStatus(result);
			}
		}
	}

	protected IWitnessFinder createWitnessFinder() {
		return new EclipseUseWitnessFinder().			
				checkDiscardCause(false);
	}
	
	protected void removeTempFile() {
		try {
			transMLProperties.loadPropertiesFile(new EclipseUseWitnessFinder().getTempDirectory());					
			File dir = new File(transMLProperties.getProperty("temp"));
			FileUtils.deleteDirectory(dir);
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}

	
	protected RuleConflict doRuleAnalysis(IProgressMonitor monitor, AnalysisResult data) {
		return doRuleAnalysis(monitor, data, false);
	}
	
	protected RuleConflict doRuleAnalysis(IProgressMonitor monitor, AnalysisResult data, boolean createIfEmpty) {
		final CheckRuleConflicts action = new CheckRuleConflicts();
		List<OverlappingRules> result = action.performAction(data, monitor);	
		ArrayList<OverlappingRules> guiltyRules = new ArrayList<OverlappingRules>();
		
		if ( createIfEmpty ) {
			guiltyRules.addAll(result);
		} else {
			for (OverlappingRules overlappingRules : result) {
				if ( overlappingRules.getAnalysisResult() == ProblemStatus.STATICALLY_CONFIRMED || 
						 overlappingRules.getAnalysisResult() == ProblemStatus.ERROR_CONFIRMED || 
						 overlappingRules.getAnalysisResult() == ProblemStatus.ERROR_CONFIRMED_SPECULATIVE ) {
					guiltyRules.add(overlappingRules);
				}
			}
		}
	
		if ( guiltyRules.size() > 0 || createIfEmpty ) {
			RuleConflict rc = AtlErrorFactory.eINSTANCE.createRuleConflict();
			rc.setDescription("Rule conflict");
			for (OverlappingRules overlappingRules : guiltyRules) {
				ConflictingRuleSet set = overlappingRules.createRuleSet();
				rc.getConflicts().add(set);
			}
			return rc;
		}
		
		return null;
	}
}
