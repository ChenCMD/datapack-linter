import { walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { CacheFile, DefaultCacheFile, DocNode, isRelIncluded, Uri } from '@spgoding/datapack-language-server/lib/types';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { loadLocale, locale } from '@spgoding/datapack-language-server/lib/locales/index';
import { Position, TextDocument } from 'vscode-languageserver-textdocument';
import { color, findDatapackRoots, getConfiguration, initCache, parseDocument } from './utils';
import * as core from '@actions/core';
import * as fs from 'fs';
import { TextDecoder } from 'util';
import path from 'path';

export const dir = process.cwd();
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
                    let isSuccess = true;
                    if (group) {
                        group = false;
                        core.endGroup()
                    }
                    parseData?.nodes.forEach((node: DocNode) => {
                        if (node.errors.length === 0) // Success
                            return;
                        // Failed
                        if (isSuccess) {
                            result = false;
                            isSuccess = false;
                            core.info(`${color.fore.light.red}✗${color.fore.reset} ${id?.id}`);
                        }
                        for (const parsingError of node.errors) {
                            const startPos = textDoc.positionAt(parsingError.range.start);
                            const endPos = textDoc.positionAt(parsingError.range.end);
                            const textStart: Position = {
                                line: startPos.line,
                                character: 0
                            };
                            const textEnd: Position = {
                                line: endPos.line,
                                character: textDoc.positionAt(textDoc.offsetAt({
                                    line: endPos.line + 1,
                                    character: 0
                                })).character
                            };
                            core.error(
                                (`   ${startPos.line}`).slice(-3)
                                + '  '
                                + locale('punc.quote',
                                    textDoc.getText({ start: textStart, end: startPos })
                                    + color.fore.light.red
                                    + textDoc.getText({ start: startPos, end: endPos })
                                    + color.fore.reset
                                    + (textEnd.character !== 0 ? textDoc.getText({ start: endPos, end: textEnd }) : '')
                                )
                            );
                            core.error(`    ${parsingError.message}`);
                        }
                    });
                    if (isSuccess)
                        core.info(`${color.fore.normal.green}✓${color.fore.reset} ${id?.id}`);
                    else
                        core.endGroup();
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