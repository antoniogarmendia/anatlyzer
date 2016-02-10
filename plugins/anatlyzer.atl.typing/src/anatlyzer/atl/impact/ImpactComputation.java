package anatlyzer.atl.impact;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EObject;

import anatlyzer.atl.analyser.AnalysisResult;
import anatlyzer.atl.errors.Problem;
import anatlyzer.atl.errors.atl_error.LocalProblem;
import anatlyzer.atl.errors.atl_error.RuleConflict;
import anatlyzer.atl.model.ATLModel.ITracedATLModel;
import anatlyzer.atl.util.ATLUtils;
import anatlyzer.atl.util.AnalyserUtils;

public class ImpactComputation {
	private AnalysisResult original;
	private AnalysisResult changed;
	private ITracedATLModel trace;
	private Collection<Problem> newProblems;
	private Collection<Problem> fixedProblems;
	private HashSet<Problem> mayBeFixedProblems;
	private HashSet<Problem> mayBeNewProblems;

	public ImpactComputation(AnalysisResult original, AnalysisResult changed, ITracedATLModel trace) {
		this.original = original;
		this.changed  = changed;
		this.trace    = trace;
	}
	
	public AnalysisResult getOriginal() {
		return original;
	}
	
	public AnalysisResult getChanged() {
		return changed;
	}
	
	public ImpactComputation perform() {
		this.newProblems = new HashSet<Problem>();
		
		// Those for which a corresponding problem in the new version of the transformation cannot be found
		// this.fixedProblems = new HashSet<Problem>(this.original.getLocalProblems());
		this.fixedProblems = new HashSet<Problem>();

		this.mayBeNewProblems = new HashSet<Problem>();

		this.mayBeFixedProblems = new HashSet<Problem>();
		
		// Detect new problems (for local problems...)
		// and fixed problems
		
		for (LocalProblem pChanged : possibleLocalProblems(this.changed)) {	
			EObject eChanged = pChanged.getElement();
			boolean found = false;
			for (LocalProblem pOriginal : possibleLocalProblems(this.original)) {
				EObject tgt = trace.getTarget(pOriginal.getElement());
				
				if ( eChanged == tgt && isMissingEqual(trace, pOriginal.getMissing(), pChanged.getMissing()) && pOriginal.eClass() == pChanged.eClass() ) {
					// The problem is mapped, then it is not a new problem
					found = true;
					break;
				}
			}
			
			if ( ! found && ! AnalyserUtils.isInternalError(pChanged) && ! AnalyserUtils.isWitnessRequred(pChanged)) {
				this.newProblems.add(pChanged);
			} else if ( ! found ) {
				// This is unlikely
				this.mayBeNewProblems.add(pChanged);
			}
		}
		
		for (LocalProblem pOriginal : possibleLocalProblems(this.original)) {
			boolean found = false;
			for (LocalProblem pChanged : possibleLocalProblems(this.changed)) {
				EObject eChanged = pChanged.getElement();
				EObject tgt = trace.getTarget(pOriginal.getElement());
				
				if ( eChanged == tgt && isMissingEqual(trace, pOriginal.getMissing(), pChanged.getMissing()) && pOriginal.eClass() == pChanged.eClass() ) {
					found = true;
					break;
				}
			}
			
			if ( ! found && ! AnalyserUtils.isInternalError(pOriginal) && ! AnalyserUtils.isWitnessRequred(pOriginal)) {
				this.fixedProblems.add(pOriginal);
			} else if ( ! found ) {
				this.mayBeFixedProblems.add(pOriginal);
			}
		}
		
		
		// Treat rule conflicts differently
		// This is a bit weird, because in principle there is only 
		// a maximum of one RuleConflict instance per analysed transformation 
		List<RuleConflict> originalConflicts = getRuleConflicts(original);
		List<RuleConflict> changedConflicts = getRuleConflicts(changed);
		if ( originalConflicts.isEmpty() && ! changedConflicts.isEmpty() ) {
			this.newProblems.addAll(changedConflicts);
		} else if ( ! originalConflicts.isEmpty() && changedConflicts.isEmpty() ) {
			this.fixedProblems.addAll(originalConflicts);
		}
				
		
		
//		this.changed.getProblems().forEach(pChanged -> {
//			eChanged = pChanged.get
//		});
		
		return this;
	}

	private boolean isMissingEqual(ITracedATLModel trace, Object missingOriginal, Object missingChanged) {
		if ( missingOriginal == null && missingChanged == null ) 
			return true;
		if ( missingOriginal == null && missingChanged != null )
			return false;
		if ( missingOriginal != null && missingChanged == null )
			return false;
		
		if ( missingOriginal instanceof EObject) {
			// This happens typically (for no binding for compulsory feature) when meta-models are not copied
			if ( missingOriginal == missingChanged )
				return true;
			
			// This is the situation when meta-models are copied, we need to look into the trace
			EObject tgt = trace.getTarget((EObject) missingOriginal);
			return tgt == missingChanged;
		}
		
		// If they are primitive types or any other object
		return missingOriginal.equals(missingChanged);
	}

	private List<RuleConflict> getRuleConflicts(AnalysisResult r) {
		return r.getProblems().stream().
			filter(p -> p instanceof RuleConflict ).
			map(p -> (RuleConflict) p).collect(Collectors.toList());
	}

	protected List<LocalProblem> possibleLocalProblems(AnalysisResult r) {
//		return r.getLocalProblems().stream().filter(p -> AnalyserUtils.isConfirmed(p)).collect(Collectors.toList());
		return r.getPossibleProblems().stream().
				filter(p -> p instanceof LocalProblem).
				map(p -> (LocalProblem) p).
				collect(Collectors.toList());
	}

	protected List<LocalProblem> confirmedLocalProblems(AnalysisResult r) {
//		return r.getLocalProblems().stream().filter(p -> AnalyserUtils.isConfirmed(p)).collect(Collectors.toList());
		return r.getProblems().stream().
				filter(p -> AnalyserUtils.isConfirmed(p)).
				filter(p -> p instanceof LocalProblem).
				map(p -> (LocalProblem) p).
				collect(Collectors.toList());
	}

	
	public boolean isFixed(Problem p) {
		return fixedProblems.contains(p);
	}
	
	public Collection<Problem> getNewProblems() {
		return newProblems;
	}
	
	public Collection<Problem> getMayBeFixedProblems() {
		return mayBeFixedProblems;
	}
	
	public Collection<Problem> getMayBeNewProblems() {
		return mayBeNewProblems;
	}
	
	public Collection<Problem> getFixedProblems() {
		return fixedProblems;
	}
}
