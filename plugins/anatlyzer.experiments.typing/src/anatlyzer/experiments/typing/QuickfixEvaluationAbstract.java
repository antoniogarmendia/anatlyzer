package anatlyzer.experiments.typing;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;

import anatlyzer.atl.analyser.AnalysisResult;
import anatlyzer.atl.editor.Activator;
import anatlyzer.atl.editor.builder.AnalyserExecutor.AnalyserData;
import anatlyzer.atl.editor.quickfix.AbstractAtlQuickfix;
import anatlyzer.atl.editor.quickfix.AtlProblemQuickfix;
import anatlyzer.atl.editor.quickfix.AtlProblemQuickfixSet;
import anatlyzer.atl.editor.quickfix.ConstraintSolvingQuickFix;
import anatlyzer.atl.editor.quickfix.MockMarker;
import anatlyzer.atl.editor.quickfix.TransformationSliceQuickFix;
import anatlyzer.atl.editor.witness.EclipseUseWitnessFinder;
import anatlyzer.atl.errors.Problem;
import anatlyzer.atl.errors.atl_error.BindingPossiblyUnresolved;
import anatlyzer.atl.errors.atl_error.BindingWithResolvedByIncompatibleRule;
import anatlyzer.atl.errors.atl_error.InvalidOperand;
import anatlyzer.atl.errors.atl_error.LocalProblem;
import anatlyzer.atl.quickfixast.QuickfixApplication;
import anatlyzer.atl.util.ATLSerializer;
import anatlyzer.atl.util.AnalyserUtils;
import anatlyzer.atl.witness.IWitnessFinder.WitnessResult;
import anatlyzer.experiments.export.CountingModel;
import anatlyzer.experiments.export.Styler;
import anatlyzer.experiments.extensions.IExperiment;
import anatlyzer.experiments.typing.CountTypeErrors.DetectedError;

public class QuickfixEvaluationAbstract extends AbstractATLExperiment implements IExperiment {

	protected List<AnalyserData> allData = new ArrayList<AnalyserData>();
	protected CountingModel<DetectedError> counting = new CountingModel<DetectedError>();

	protected boolean recordAll = false;
	protected boolean useCSP    = true;
	protected Workbook workbook = new XSSFWorkbook();

	class QuickfixSummary {
		int id;
		int maxQuickfixes = 0;
		int minQuickfixes = Integer.MAX_VALUE;
		int totalQuickfixes;
		int totalProblems;
		private String description;

		public QuickfixSummary(int problemId, String description) {
			this.id = problemId;
			this.description = description;
		}
		
		public void appliedQuickfixes(int count) {
			if ( count < minQuickfixes )  {
				minQuickfixes = count;
			}
			if ( count > maxQuickfixes ) {
				maxQuickfixes = count;
			}
			totalQuickfixes += count;
			totalProblems++;
		}


		private double getAvg() {
			return totalQuickfixes / (1.0 * totalProblems);
		}

		public String toLatexRow() {
			return description + " & " + totalProblems + " & " + totalQuickfixes + " & " + getAvg() + " & " + minQuickfixes + " & " + maxQuickfixes + "\\\\ \\hline" ;
		}
		
		@Override
		public String toString() {
			return id + ": \n" + 
					"\t" + "min: " + minQuickfixes + "\n" +
					"\t" + "max: " + maxQuickfixes + "\n" +					
					"\t" + "avg:" + totalQuickfixes / (1.0 * totalProblems) + "\n" +
					"\t" + "pro:" + totalProblems + "\n" +
					"\t" + "qfx:" + totalQuickfixes + "\n";
		}

	}
	
	private HashMap<Integer, QuickfixSummary> summary = new HashMap<Integer, QuickfixEvaluationAbstract.QuickfixSummary>();
	
	
	class AppliedQuickfixInfo {

		private AtlProblemQuickfix quickfix;
		private AnalysisResult original;
		private AnalysisResult newResult;
		private boolean notSupported;
		private boolean implError;
		public String description = "no-description";

		public AppliedQuickfixInfo(AtlProblemQuickfix quickfix, AnalysisResult original, List<Problem> originalProblems) {
			// TODO Auto-generated constructor stub
			this.quickfix = quickfix;
			this.original = original;
			this.originalProblems = new ArrayList<Problem>(originalProblems);
		}

		public void setRetyped(AnalysisResult newResult, List<Problem> retypedProblems) {
			this.newResult = newResult;
			this.retypedProblems = new ArrayList<Problem>(retypedProblems);
		}
		
		public AnalysisResult getRetyped() {
			if ( newResult == null )
				throw new IllegalStateException();
			return newResult;
		}

		public void setNotSupported() {
			this.notSupported = true;
		}
		
		public boolean isNotSupported() {
			return notSupported;
		}

