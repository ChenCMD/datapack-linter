import { DatapackDocument } from '@spgoding/datapack-language-server/lib/types';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { TextDocument, DiagnosticSeverity, Diagnostic } from 'vscode-json-languageservice';
import * as core from '@actions/core';
import path from 'path';
import { FailCount } from '../types/Results';

/**
 * Create and return message data from DatapackDocument.
 */
export function printParseResult(parsedData: DatapackDocument, id: IdentityNode, doc: TextDocument, root: string, rel: string): FailCount {
    const title = `${id} (${path.parse(root).name}/${rel.replace(/\\/g, '/')})`;
    const failCount = { warning: 0, error: 0 };
    let isErrorFound = false;

    for (const node of parsedData?.nodes ?? []) {
        if (node.errors.filter(err => err.severity < 3).length === 0) // Success
            continue;
        // Failed
        if (!isErrorFound) {
            core.info(`\u001b[91m✗\u001b[39m  ${title}`);
            isErrorFound = true;
        }
        node.errors
            .filter(err => err.severity < 3)
            .map(err => err.toDiagnostic(doc))
            .sort((errA, errB) => errA.range.start.line - errB.range.start.line)
            .forEach(err => printErrorSingle(failCount, err));
    }
    if (!isErrorFound)
        core.info(`\u001b[92m✓\u001b[39m  ${id} (${path.parse(root).name}/${rel.replace(/\\/g, '/')})`);
    return failCount;
}

export function printErrorSingle(failCount: FailCount, error: Diagnostic): void {
    const pos = error.range.start;
    const paddingedLine = `   ${pos.line + 1}`.slice(-4);
    const paddingedChar = `${pos.character + 1}     `.slice(0, 5);
    const humanReadbleSaverity = error.severity === DiagnosticSeverity.Error ? 'Error  ' : 'Warning';
    const indentAdjust = error.severity === DiagnosticSeverity.Error ? '   ' : ' ';

    core.info(`${indentAdjust}${paddingedLine}:${paddingedChar} ${humanReadbleSaverity} ${error.message}`);
    error.severity === DiagnosticSeverity.Error ? failCount.error++ : failCount.warning++;
}