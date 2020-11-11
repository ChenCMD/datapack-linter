import { DatapackDocument, Uri, FileType } from '@spgoding/datapack-language-server/lib/types';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { TextDocument, DiagnosticSeverity } from 'vscode-json-languageservice';
import * as core from '@actions/core';
import path from 'path';
import { MessageData, Output, LintingData, FailCount } from '../types/Results';

export function getMessageData(parseData: DatapackDocument, id: IdentityNode, document: TextDocument, root: Uri, rel: string): MessageData {
    const title = `${id} (${path.parse(root.fsPath).name}/${rel.replace(/\\/g, '/')})`;
    const messages: Output[] = [];
    for (const node of parseData?.nodes ?? []) {
        if (node.errors.length === 0) // Success
            continue;
        // Failed
        const result = node.errors
            .filter(err => err.severity < 3)
            .map(err => err.toDiagnostic(document))
            .sort((errA, errB) => errA.range.start.line - errB.range.start.line)
            .map(err => {
                const pos = err.range.start;
                const paddingedLine = `   ${pos.line + 1}`.slice(-4);
                const paddingedChar = (`${pos.character + 1}     `).slice(0, 5);
                const humanReadbleSaverity = err.severity === DiagnosticSeverity.Error ? 'Error  ' : 'Warning';
                return {
                    message: `${paddingedLine}:${paddingedChar} ${humanReadbleSaverity} ${err.message}`,
                    severity: err.severity ?? DiagnosticSeverity.Warning
                };
            });
        messages.push(...result);
    }
    return { title, messages };
}

export function outputMessage(results: LintingData): FailCount {
    const failCount = { warning: 0, error: 0 };
    for (const type of Object.keys(results)) {
        // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
        for (const result of results[type as FileType]!.sort((a, b) => a.title > b.title ? 1 : -1)) {
            if (result.messages.length === 0) {
                core.info(`\u001b[92m✓\u001b[39m  ${result.title}`);
                continue;
            }
            core.info(`\u001b[91m✗\u001b[39m  ${result.title}`);
            for (const out of result.messages) {
                if (out.severity === DiagnosticSeverity.Error) {
                    failCount.error++;
                    core.info(`   ${out.message}`);
                } else {
                    failCount.warning++;
                    core.info(` ${out.message}`);
                }
            }
        }
    }
    return failCount;
}