		public AnalysisResult getOriginal() {
			return original;
		}

		public void setImplError() {
			this.implError = true;
		}
		
		public boolean isImplError() {
			return implError;
		}

		public void setDescription(String displayString) {
			this.description = displayString;
		}

		
		List<Problem> originalProblems;
		List<Problem> retypedProblems;
		
		
		public List<Problem> getRetypedProblems() {
			return retypedProblems;
		}

		public List<Problem> getOriginalProblems() {
			return originalProblems;
		}
		
	}
	
	class AnalysedTransformation {
		private IResource r;
		private HashMap<Problem, List<AppliedQuickfixInfo>> problemToQuickfix = new HashMap<>();
		private Problem currentProblem;
		private AnalysisResult original;
		private List<Problem> originalProblems;

		public AnalysedTransformation(IResource resource, AnalysisResult original, List<Problem> originalProblems) {
			this.r = resource;
			this.original = original;
			this.originalProblems = originalProblems;
		}
		
		public void currentProblem(Problem p) {
			ArrayList<AppliedQuickfixInfo> applications = new ArrayList<QuickfixEvaluationAbstract.AppliedQuickfixInfo>();
			problemToQuickfix.put(p, applications);
			this.currentProblem = p;
		}

		public void appliedQuickfix(AppliedQuickfixInfo qi) {
			problemToQuickfix.get(currentProblem).add(qi);
		}
		
		public List<Problem> getOriginalProblems() {
			return originalProblems;
		}

		public List<AppliedQuickfixInfo> getQuickfixes(Problem p) {
			List<AppliedQuickfixInfo> l = problemToQuickfix.get(p);
			if ( l == null )
				l = new ArrayList<QuickfixEvaluationAbstract.AppliedQuickfixInfo>();
			return l;
		}
	}
	
	class Project {
		private List<AnalysedTransformation> trafos = new ArrayList<QuickfixEvaluationAbstract.AnalysedTransformation>();
		private String name;

		public Project(String name) {
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
		
		public List<AnalysedTransformation> getTrafos() {
			return trafos;
		}
		
	}
	
	
	// Just for test purposes
	protected List<String> messages = new ArrayList<String>();
	protected HashMap<String, Project> projects = new HashMap<String, QuickfixEvaluationAbstract.Project>();
	
	public QuickfixEvaluationAbstract() {
		counting.setRepetitions(true);
		counting.showRepetitionDetails(false);
	}

	private static int id = 0;

	@Override
	public void projectDone(IProject project) {
		// Detect that we are in a new project, so dump the previous
		// and free memory
		if ( ! recordAll && projects.size() == 1 ) {
			Project p = projects.get(project.getName());
			createDetail(workbook, p);
			
			projects.clear(); // free memory
		}
	}
	
	@Override
	protected void perform(IResource resource) {
		perform(resource, null);
	}

	@Override
	public void perform(IResource resource, IProgressMonitor monitor) {
		String projectName = resource.getProject().getName();
		if ( ! projects.containsKey(projectName) ) {
			projects.put(projectName, new Project(projectName));
		}
		Project project = projects.get(projectName);
		
		evaluateQuickfixesOfFile(resource, project, monitor); 
	}

	protected void evaluateQuickfixesOfFile(IResource resource, Project project, IProgressMonitor monitor) {
		AnalyserData data;
		try {
			data = executeAnalyser(resource);
			if ( data == null )
				return;
			
			allData.add(data);
			
			String fileName = resource.getName();
			counting.processingArtefact(fileName);
			
			List<Problem> allProblems = selectProblems(data.getProblems(), data);
			AnalysedTransformation trafo = new AnalysedTransformation(resource, data, allProblems);
			project.trafos.add(trafo);

			monitor.beginTask("Processing problems.", allProblems.size());
			
			int i = 0;
			for (Problem p : allProblems) {
				if ( monitor.isCanceled() ) {
					return;
				}
				
				// Get summary and initialize if needed
				QuickfixSummary qs = summary.get(AnalyserUtils.getProblemId(p));
				if ( qs == null ) {
					qs = new QuickfixSummary(AnalyserUtils.getProblemId(p), AnalyserUtils.getProblemDescription(p));
					summary.put(AnalyserUtils.getProblemId(p), qs);										
				}

				
				i++;
				monitor.subTask("Problem " + "(" + i + "/" + allProblems.size() + "): " + p.getDescription());
				
				printMessage("\n");
				printMessage("[" + ((LocalProblem) p).getLocation() + "]" + p.getDescription());
				
				trafo.currentProblem(p);
			
				List<AtlProblemQuickfix> quickfixes = getQuickfixes(p, data);
				
				if ( quickfixes.size() > 0 ) { 
					// Printing
					printMessage("Available quickfixes:");
					for (AtlProblemQuickfix atlProblemQuickfix : quickfixes) {
						try { 
							printMessage(" * " + atlProblemQuickfix.getDisplayString());
						} catch (Exception e) {
							printMessage(" * Error in 'displayString' method");
							e.printStackTrace();
						}
					}
		
					// Recording					
					int appliedQuickfixesCount = 0;
					for (AtlProblemQuickfix quickfix : quickfixes) {
						if ( monitor != null && monitor.isCanceled() )
							return;
						
						
						AppliedQuickfixInfo qi = applyQuickfix(quickfix, resource, p, data, allProblems);
						trafo.appliedQuickfix(qi);
						appliedQuickfixesCount++;
					}
										
					// Add to summary
					qs.appliedQuickfixes(appliedQuickfixesCount);					
				} else {
					qs.appliedQuickfixes(0);					
					printMessage(" - No quickfixes available");
				}
				
				monitor.worked(1);
			}
			
			monitor.done();
		} catch (Exception e) {
			printMessage("Error: " + e.getMessage());
			counting.addError(resource.getName(), e);
			e.printStackTrace();
		}
	}

