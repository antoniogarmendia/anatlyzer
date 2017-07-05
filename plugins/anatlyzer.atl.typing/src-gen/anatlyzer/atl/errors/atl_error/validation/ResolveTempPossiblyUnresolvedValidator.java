/**
 *
 * $Id$
 */
package anatlyzer.atl.errors.atl_error.validation;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

/**
 * A sample validator interface for {@link anatlyzer.atl.errors.atl_error.ResolveTempPossiblyUnresolved}.
 * This doesn't really do anything, and it's not a real EMF artifact.
 * It was generated by the org.eclipse.emf.examples.generator.validator plug-in to illustrate how EMF's code generator can be extended.
 * This can be disabled with -vmargs -Dorg.eclipse.emf.examples.generator.validator=false.
 */
public interface ResolveTempPossiblyUnresolvedValidator {
	boolean validate();

	boolean validateProblematicClasses(EList<EClass> value);
	boolean validateProblematicClassesImplicit(EList<EClass> value);
	boolean validateResolvedExpression(EObject value);
}
