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

import { getRelAndRootIndex, partitionedIteration, walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { CacheFile, isRelIncluded, trimCache, Uri } from '@spgoding/datapack-language-server/lib/types';
import { DatapackLanguageService, pathAccessible } from '@spgoding/datapack-language-server';
import * as fsp from 'fs/promises';
import path from 'path';

/**
 * This function is equivalent to the one implemented in datapack-language-server/server.ts, except for the last function service.onDeletedFile.
 */
export async function updateCacheFile(service: DatapackLanguageService): Promise<void> {
    try {
        // Check the files saved in the cache file.
        const time1 = new Date().getTime();
        await checkFilesInCache(service.cacheFile, service.roots, service);
        const time2 = new Date().getTime();
        await addNewFilesToCache(service.cacheFile, service.roots, service);
        trimCache(service.cacheFile.cache);
        const time3 = new Date().getTime();
        console.info(`[updateCacheFile] [1] ${time2 - time1} ms`);
        console.info(`[updateCacheFile] [2] ${time3 - time2} ms`);
        console.info(`[updateCacheFile] [T] ${time3 - time1} ms`);
        service.onDeletedFile(Uri.file(path.join(
            'What_is_this',
            'It_is_taking_a_non-existent_path_to_Uri_and_passing_it_to_a_function.',
            'Why_I_do_not_know_what_that_means.',
            'This_way_I_can_illegally_clear_service.caches.',
            'If_you_do_not_do_this_it_will_cause_an_error.'
        )));
    } catch (e) {
        console.error('[updateCacheFile] ', e);
    }
}

/**
 * This function is equivalent to the one implemented in datapack-language-server/server.ts.
 */
async function checkFilesInCache(cacheFile: CacheFile, roots: Uri[], service: DatapackLanguageService) {
    const uriStrings = Object.keys(cacheFile.files).values();
    return partitionedIteration(uriStrings, async uriString => {
        const uri = service.parseUri(uriString);
        const result = getRelAndRootIndex(uri, roots);
        if (!result?.rel || !isRelIncluded(result.rel, await service.getConfig(roots[result.index]))) {
            delete cacheFile.files[uriString];
        } else {
            if (!(await pathAccessible(uri.fsPath))) {
                service.onDeletedFile(uri);
            } else {
                const stat = await fsp.stat(uri.fsPath);
                const lastModified = stat.mtimeMs;
                // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                const lastUpdated = cacheFile.files[uriString]!;
                if (lastModified > lastUpdated) {
                    cacheFile.files[uriString] = lastModified;
                    await service.onModifiedFile(uri);
                }
            }
        }
    });
}

/**
 * This function is equivalent to the one implemented in datapack-language-server/server.ts.
 */
async function addNewFilesToCache(cacheFile: CacheFile, roots: Uri[], service: DatapackLanguageService) {
    return Promise.all(roots.map(root => {
        const dataPath = path.join(root.fsPath, 'data');
        return walkFile(
            root.fsPath,
            dataPath,
            async (abs, _rel, stat) => {
                const uri = service.parseUri(Uri.file(abs).toString());
                const uriString = uri.toString();
                if (cacheFile.files[uriString] === undefined) {
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