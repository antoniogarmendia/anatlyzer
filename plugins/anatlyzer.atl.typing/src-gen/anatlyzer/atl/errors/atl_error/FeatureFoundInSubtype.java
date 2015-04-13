/**
 */
package anatlyzer.atl.errors.atl_error;

import org.eclipse.emf.common.util.EList;

import org.eclipse.emf.ecore.EClass;

/**
 * <!-- begin-user-doc -->
 * A representation of the model object '<em><b>Feature Found In Subtype</b></em>'.
 * <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 *   <li>{@link anatlyzer.atl.errors.atl_error.FeatureFoundInSubtype#getPossibleClasses <em>Possible Classes</em>}</li>
 * </ul>
 *
 * @see anatlyzer.atl.errors.atl_error.AtlErrorPackage#getFeatureFoundInSubtype()
 * @model annotation="description name='Feature found in subtype' text='Feature cannot be found in an object\'s class, but found in subtype. The error may not happen depending on the program logic.'"
 *        annotation="info prec='sometimes-solver' path='yes' severity='runtime-error' when='model-dep' kind='src-typing' phase='typing' source='none'"
 * @generated
 */
public interface FeatureFoundInSubtype extends FeatureNotFound, RuntimeError {
	/**
	 * Returns the value of the '<em><b>Possible Classes</b></em>' reference list.
	 * The list contents are of type {@link org.eclipse.emf.ecore.EClass}.
	 * <!-- begin-user-doc -->
	 * <p>
	 * If the meaning of the '<em>Possible Classes</em>' reference list isn't clear,
	 * there really should be more of a description here...
	 * </p>
	 * <!-- end-user-doc -->
	 * @return the value of the '<em>Possible Classes</em>' reference list.
	 * @see anatlyzer.atl.errors.atl_error.AtlErrorPackage#getFeatureFoundInSubtype_PossibleClasses()
	 * @model
	 * @generated
	 */
	EList<EClass> getPossibleClasses();

} // FeatureFoundInSubtype