	private List<Problem> selectProblems(List<Problem> problems, AnalysisResult r) {
		ArrayList<Problem> allProblems = new ArrayList<Problem>();
		for (Problem p : problems) {
			if ( useCSP && requireCSP(p) ) {
				//WitnessResult result = new StandaloneWitnessFinder().
				//		checkDiscardCause(false).find(p, r);
				WitnessResult result = new EclipseUseWitnessFinder().
						checkProblemsInPath(true).
						checkDiscardCause(false).find(p, r);
				
				switch (result) {
				case ERROR_CONFIRMED:
				case ERROR_CONFIRMED_SPECULATIVE:
					// that's fine						
					printMessage("Confirmed: " + ((LocalProblem) p).getLocation());
					allProblems.add(p);
					break;
				case ERROR_DISCARDED:
				case ERROR_DISCARDED_DUE_TO_METAMODEL:
					printMessage("Discarded: " + ((LocalProblem) p).getLocation());
					continue;
				case CANNOT_DETERMINE:
				case INTERNAL_ERROR:
				case NOT_SUPPORTED_BY_USE:
					printMessage("Error: " + ((LocalProblem) p).getLocation() + ", " + ((LocalProblem) p).getFileLocation());
					continue;
				case PROBLEMS_IN_PATH:
					printMessage("Problems in path for: " + ((LocalProblem) p).getLocation() + ", " + ((LocalProblem) p).getFileLocation());
					continue;					
				}
			} else {
				allProblems.add(p);
			}
		}
		return allProblems;
	}

	private boolean requireCSP(Problem p) {
		return 	p instanceof BindingPossiblyUnresolved ||
				p instanceof BindingWithResolvedByIncompatibleRule
		;
	}

	private AppliedQuickfixInfo applyQuickfix(AtlProblemQuickfix quickfix, IResource resource, Problem p, AnalyserData original, List<Problem> originalProblems) throws IOException, CoreException, Exception {
		// Re-execute the analyser to have a fresh copy to apply the quickfix, and then retype
		AnalyserData newResult = executeAnalyser(resource);
		if ( newResult.getProblems().size() != original.getProblems().size() ) {
			throw new IllegalStateException();
		}
		
		int idx = original.getProblems().indexOf(p);					
		LocalProblem newProblem = (LocalProblem) newResult.getProblems().get(idx);
		if ( ! newProblem.getLocation().equals(((LocalProblem) p).getLocation()) ) {
			throw new IllegalStateException("This should not happen");
		}
		
		quickfix.setErrorMarker(new MockMarker(newProblem, newResult));

		
		AppliedQuickfixInfo qi = new AppliedQuickfixInfo(quickfix, original, originalProblems);
		try { 
			// getDisplayString() may have implementation errors, so it needs to be called
			//                    within the try-catch
			String displayString = quickfix.getDisplayString();
			printMessage("Applying quickfix: " + displayString);
			
			qi.setDescription(displayString);
			QuickfixApplication qfa = ((AbstractAtlQuickfix) quickfix).getQuickfixApplication();
			qfa.apply();

			// Serialize, just for testing purposes
			IFolder temp = experimentFile.getProject().getFolder("temp");
			if ( ! temp.exists() ) {
				temp.create(true, true, null);
			}
			
			IFile f = temp.getFile(resource.getName().replace(".atl", "") + (++id) + ".atl");
			
			ATLSerializer.serialize(newResult.getAnalyser().getATLModel(), f.getLocation().toPortableString());
			printMessage("Generated quickfixed file" + f.getName());
			f.refreshLocal(1, null);
			
			// Retype
			newResult = executeAnalyser(f);

			/*
			newResult.getAnalyser().getATLModel().clear();
			newResult.getProblems().size();
			newResult = executeAnalyser(resource, newResult.getAnalyser().getATLModel());
			if ( newResult == null ) {
				throw new IllegalStateException();
			}
			*/
			
			List<Problem> newProblems = selectProblems(newResult.getProblems(), newResult);
			qi.setRetyped(newResult, newProblems);
			
		} catch ( UnsupportedOperationException e ) {
			printMessage("Quickfix not implemented at the AST Level");
			qi.setNotSupported();
		} catch ( Exception e ) {
			qi.setImplError();
		}
		
		return qi;
	}

