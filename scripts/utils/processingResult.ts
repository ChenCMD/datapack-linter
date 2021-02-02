import { DatapackDocument, Uri, FileType, CacheType, CacheCategory, CacheVisibility, Config } from '@spgoding/datapack-language-server/lib/types';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { TextDocument, DiagnosticSeverity } from 'vscode-json-languageservice';
import * as core from '@actions/core';
import path from 'path';
import { DefineData, ErrorData, LintingData, FailCount, Output } from '../types/Results';

/**
 * Create and return message data from DatapackDocument.
 */
export function getError(parsedData: DatapackDocument, id: IdentityNode, doc: TextDocument, root: Uri, rel: string): Output<ErrorData> {
    const title = `${id} (${path.parse(root.fsPath).name}/${rel.replace(/\\/g, '/')})`;
    const res: Output<ErrorData> = { title, messages: [] };
    for (const node of parsedData?.nodes ?? []) {
        if (node.errors.length === 0) // Success
            continue;
        // Failed
        const result = node.errors
            .filter(err => err.severity < 3)
            .map(err => err.toDiagnostic(doc))
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
        res.messages.push(...result);
    }
    return res;
}

export function getDefine(parsedData: DatapackDocument, id: IdentityNode, root: Uri, rel: string, testPath: string[], config: Config): Output<DefineData> {
    const test = (visibility: CacheVisibility | CacheVisibility[] | undefined): boolean => {
        if (!visibility) {
            const defaultVisibility = config.env.defaultVisibility;
            if (typeof defaultVisibility === 'string') {
                if (defaultVisibility === 'private')
                    return test({ type: '*', pattern: id.toString() });
                if (defaultVisibility === 'internal') {
                    const namespace = id.getNamespace();
                    if (namespace === IdentityNode.DefaultNamespace)
                        return test({ type: '*', pattern: namespace });
                    else
                        return test([{ type: '*', pattern: `${namespace}:**` }, { type: '*', pattern: `${IdentityNode.DefaultNamespace}:**` }]);
                }
                if (defaultVisibility === 'public')
                    return true;
            }
            if (defaultVisibility instanceof Array && !defaultVisibility.length)
                return true;
            return test(defaultVisibility);
        }
        if (visibility instanceof Array) {
            if (!visibility.length)
                return test(undefined);
            return visibility.some(v => test(v));
        }
        const regex = new RegExp(`^${visibility.pattern
            .replace(/\?/g, '[^:/]')
            .replace(/\*\*\//g, '.*')
            .replace(/\*\*/g, '.*')
            .replace(/\*/g, '[^:/]*')}$`);
        return testPath.some(v => regex.test(v) || regex.test(v.match(/^[^:]+$/) ? `minecraft:${v}` : v));
    };
    const title = `${id} (${path.parse(root.fsPath).name}/${rel.replace(/\\/g, '/')})`;
    const res: Output<DefineData> = { title, messages: [] };
    for (const node of parsedData?.nodes ?? []) {
        for (const type of Object.keys(node.cache) as CacheType[]) {
            const category = node.cache[type] as CacheCategory;
            for (const name of Object.keys(category)) {
                if (category[name]!.dcl?.some(v => test(v.visibility))
                    || category[name]!.def?.filter(v => v.end !== 0).some(v => test(v.visibility)))
                    res.messages.push({ type, name });
            }
        }
    }
    return res;
}

/**
 * Output message data.
 */
export function outputErrorMessage(results: LintingData<ErrorData>): FailCount {
    const failCount = { warning: 0, error: 0 };
    for (const type of Object.keys(results)) {
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

/**
 * Output message data.
 */
export function outputDefineMessage(results: LintingData<DefineData>): void {
    for (const type of Object.keys(results)) {
        results[type as FileType]
            ?.filter(result => result.messages.length)
            .sort((a, b) => a.title > b.title ? 1 : -1)
            .forEach(result => {
                core.info(`${result.title}`);
                result.messages.forEach(out => core.info(`    ${out.type} ${out.name}`));
            });
    }
    return;
}