import { walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { CacheFile, DefaultCacheFile, DocNode, isRelIncluded, Uri } from '@spgoding/datapack-language-server/lib/types';
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
                    const severityToString = (severity: number) => {
                        switch (severity) {
                            case 0: return 'Error      ';
                            case 1: return 'Warning    ';
                            case 2: return 'Information';
                            case 3: return 'Hint       ';
                        }
                    };
                    let isSuccess = true;
                    if (group) {
                        group = false;
                        core.endGroup();
                    }
                    parseData?.nodes.forEach((node: DocNode) => {
                        if (node.errors.length === 0) // Success
                            return;
                        // Failed
                        if (isSuccess) {
                            result = false;
                            isSuccess = false;
                            core.info(`✗ ${id?.id}`);
                        }
                        for (const parsingError of node.errors) {
                            const pos = textDoc.positionAt(parsingError.range.start);
                            core.error(
                                // eslint-disable-next-line prefer-template, space-unary-ops
                                ' '
                                + `   ${pos.line}`.slice(-4)
                                + ':'
                                + (`${pos.character}     `).slice(0, 5)
                                + ' '
                                + severityToString(parsingError.severity)
                                + ' '
                                + parsingError.message
                            );
                        }

                    });
                    if (isSuccess)
                        core.info(`✓ ${id?.id}`);
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