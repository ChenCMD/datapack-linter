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
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.findDatapackRoots = void 0;
const common_1 = require("@spgoding/datapack-language-server/lib/services/common");
const types_1 = require("@spgoding/datapack-language-server/lib/types");
const datapack_language_server_1 = require("@spgoding/datapack-language-server");
const path_1 = __importDefault(require("path"));
/**
 * This function is equivalent to the one implemented in datapack-language-server/server.ts.
 */
async function findDatapackRoots(dir, config) {
    const rootCandidatePaths = new Set();
    const dirPath = dir.fsPath;
    rootCandidatePaths.add(dirPath);
    await common_1.walkRoot(dir, dirPath, abs => rootCandidatePaths.add(abs), config.env.detectionDepth);
    const roots = [];
    for (const candidatePath of rootCandidatePaths) {
        const dataPath = path_1.default.join(candidatePath, 'data');
        const packMcmetaPath = path_1.default.join(candidatePath, 'pack.mcmeta');
        if (await datapack_language_server_1.pathAccessible(dataPath) && await datapack_language_server_1.pathAccessible(packMcmetaPath)) {
            const uri = common_1.getRootUri(types_1.Uri.file(candidatePath).toString());
            roots.push(uri);
        }
    }
    return roots;
}
exports.findDatapackRoots = findDatapackRoots;
