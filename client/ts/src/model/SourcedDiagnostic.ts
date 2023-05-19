import { Diagnostic } from '../protocol';


interface SourcedDiagnostic extends Diagnostic {
  source?: string; // Human readable string describing the source of this diagnostic
}
export default SourcedDiagnostic;
