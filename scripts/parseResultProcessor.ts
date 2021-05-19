import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { CacheCategory, CacheType, CacheVisibility, Config, DatapackDocument } from '@spgoding/datapack-language-server/lib/types';
import path from 'path';
import { DiagnosticSeverity, TextDocument } from 'vscode-json-languageservice';
import { LintData } from './types/ParseData';



export function makeLintData(parsedData: DatapackDocument, id: IdentityNode, doc: TextDocument, root: string, rel: string): LintData {
    const messages: string[] = [];
    const title = `${id} (${path.parse(root).name}/${rel.replace(/\\/g, '/')})`;
    const failCount = { warning: 0, error: 0 };
    let isErrorFound = false;

    for (const node of parsedData?.nodes ?? []) {
        if (node.errors.filter(err => err.severity < 3).length === 0) // Success
            continue;
        // Failed
        if (!isErrorFound) {
            messages.push(`\u001b[91m✗\u001b[39m  ${title}`);
            isErrorFound = true;
        }
        node.errors
            .filter(err => err.severity < 3)
            .map(err => err.toDiagnostic(doc))
            .sort((errA, errB) => errA.range.start.line - errB.range.start.line)
            .forEach(err => {
                const pos = err.range.start;
                const paddingLine = `   ${pos.line + 1}`.slice(-4);
                const paddingChar = `${pos.character + 1}     `.slice(0, 5);
                const humanReadableSeverity = err.severity === DiagnosticSeverity.Error ? 'Error  ' : 'Warning';
                const indentAdjust = err.severity === DiagnosticSeverity.Error ? '   ' : ' ';

                messages.push(`${indentAdjust}${paddingLine}:${paddingChar} ${humanReadableSeverity} ${err.message}`);
                err.severity === DiagnosticSeverity.Error ? failCount.error++ : failCount.warning++;
            });
    }
    if (!isErrorFound)
        messages.push(`\u001b[92m✓\u001b[39m  ${title}`);
    return { messages, failCount };
}

export function makeDefineData(parsedData: DatapackDocument, id: IdentityNode, root: string, rel: string, testPath: string[] | undefined, config: Config | undefined): string[] | undefined {
    if (!testPath || !config) return undefined;

    const test = (visibility: CacheVisibility | CacheVisibility[] | undefined): boolean => {
        if (visibility) {
            if (visibility instanceof Array)
                return visibility.length ? visibility.some(v => test(v)) : test(undefined);

            const regex = new RegExp(`^${visibility.pattern
                .replace(/\?/g, '[^:/]')
                .replace(/\*\*\//g, '.*')
                .replace(/\*\*/g, '.*')
                .replace(/\*/g, '[^:/]*')}$`);
            return testPath.some(v => regex.test(v) || regex.test(v.match(/^[^:]+$/) ? `minecraft:${v}` : v));
        }

        const defaultVisibility = config.env.defaultVisibility;
        if (typeof defaultVisibility === 'string') {
            const genVisibility = (pattern: string): CacheVisibility => ({ type: '*', pattern });
            if (defaultVisibility === 'private')
                return test(genVisibility(id.toString()));

            if (defaultVisibility === 'internal') {
                const namespace = id.getNamespace();
                if (namespace === IdentityNode.DefaultNamespace)
                    return test(genVisibility(namespace));
                else
                    return test([genVisibility(`${namespace}:**`), genVisibility(`${IdentityNode.DefaultNamespace}:**`)]);
            }
            if (defaultVisibility === 'public')
                return true;
        }
        return (defaultVisibility instanceof Array && !defaultVisibility.length) ? true : test(defaultVisibility);
    };

    const messages: string[] = [];
    let isDefineFind = false;
    const append = (str: string, indent = 0) => messages.push(' '.repeat(indent) + str);

    for (const node of parsedData?.nodes ?? []) {
        for (const type of Object.keys(node.cache) as CacheType[]) {
            const category = node.cache[type] as CacheCategory;
            for (const name of Object.keys(category)) {
                const defines = [...category[name]!.dcl ?? [], ...category[name]!.def?.filter(v => v.end) ?? []];
                if (defines.some(v => test(v.visibility))) {
                    if (!isDefineFind) {
                        append(`${path.parse(root).name}/${rel.replace(/\\/g, '/')}`);
                        isDefineFind = true;
                    }
                    append(`${type} ${name}`, 4);
                }
            }
        }
    }
    return messages;
}