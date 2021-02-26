/**
 * @license
 * MIT License
 *
 * Copyright (c) 2019-2020 SPGoding
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { CacheFile, isRelIncluded, trimCache, Uri } from '@spgoding/datapack-language-server/lib/types';
import { walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { DatapackLanguageService } from '@spgoding/datapack-language-server';
import path from 'path';
import { DLSGarbageCollector } from './DLSGarbageCollector';

/**
 * This function is equivalent to the one implemented in datapack-language-server/server.ts, except for the last function service.onDeletedFile.
 */
export async function updateCacheFile(service: DatapackLanguageService, garbageCollector: DLSGarbageCollector): Promise<void> {
    try {
        // Check the files saved in the cache file.
        const time1 = new Date().getTime();
        // await checkFilesInCache(service.cacheFile, service.roots, service);
        const time2 = new Date().getTime();
        await addNewFilesToCache(service.cacheFile, service.roots, service, garbageCollector);
        trimCache(service.cacheFile.cache);
        const time3 = new Date().getTime();
        console.info(`[updateCacheFile] [1] ${time2 - time1} ms`);
        console.info(`[updateCacheFile] [2] ${time3 - time2} ms`);
        console.info(`[updateCacheFile] [T] ${time3 - time1} ms`);
        garbageCollector.gc(true);
    } catch (e) {
        console.error('[updateCacheFile] ', e);
    }
}

/**
 * This function is equivalent to the one implemented in datapack-language-server/server.ts.
 */
async function addNewFilesToCache(cacheFile: CacheFile, roots: Uri[], service: DatapackLanguageService, garbageCollector: DLSGarbageCollector) {
    return Promise.all(roots.map(root => {
        const dataPath = path.join(root.fsPath, 'data');
        return walkFile(
            root.fsPath,
            dataPath,
            async (abs, _rel, stat) => {
                const uri = service.parseUri(Uri.file(abs).toString());
                const uriString = uri.toString();
                if (cacheFile.files[uriString] === undefined) {
                    garbageCollector.gc(17);
                    await service.onAddedFile(uri);
                    cacheFile.files[uriString] = stat.mtimeMs;
                }
            },
            async (_abs, rel) => {
                const config = await service.getConfig(root);
                return isRelIncluded(rel, config);
            }
        );
    }));
}