	private void printMessage(String msg) {
		System.out.println(msg);
		messages.add(msg);
	}

	public void printLatexTable(PrintStream out) {
		out.println("\\begin{table}[h]");
		out.println("\\begin{tabular}{|p{2.5cm}|l|l|l|l|l|}");
		out.println("\\hline");
		out.println("Problem        & \\#Prob. & \\#Qfixes & Avg & Min & Max \\\\ \\hline");

		summary.values().forEach(qs -> {
			out.println(qs.toLatexRow());
		});
		
		out.println("\\end{tabular}");
		out.println("\\end{table}");		
	}
	
	@Override
	public void printResult(PrintStream out) {
		printLatexTable(out);
		
		summary.values().forEach(qs -> {
			out.println(qs);
		});
		
		
		for (String str : messages) {
			out.println(str);
		}
	}

	@Override
	public boolean canExportToExcel() {
		return true;
	}

	@Override
	public void exportToExcel(String fileName) throws IOException {
		Workbook wb = null;
		if ( recordAll || projects.size() > 0) {		
			wb = new XSSFWorkbook();
			
			for (Project p : projects.values()) {
				createDetail(wb, p);			
			}
		} else {
			wb = workbook;
		}
		
		FileOutputStream fileOut = new FileOutputStream(fileName);
		wb.write(fileOut);
		wb.close();
		fileOut.close();          
	}

	
	protected void createDetail(Workbook wb, Project project) {
		Sheet s = wb.createSheet(project.getName());
		List<AnalysedTransformation> trafos = project.trafos;
		
		Styler st = new Styler(wb);

		int startCol = 1;
		int starRow  = 1;
		
		int row = starRow;
		
		for (AnalysedTransformation t : trafos) {
			row++;
			
			st.cell(s, row, startCol + 0, t.r.getName()).centeringBold();
			st.createCell(s, row, startCol + 1, (long) t.getOriginalProblems().size());
			row++;
			
			st.cell(s, row, startCol + 1, "Id.").centeringBold();
			st.cell(s, row, startCol + 2, "Description.").centeringBold().charsWidth(50);
			st.cell(s, row, startCol + 3, "Quickfixes").centeringBold();
			st.cell(s, row, startCol + 4, "Fixed").centeringBold();
			st.cell(s, row, startCol + 5, "Total").centeringBold();
			
			row++;
			
			
			// for (Problem p : t.problemToQuickfix.keySet()) {
			for (Problem p : t.getOriginalProblems()) {			
				st.createCell(s, row, startCol + 1, (long) AnalyserUtils.getProblemId(p));
				st.createCell(s, row, startCol + 2, p.getDescription() + " at " + ((LocalProblem) p).getLocation());
				
				List<AppliedQuickfixInfo> quickfixes = t.getQuickfixes(p);
				if ( quickfixes.isEmpty() ) {
					st.createCell(s, row, startCol + 3, 0L);
				} else {
					st.createCell(s, row, startCol + 3, (long) quickfixes.size());	
				}
				row++;
				for(AppliedQuickfixInfo qi : quickfixes) {
					st.createCell(s, row, startCol + 3, qi.description);
					if ( qi.isNotSupported() ) {
						st.cell(s, row, startCol + 4, (long) 0).background(HSSFColor.DARK_RED.index);
						st.cell(s, row, startCol + 6, "No AST");							
					} else if ( qi.isImplError() ) {
						st.cell(s, row, startCol + 4, (long) 0).background(HSSFColor.RED.index);	
						st.cell(s, row, startCol + 6, "Impl. Error");							
					} else {
						long newProblems = qi.getRetypedProblems().size();
						long oldProblems = qi.getOriginalProblems().size(); 
						st.createCell(s, row, startCol + 4, (long) oldProblems - newProblems);											
						st.createCell(s, row, startCol + 5, (long) newProblems);											
					}
					row++;
				}				
			}
		}
		
	
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
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return quickfixes;
	}

	private static boolean discardQuickfix(AtlProblemQuickfix q) {
		return 	q instanceof ConstraintSolvingQuickFix || 
				q instanceof TransformationSliceQuickFix;
	}

}