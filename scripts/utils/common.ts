import { getRootUri, walkRoot } from '@spgoding/datapack-language-server/lib/services/common';
import { Config, Uri } from '@spgoding/datapack-language-server/lib/types';
import { pathAccessible } from '@spgoding/datapack-language-server';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { IndexSignature } from '../types/common';
/**
 * This function is equivalent to the one implemented in datapack-language-server/server.ts.
 *
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
export async function findDatapackRoots(dir: string, config: Config): Promise<Uri[]> {
    const rootCandidatePaths = new Set<string>();
    const dirPath = dir;
    rootCandidatePaths.add(dirPath);
    await walkRoot(
        Uri.file(dir), dirPath,
        abs => rootCandidatePaths.add(abs),
        config.env.detectionDepth
    );
    const roots: Uri[] = [];
    for (const candidatePath of rootCandidatePaths) {
        const dataPath = path.join(candidatePath, 'data');
        const packMcmetaPath = path.join(candidatePath, 'pack.mcmeta');
        if (await pathAccessible(dataPath) && await pathAccessible(packMcmetaPath)) {
            const uri = getRootUri(Uri.file(candidatePath).toString());
            roots.push(uri);
        }
    }
    return roots;
}

export async function generateChecksum(file: string): Promise<string> {
    return await new Promise((resolve, reject) => {
        const hash = crypto.createHash('sha1');
        fs.createReadStream(file, { encoding: 'utf-8', highWaterMark: 128 * 1024 })
            .on('data', chunk => hash.update(chunk))
            .on('end', () => resolve(hash.digest('hex').toLowerCase()))
            .on('error', reject);
    });
}

export async function combineIndesSignatureForEach<T, U>(
    baseList: IndexSignature<T>,
    overrideList: IndexSignature<U>,
    convertFn: (element: U, key: string, list: IndexSignature<U>) => T | Promise<T>,
    callbackFn: (element: T, key: string, list: IndexSignature<T>) => void | Promise<void>,
    sortFn?: (a: string, b: string) => number
): Promise<void> {
    for (const key of new Set<string>([...Object.keys(baseList), ...Object.keys(overrideList)].sort(sortFn))) {
        if (overrideList[key]) baseList[key] = await convertFn(overrideList[key], key, overrideList);
        await callbackFn(baseList[key], key, baseList);
    }
}

export function getSafeRecordValue<T extends string | number | symbol, U>(data: Record<T, U[]>, type: T): U[] {
    return data[type] ?? (data[type] = []);
}