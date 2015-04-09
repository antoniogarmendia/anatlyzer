package anatlyzer.experiments.typing;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;

import anatlyzer.atl.analyser.AnalysisResult;
import anatlyzer.atl.editor.Activator;
import anatlyzer.atl.editor.builder.AnATLyzerBuilder;
import anatlyzer.atl.editor.builder.AnalyserExecutor.AnalyserData;
import anatlyzer.atl.editor.quickfix.AtlProblemQuickfix;
import anatlyzer.atl.editor.quickfix.AtlProblemQuickfixSet;
import anatlyzer.atl.editor.quickfix.ConstraintSolvingQuickFix;
import anatlyzer.atl.editor.quickfix.TransformationSliceQuickFix;
import anatlyzer.atl.errors.Problem;
import anatlyzer.experiments.export.CountingModel;
import anatlyzer.experiments.extensions.IExperiment;
import anatlyzer.experiments.typing.CountTypeErrors.DetectedError;

public class ApplyQuickfixes extends AbstractATLExperiment implements IExperiment {

	List<AnalyserData> allData = new ArrayList<AnalyserData>();
	private CountingModel<DetectedError> counting = new CountingModel<DetectedError>();

	
	public ApplyQuickfixes() {
		counting.setRepetitions(true);
		counting.showRepetitionDetails(false);
	}

	@Override
	public void perform(IResource resource) {
		AnalyserData data;
		try {
			data = executeAnalyser(resource);
			if ( data == null )
				return;

			allData.add(data);
			
			String fileName = resource.getName();
			counting.processingArtefact(fileName);
			
			System.out.println();
			System.out.println();
			System.out.println("==== QUICKFIXES ====");
			List<Problem> allProblems = data.getProblems();
			for (Problem p : allProblems) {
				System.out.println(p.getDescription());
				
				List<AtlProblemQuickfix> quickfixes = getQuickfixes(p, data);
				for (AtlProblemQuickfix atlProblemQuickfix : quickfixes) {
					System.out.println(" * " + atlProblemQuickfix.getDisplayString());
				}
				
				
			}
			
			
			/*
			String fileName = resource.getName();
			counting.processingArtefact(fileName);
			
			for(Problem p : data.getProblems()) {
				String errorCode = AnalyserUtils.getProblemId(p) + "";
				DetectedError e = new DetectedError(errorCode, fileName);
				counting.addByCategory(errorCode, e);
			}
			*/
		} catch (Exception e) {
			counting.addError(resource.getName(), e);
			e.printStackTrace();
		} 
	}

	@Override
	public void printResult(PrintStream out) {
		
	}

	@Override
	public boolean canExportToExcel() {
		return false;
	}

	@Override
	public void exportToExcel(String file) throws IOException {
		// counting.toExcel(file);
	}

	
	// Similar to the method in the editor...
	public static List<AtlProblemQuickfix> getQuickfixes(Problem p, AnalysisResult r) {
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		IConfigurationElement[] extensions = registry.getConfigurationElementsFor(Activator.ATL_QUICKFIX_EXTENSION_POINT);
		ArrayList<AtlProblemQuickfix> quickfixes = new ArrayList<AtlProblemQuickfix>();
		
		MockMarker iMarker = new MockMarker(p, r);
		
		for (IConfigurationElement ce : extensions) {
			try {
				if ( ce.getName().equals("quickfix") ) {
					AtlProblemQuickfix qf = (AtlProblemQuickfix) ce.createExecutableExtension("apply");
					if ( qf.isApplicable(iMarker) && ! discardQuickfix(qf) ) {
						qf.setErrorMarker(iMarker);
						quickfixes.add(qf);
					}
				} 
				else if ( ce.getName().equals("quickfixset") ) {
					AtlProblemQuickfixSet detector = (AtlProblemQuickfixSet) ce.createExecutableExtension("detector");
					if ( detector.isApplicable(iMarker) ) {
						for(AtlProblemQuickfix q : detector.getQuickfixes(iMarker) ) {
							if ( ! q.isApplicable(iMarker) ) {
								throw new IllegalStateException();
							}
							
							if ( discardQuickfix(q) )
								continue;
							
							q.setErrorMarker(iMarker);
							quickfixes.add(q);							
						}
					}
				}
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}

		return quickfixes;
	}

	private static boolean discardQuickfix(AtlProblemQuickfix q) {
		return 	q instanceof ConstraintSolvingQuickFix || 
				q instanceof TransformationSliceQuickFix;
	}

	public static class MockMarker implements IMarker {

		private Problem problem;
		private HashMap<String, Object> attributes;

		public MockMarker(Problem p, AnalysisResult data) {
			this.problem = p;
			this.attributes = new HashMap<String, Object>();

			try {
				this.setAttribute(AnATLyzerBuilder.PROBLEM, problem);
				this.setAttribute(AnATLyzerBuilder.ANALYSIS_DATA, data);
				this.setAttribute(IMarker.MESSAGE, p.getDescription());
			} catch (CoreException e) {
				e.printStackTrace();
			}
			
			// pbmMarker.setAttribute(IMarker.SEVERITY, severity); 
			// pbmMarker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			// pbmMarker.setAttribute(IMarker.LOCATION, Messages.getString("MarkerMaker.LINECOLUMN", //$NON-NLS-1$
			//		new Object[] {new Integer(lineNumber), new Integer(columnNumber)}));
			// pbmMarker.setAttribute(IMarker.CHAR_START, charStart);
			// pbmMarker.setAttribute(IMarker.CHAR_END, (charEnd > charStart) ? charEnd : charStart + 1);			
		}
		
		@Override
		public Object getAdapter(Class adapter) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void delete() throws CoreException {
			throw new UnsupportedOperationException();			
		}

		@Override
		public boolean exists() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object getAttribute(String attributeName) throws CoreException {
			Object obj = getAttributes().get(attributeName);
			if ( obj == null )
				throw new IllegalArgumentException("No attribute: " + attributeName);
			return obj;
		}

		@Override
		public int getAttribute(String attributeName, int defaultValue) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getAttribute(String attributeName, String defaultValue) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean getAttribute(String attributeName, boolean defaultValue) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<String, Object> getAttributes() throws CoreException {
			return attributes;
		}

		@Override
		public Object[] getAttributes(String[] attributeNames) throws CoreException {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getCreationTime() throws CoreException {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getId() {
			throw new UnsupportedOperationException();
		}

		@Override
		public IResource getResource() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getType() throws CoreException {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isSubtypeOf(String superType) throws CoreException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setAttribute(String attributeName, int value) throws CoreException {
			this.attributes.put(attributeName, value);
		}

		@Override
		public void setAttribute(String attributeName, Object value) throws CoreException {
			this.attributes.put(attributeName, value);
		}

		@Override
		public void setAttribute(String attributeName, boolean value) throws CoreException {
			this.attributes.put(attributeName, value);
		}

		@Override
		public void setAttributes(String[] attributeNames, Object[] values) throws CoreException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setAttributes(Map<String, ? extends Object> attributes) throws CoreException {
			throw new UnsupportedOperationException();
		}
		
	}
}
