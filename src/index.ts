import { walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { Uri } from '@spgoding/datapack-language-server/lib/types';
import { TextDocument } from 'vscode-languageserver-textdocument';
import * as fs from 'fs';
import { TextDecoder } from 'util';
import './utils/methodExtensions';
import { getResourcePath, getDatapackRoot } from './utils/common';
import { getFileType } from './types/FileTypes';
import path from 'path';
import { getConfiguration } from './utils/config';
import { getCache } from './utils/cache';
import { lintFile } from './utils/linter';

(async () => {
    // constant dir
    const dir = process.cwd();
    // get vscode config
    const config = getConfiguration(path.join(dir, '.vscode', 'settings.json'));
    // Cache Generate Region
    const cacheFile = await getCache(dir);
    // Lint Region
    await walkFile(dir, dir, async file => {
        const index = file.lastIndexOf('.');
        const ext = index !== -1 ? file.substring(index + 1) : undefined;
        const dpRoot = await getDatapackRoot(file);
        const filePath = dpRoot ? getResourcePath(file, dpRoot, getFileType(file, dpRoot)) : file;
        if (ext && (ext === 'mcfunction' || (ext === 'json' && dpRoot && getFileType(file, dpRoot)))) {
            const textDoc = TextDocument.create(Uri.file(file).toString(), ext, 0, new TextDecoder().decode(fs.readFileSync(file)));
            const errors = (await lintFile(textDoc, cacheFile, config)).filter(v => v.errors.length !== 0).flat(v => v.errors);

            if (errors.length === 0) { // Success
                console.info(`\u001b[92m✓\u001b[39m  ${filePath}`);
                return;
            } else { // Failed
                console.error(`\u001b[91✗\u001b[39m  ${filePath}`);
                for (const parsingError of errors) {
                    console.error(`    ${textDoc.getText({
                        start: textDoc.positionAt(parsingError.range.start),
                        end: textDoc.positionAt(parsingError.range.end)
                    })}`);
                    console.error(`    ${parsingError.message}`);
                }
            }
        }
    });
})();