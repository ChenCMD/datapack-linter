import { DiagnosticSeverity } from 'vscode-json-languageservice';

export interface Output {
    severity: DiagnosticSeverity
    message: string
}