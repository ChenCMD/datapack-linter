"use strict";
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
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    Object.defineProperty(o, k2, { enumerable: true, get: function() { return m[k]; } });
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.updateCacheFile = void 0;
const common_1 = require("@spgoding/datapack-language-server/lib/services/common");
const types_1 = require("@spgoding/datapack-language-server/lib/types");
const datapack_language_server_1 = require("@spgoding/datapack-language-server");
const fsp = __importStar(require("fs/promises"));
const path_1 = __importDefault(require("path"));
async function updateCacheFile(service) {
    try {
        // Check the files saved in the cache file.
        const time1 = new Date().getTime();
        await checkFilesInCache(service.cacheFile, service.roots, service);
        const time2 = new Date().getTime();
        await addNewFilesToCache(service.cacheFile, service.roots, service);
        types_1.trimCache(service.cacheFile.cache);
        const time3 = new Date().getTime();
        console.info(`[updateCacheFile] [1] ${time2 - time1} ms`);
        console.info(`[updateCacheFile] [2] ${time3 - time2} ms`);
        console.info(`[updateCacheFile] [T] ${time3 - time1} ms`);
    }
    catch (e) {
        console.error('[updateCacheFile] ', e);
    }
}
exports.updateCacheFile = updateCacheFile;
async function checkFilesInCache(cacheFile, roots, service) {
    const uriStrings = Object.keys(cacheFile.files).values();
    return common_1.partitionedIteration(uriStrings, async (uriString) => {
        const uri = service.parseUri(uriString);
        const result = common_1.getRelAndRootIndex(uri, roots);
        if (!result?.rel || !types_1.isRelIncluded(result.rel, await service.getConfig(roots[result.index]))) {
            delete cacheFile.files[uriString];
        }
        else {
            if (!(await datapack_language_server_1.pathAccessible(uri.fsPath))) {
                service.onDeletedFile(uri);
            }
            else {
                const stat = await fsp.stat(uri.fsPath);
                const lastModified = stat.mtimeMs;
                // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                const lastUpdated = cacheFile.files[uriString];
                if (lastModified > lastUpdated) {
                    cacheFile.files[uriString] = lastModified;
                    await service.onModifiedFile(uri);
                }
            }
        }
    });
}
async function addNewFilesToCache(cacheFile, roots, service) {
    return Promise.all(roots.map(root => {
        const dataPath = path_1.default.join(root.fsPath, 'data');
        return common_1.walkFile(root.fsPath, dataPath, async (abs, _rel, stat) => {
            const uri = service.parseUri(types_1.Uri.file(abs).toString());
            const uriString = uri.toString();
            if (cacheFile.files[uriString] === undefined) {
                await service.onAddedFile(uri);
                cacheFile.files[uriString] = stat.mtimeMs;
            }
        }, async (_abs, rel) => {
            const config = await service.getConfig(root);
            return types_1.isRelIncluded(rel, config);
        });
    }));
}
