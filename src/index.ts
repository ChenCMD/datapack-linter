import { walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { CacheFile, DefaultCacheFile, DocNode, isRelIncluded, Uri } from '@spgoding/datapack-language-server/lib/types';
import { Position, TextDocument } from 'vscode-languageserver-textdocument';
import * as fs from 'fs';
import { TextDecoder } from 'util';
import './utils/methodExtensions';
import { findDatapackRoots } from './utils/common';
import path from 'path';
import { getConfiguration } from './utils/config';
import { initCache } from './utils/cache';
import { parseDocument } from './utils/parser';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';

export const dir = process.cwd();
export const config = getConfiguration(path.join(dir, '.vscode', 'settings.json'));
export let roots: Uri[];
export const cacheFile: CacheFile = DefaultCacheFile;

(async () => {
    // find datapack roots
    roots = await findDatapackRoots(Uri.file(dir), config);
    console.log(`datapack roots: ${roots.join(', ')}`);
    // Cache Generate Region
    await initCache();
    // Lint Region
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
                            isSuccess = false;
                            console.error(`\u001b[91m✗\u001b[39m  ${id?.id}`);
                        }
                        for (const parsingError of node.errors) {
                            const textStart: Position = {
                                line: textDoc.positionAt(parsingError.range.start).line,
                                character: 0
                            };
                            const textEnd = textDoc.positionAt(textDoc.offsetAt({
                                line: textDoc.positionAt(parsingError.range.end).line + 1,
                                character: 0
                            }));

                            console.error(`    ${textDoc.getText({
                                start: textStart,
                                end: textDoc.positionAt(parsingError.range.start)
                            })}\u001b[91m${textDoc.getText({
                                start: textDoc.positionAt(parsingError.range.start),
                                end: textDoc.positionAt(parsingError.range.end)
                            })}\u001b[39m${textDoc.getText({
                                start: textDoc.positionAt(parsingError.range.end),
                                end: textEnd
                            })}`);
                            console.error(`    ${parsingError.message}`);
                        }
                    });
                    if (isSuccess)
                        console.info(`\u001b[92m✓\u001b[39m  ${id?.id}`);
                }
            },
            // eslint-disable-next-line require-await
            async (_, rel) => isRelIncluded(rel, config)
        )
    ));
})();