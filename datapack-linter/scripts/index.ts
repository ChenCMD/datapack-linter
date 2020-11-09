import { walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { CacheFile, DefaultCacheFile, DocNode, isRelIncluded, ParsingError, Uri } from '@spgoding/datapack-language-server/lib/types';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { loadLocale } from '@spgoding/datapack-language-server/lib/locales';
import { TextDocument } from 'vscode-languageserver-textdocument';
import { findDatapackRoots, getConfiguration, initCache, parseDocument } from './utils';
import * as core from '@actions/core';
import * as fs from 'fs';
import { TextDecoder } from 'util';
import path from 'path';

export const dir = path.dirname(process.cwd());
export const config = getConfiguration(path.join(dir, '.vscode', 'settings.json'));
export let roots: Uri[];
export const cacheFile: CacheFile = DefaultCacheFile;

(async () => {
    let group = true;
    core.startGroup('init log');
    // init locale
    loadLocale(config.env.language, 'en');
    // find datapack roots
    roots = await findDatapackRoots(Uri.file(dir), config);
    // Cache Generate Region
    await initCache();
    // Lint Region
    let result = true;
    await Promise.all(roots.map(async root =>
        await walkFile(
            root.fsPath,
            root.fsPath,
            async (file, rel) => {
                const extIndex = file.lastIndexOf('.');
                const ext = extIndex !== -1 ? file.substring(extIndex + 1) : '';
                const uri = Uri.file(file);
                const textDoc = TextDocument.create(uri.toString(), ext, 0, new TextDecoder().decode(fs.readFileSync(file)));

                if (textDoc.languageId === 'mcfunction' || textDoc.languageId === 'json') {
                    const parseData = await parseDocument(textDoc);
                    const id = IdentityNode.fromRel(rel);
                    if (group) {
                        group = false;
                        core.endGroup();
                    }
                    const messages: string[] = [];
                    parseData?.nodes.forEach((node: DocNode) => {
                        if (node.errors.length === 0) // Success
                            return;
                        // Failed
                        result = false;
                        messages.push(...getErrorMessages(node.errors, textDoc));
                    });
                    if (messages.length === 0) {
                        core.info(`  \u001b[92m✓\u001b[39m   ${id?.id}`);
                    } else {
                        core.error(`${id?.id}`);
                        for (const mes of messages)
                            core.error(mes);
                    }
                }
            },
            // eslint-disable-next-line require-await
            async (_, rel) => isRelIncluded(rel, config)
        )
    ));
    if (!result)
        core.setFailed('Check failed');
    else
        core.info('Check successful');
})();

function getErrorMessages(errors: ParsingError[], textDoc: TextDocument): string[] {
    const severityToString = (severity: number) => {
        switch (severity) {
            case 0: return 'Error  ';
            case 1: return 'Warning';
        }
    };
    return errors.filter(v => v.severity < 2).map(error => {
        const pos = textDoc.positionAt(error.range.start);
        // eslint-disable-next-line prefer-template, space-unary-ops
        return ' '
            + `   ${pos.line + 1}`.slice(-4)
            + ':'
            + (`${pos.character + 1}     `).slice(0, 5)
            + ' '
            + severityToString(error.severity)
            + ' '
            + error.message;
    });
}