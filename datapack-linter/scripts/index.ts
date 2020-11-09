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
                const filePath = `${path.parse(root.fsPath).name}\\${rel}`;

                if (!(textDoc.languageId === 'mcfunction' || textDoc.languageId === 'json'))
                    return;
                const parseData = await parseDocument(textDoc);
                const id = IdentityNode.fromRel(rel);
                if (group) {
                    group = false;
                    core.endGroup();
                }
                let isSuccess = true;
                parseData?.nodes.forEach((node: DocNode) => {
                    if (node.errors.length === 0) // Success
                        return;
                    // Failed
                    result = false;
                    if (isSuccess) {
                        isSuccess = false;
                        core.error(`${id?.id} (${filePath})`);
                    }
                    outputErrorMessage(node.errors, textDoc);
                });
                if (isSuccess)
                    core.info(`  \u001b[92m✓\u001b[39m   ${id?.id} (${filePath})`);
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

function outputErrorMessage(errors: ParsingError[], textDoc: TextDocument): void {
    errors.filter(v => v.severity < 3).forEach(error => {
        const pos = textDoc.positionAt(error.range.start);
        const mes =
            // eslint-disable-next-line prefer-template, space-unary-ops
            ' '
            + `   ${pos.line + 1}`.slice(-4)
            + ':'
            + (`${pos.character + 1}     `).slice(0, 5)
            + ' '
            + (error.severity === 1 ? 'Error  ' : 'Warning')
            + ' '
            + error.message;
        if (error.severity === 1)
            core.error(mes);
        else
            core.warning(mes);
    });
}