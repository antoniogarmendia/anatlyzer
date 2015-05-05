package anatlyzer.atl.editor.quickfix.errors;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;

import anatlyzer.atl.analyser.AnalysisResult;
import anatlyzer.atl.errors.atl_error.BindingProblem;
import anatlyzer.atl.errors.atl_error.BindingWithResolvedByIncompatibleRule;
import anatlyzer.atl.quickfixast.QuickfixApplication;
import anatlyzer.atlext.ATL.MatchedRule;

public class BindingInvalidTargetInResolvedRule_RemoveRule extends BindingInvalidTargetInResolvedRule_Abstract {

	@Override
	public boolean isApplicable(IMarker marker) throws CoreException {
		return checkProblemType(marker, BindingWithResolvedByIncompatibleRule.class)
				&& isOptionalFeature((BindingProblem) getProblem(marker));
	}
	
	@Override
	public QuickfixApplication getQuickfixApplication() throws CoreException {
		BindingWithResolvedByIncompatibleRule p = (BindingWithResolvedByIncompatibleRule) getProblem();
		AnalysisResult analysis = getAnalysisResult();
		
		List<MatchedRule> guiltyRules = detectGuiltyRules(p, analysis);

		QuickfixApplication qfa = new QuickfixApplication();
		for (MatchedRule matchedRule : guiltyRules) {
			qfa.remove(matchedRule);
		}
		return qfa;
	}
	
	@Override
	public String getDisplayString() {
		return "Remove binding";
	}
	

}
