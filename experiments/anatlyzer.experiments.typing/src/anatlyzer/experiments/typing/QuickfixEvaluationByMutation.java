package anatlyzer.experiments.typing;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.m2m.atl.core.ATLCoreException;
import org.eclipse.swt.widgets.Display;

import transML.exceptions.transException;
import anatlyzer.evaluation.tester.Tester;


public class QuickfixEvaluationByMutation extends QuickfixEvaluationAbstract {
	
	

	public QuickfixEvaluationByMutation() {
		compactNotClassified = false;
		
		performRuleAnalysis = true;
		
		optimizeWithProblemTracking = true;
	}

	// Here to allow its modification by the UI thread...
	boolean generateMutants = true;
	
	
	@Override
	public void perform(IResource resource, IProgressMonitor monitor) {
		recordAll = true;
		
		String trafoName = resource.getLocation().removeFileExtension().lastSegment() + "_";
		IFolder baseFolder = resource.getProject().getFolder(trafoName);
		IFolder folder = resource.getProject().getFolder(trafoName + "mutants");
		
		try {
			generateMutants = true;
			if ( folder.exists() ) {
				
				Display.getDefault().syncExec(new Runnable() {					
					@Override
					public void run() {
						generateMutants = ! MessageDialog.openQuestion(null, "Folder already exists", "Mutants already generated. Proceed without overwriting?");											
					}
				});
				
				if ( generateMutants ) {
					folder.delete(true, monitor);
				}
			}

			if ( generateMutants ) {
				anatlyzer.evaluation.tester.Tester tester = new Tester(
							resource.getLocation().toPortableString(),
							baseFolder.getLocation().toPortableString()
						);
				tester.generateMutants();
			}
		} catch (ATLCoreException | transException | CoreException e) {
			e.printStackTrace();
			return;
		}
		
		// Get the actual folder generated by Tester
		final List<IFile> allFiles = new ArrayList<IFile>();
		try {
			folder.refreshLocal(1, monitor);
			folder.accept(new IResourceVisitor() {			
				@Override
				public boolean visit(IResource resource) throws CoreException {
					if ( resource instanceof IFile && resource.getFileExtension().equals("atl") ) {
						allFiles.add((IFile) resource);
					}
					return true;
				}
			});
		} catch (CoreException e) {
			e.printStackTrace();
			return;
		}
		
		projects.put("default", new Project("default"));
		projects.put("ArgumentTypeModification", new Project("ArgumentTypeModification"));
		projects.put("Binding-targetModification", new Project("Binding-targetModification"));
		projects.put("CollectionOperationCallModification", new Project("CollectionOperationCallModification"));
		projects.put("CollectionTypeModification", new Project("CollectionTypeModification"));
		projects.put("CreationofBinding", new Project("CreationofBinding"));
		projects.put("DeletionofBinding", new Project("DeletionofBinding"));            // New with respect MoDELS'15 (?)
		projects.put("DeletionofArgument", new Project("DeletionofArgument"));
		projects.put("DeletionofFilter", new Project("DeletionofFilter"));
		projects.put("DeletionofRule", new Project("DeletionofRule"));  	  	        // New with respect MoDELS'15 (?)
		projects.put("InPatternElementModification", new Project("InPatternElementModification"));
		projects.put("IteratorModification", new Project("IteratorModification")); 		// New with respect MoDELS'15 (?)
		projects.put("NavigationModification", new Project("NavigationModification"));
		projects.put("OperatorModification", new Project("OperatorModification"));
		projects.put("OperationCallModification", new Project("OperationCallModification"));
		projects.put("OutPatternElementModification", new Project("OutPatternElementModification"));

		// New(!) with respect MoDELS'15 
		projects.put("PrimitiveValueModification", new Project("PrimitiveValueModification"));
		projects.put("ModificationofParentRule", new Project("ModificationofParentRule"));
		projects.put("ModificationofParentRule", new Project("ModificationofParentRule"));
		projects.put("HelperReturnTypeModification", new Project("HelperReturnTypeModification"));
		projects.put("HelperContextTypeModification", new Project("HelperContextTypeModification"));
		projects.put("HelperCallModification", new Project("HelperCallModification"));
		projects.put("DeletionofOutPatternElement", new Project("DeletionofOutPatternElement"));
		projects.put("DeletionofHelper", new Project("DeletionofHelper"));
		projects.put("DeletionofContext", new Project("DeletionofContext"));
		projects.put("CreationofOutPatternElement", new Project("CreationofOutPatternElement"));
		projects.put("CreationofInPatternElement", new Project("CreationofInPatternElement"));

		for (IFile aFile : allFiles) {
			if ( monitor.isCanceled() )
				return;
			
			String fname = aFile.getFullPath().lastSegment();
			String mutationTypeName = fname.replaceAll("_mutant.*atl$", "");
			Pattern pattern = Pattern.compile(mutationTypeName + "_mutant(.*).atl");
			Matcher matcher = pattern.matcher(fname);
			if ( ! matcher.matches() ) {
				System.out.println("No match for " + fname);
				continue;
			}
			int numMutation = Integer.parseInt(matcher.group(1));
						
			Project p = projects.get(mutationTypeName);
			if ( p == null ) {
				p = projects.get("default");
				mutationTypeName = "default";
			} 
			
			
			int maxMutations = Integer.parseInt((String) options.getOrDefault(mutationTypeName, Integer.MAX_VALUE + ""));
			
			if ( numMutation > maxMutations ) 
				continue;			
			
			evaluateQuickfixesOfFile(aFile, p, monitor);
		}
		
		
		//anatlyzer.evaluation.tester.Tester tester = new Tester(path-transformacion, path-salida-mutantes);
		//tester.generateMutants(); 
	}
	
	@Override
	public void printResult(PrintStream out) {
		List<AnalysedTransformation> trafos = projects.values().stream().flatMap(p -> p.getTrafos().stream()).collect(Collectors.toList());
		
		out.println("Total mutants: " + trafos.size());
		out.println(" Analyser error: " + counting.getErrors().size());
		out.println(" Without errors:" + trafos.stream().filter(t -> t.getOriginalProblems().isEmpty()).count() );	
		out.println(" Mutants analysed and with at least one error: " + trafos.stream().filter(t -> ! t.getOriginalProblems().isEmpty()).count() );
		
		super.printResult(out);
	}
	
}
