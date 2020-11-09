import { walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { CacheFile, DefaultCacheFile, DocNode, isRelIncluded, Uri } from '@spgoding/datapack-language-server/lib/types';
import { Position, TextDocument } from 'vscode-languageserver-textdocument';
import * as fs from 'fs';
import { TextDecoder } from 'util';
import { findDatapackRoots } from './utils/common';
import path from 'path';
import { getConfiguration } from './utils/config';
import { initCache } from './utils/cache';
import { parseDocument } from './utils/parser';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import * as core from '@actions/core';

export const dir = process.cwd();
export const config = getConfiguration(path.join(dir, '.vscode', 'settings.json'));
export let roots: Uri[];
export const cacheFile: CacheFile = DefaultCacheFile;

(async () => {
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

                if (textDoc.languageId === 'mcfunction') {
                    const parseData = await parseDocument(textDoc);
                    const id = IdentityNode.fromRel(rel);
                    let isSuccess = true;
                    parseData?.nodes.forEach((node: DocNode) => {
                        if (node.errors.length === 0) // Success
                            return;
                        // Failed
                        if (isSuccess) {
                            result = false;
                            isSuccess = false;
                            core.startGroup(`\u001b[91m ✗\u001b[39m  ${id?.id}`);
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

                            core.error(`${(`   ${startPos.line}`).slice(-3)} "${textDoc.getText({
                                start: textStart,
                                end: startPos
                            })}\u001b[91m${textDoc.getText({
                                start: startPos,
                                end: endPos
                            })}\u001b[39m${textEnd.character !== 0 ? textDoc.getText({
                                start: endPos,
                                end: textEnd
                            }) : ''}"`);
                            core.error(`    ${parsingError.message}`);
                        }
                    });
                    if (isSuccess)
                        core.info(`\u001b[92m ✓\u001b[39m  ${id?.id}`);
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