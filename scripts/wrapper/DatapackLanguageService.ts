import * as core from '@actions/core';
import { DatapackLanguageService, pathAccessible, readFile, requestText } from '@spgoding/datapack-language-server';
import { loadLocale } from '@spgoding/datapack-language-server/lib/locales';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { Plugin } from '@spgoding/datapack-language-server/lib/plugins';
import { PluginLoader } from '@spgoding/datapack-language-server/lib/plugins/PluginLoader';
import { getRel, getTextDocument, partitionedIteration, walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { CacheCategory, CacheFile, CacheType, CacheUnit, Config, DatapackDocument, FetchConfigFunction, getClientCapabilities, isRelIncluded, trimCache, Uri, VersionInformation } from '@spgoding/datapack-language-server/lib/types';
import { promises as fsp } from 'fs';
import path from 'path';
import { TextDocument } from 'vscode-languageserver-textdocument';
import { findDatapackRoots, generateChecksum, getConfiguration, setTimeOut } from '../utils';
import { FileChangeChecker } from '../utils/FileChangeChecker';

export class EasyDatapackLanguageService {
    private readonly _service: DatapackLanguageService;
    private _analyzedObjectCount = 0;


    private constructor(
        globalStoragePath: string,
        private _config: Config,
        cacheFile: CacheFile | undefined,
        fetchConfig: FetchConfigFunction,
        plugins: Map<string, Plugin>,
        versionInformation: VersionInformation | undefined,
        private _gcThreshold: number
    ) {
        const capabilities = getClientCapabilities({ workspace: { configuration: true, didChangeConfiguration: { dynamicRegistration: true } } });
        this._service = new DatapackLanguageService({
            cacheFile,
            capabilities,
            fetchConfig,
            globalStoragePath,
            plugins,
            versionInformation
        });
    }

    static async createInstance(
        dir: string,
        globalStoragePath: string,
        cacheFile: CacheFile | undefined,
        fileChangeChecker: FileChangeChecker,
        gcThreshold: number
    ): Promise<EasyDatapackLanguageService> {
        const config = await getConfig(dir);
        const easyDLS = new EasyDatapackLanguageService(
            globalStoragePath,
            config,
            cacheFile,
            () => Promise.resolve(config),
            await PluginLoader.load(),
            await getLatestVersions(),
            gcThreshold
        );

        await easyDLS._service.init();

        await easyDLS._service.getVanillaData(easyDLS.config);

        // update root
        easyDLS._service.roots.push(...await findDatapackRoots(dir, easyDLS.config));
        core.info('datapack roots:');
        easyDLS.roots.forEach(v => core.info(v.path));

        // update cache
        await easyDLS._updateCacheFile(fileChangeChecker);

        return easyDLS;
    }

    get cacheFile(): CacheFile {
        return this._service.cacheFile;
    }

    get roots(): Uri[] {
        return this._service.roots;
    }

    get config(): Config {
        return this._config;
    }


    async writeCacheFile(cachePath: string): Promise<string> {
        await fsp.writeFile(cachePath, JSON.stringify(this.cacheFile), { encoding: 'utf8' });
        return JSON.stringify(this.cacheFile, undefined, '  ');
    }

    async parseDoc(file: string, rel: string): Promise<{
        parseResult: DatapackDocument
        identityNode: IdentityNode
        textDocument: TextDocument
    } | undefined> {
        // language check region
        const dotIndex = file.lastIndexOf('.');
        const slashIndex = file.lastIndexOf('/');
        const langID = dotIndex !== -1 && slashIndex < dotIndex ? file.substring(dotIndex + 1) : '';
        if (!(langID === 'mcfunction' || langID === 'json'))
            return undefined;

        // parse region
        const textDocument = await getTextDocument({ uri: Uri.file(file), langID, version: null, getText: async () => await readFile(file) });
        const parseResult = await this._service.parseDocument(textDocument);

        // get IdentityNode
        const { id } = IdentityNode.fromRel(rel) ?? {};

        // undefined check
        if (!parseResult || !id)
            return undefined;

        this.gc(parseResult.nodes.length);

        return { parseResult, identityNode: id, textDocument };
    }

    gc(force: true): void;
    gc(addAnalyzedObjectCount?: number): void;
    gc(countOrForce: number | true = 17): void {
        if (typeof countOrForce === 'number')
            this._analyzedObjectCount += countOrForce;
        if (countOrForce === true || this._gcThreshold <= this._analyzedObjectCount) {
            this._analyzedObjectCount = 0;
            this._service.onDeletedFile(Uri.file(path.join('This', 'way', 'I', 'can', 'illegally', 'clear', 'service.caches')));
        }
    }


    private async _updateCacheFile(fileChangeChecker: FileChangeChecker): Promise<void> {
        try {
            // Check the files saved in the cache file.
            const time1 = new Date().getTime();
            if (fileChangeChecker.isChecksumsExists()) await this._checkFilesInCache(fileChangeChecker);
            const time2 = new Date().getTime();
            await this._addNewFilesToCache(fileChangeChecker);
            trimCache(this.cacheFile.cache);
            const time3 = new Date().getTime();
            console.info(`[updateCacheFile] [1] ${time2 - time1} ms`);
            console.info(`[updateCacheFile] [2] ${time3 - time2} ms`);
            console.info(`[updateCacheFile] [T] ${time3 - time1} ms`);
            this.gc(true);
        } catch (e) {
            console.error('[updateCacheFile] ', e);
        }
    }

    private async _checkFilesInCache(fileChangeChecker: FileChangeChecker) {
        const uriStrings = Object.keys(this.cacheFile.files).values();
        return await partitionedIteration(uriStrings, async uriString => {
            const uri = this._service.parseUri(uriString);
            const rel = getRel(uri, this.roots);
            const manageChecksum = (checksum?: string) => {
                fileChangeChecker.appendBypassFiles(...this._getReferenceFromFile(uri.fsPath));
                fileChangeChecker.updateNextChecksum(uri.fsPath, checksum);
            };
            if (rel) {
                if (!(await pathAccessible(uri.fsPath))) {// removed/renamed is also processed here
                    core.debug(`file delete detected: ${uri.fsPath}`);
                    manageChecksum();
                    this._service.onDeletedFile(uri);
                } else {
                    const checkSum = await generateChecksum(uri.fsPath);
                    if (fileChangeChecker.isFileNotEqualChecksum(uri.fsPath, checkSum, false, false)) {
                        core.debug(`file change detected: ${uri.fsPath}`);
                        fileChangeChecker.appendBypassFiles(...this._getReferenceFromFile(uri.fsPath));
                        await this._service.onModifiedFile(uri);
                        manageChecksum(checkSum);
                        this.cacheFile.files[uriString] = 0;
                    }
                }
            }
        });
    }

    private async _addNewFilesToCache(fileChangeChecker: FileChangeChecker) {
        return Promise.all(this.roots.map(root => {
            const dataPath = path.join(root.fsPath, 'data');
            return walkFile(
                root.fsPath,
                dataPath,
                async abs => {
                    const uri = this._service.parseUri(Uri.file(abs).toString());
                    const uriString = uri.toString();
                    if (this.cacheFile.files[uriString] === undefined && fileChangeChecker.isFileNewly(abs)) {
                        core.debug(`file add detected: ${uri.fsPath}`);
                        await this._service.onAddedFile(uri);
                        this.gc();
                        fileChangeChecker.appendBypassFiles(...this._getReferenceFromFile(abs));
                        fileChangeChecker.updateNextChecksum(abs, await generateChecksum(abs));
                        this.cacheFile.files[uriString] = 0;
                    }
                },
                async (_, rel) => isRelIncluded(rel, this.config)
            );
        }));
    }

    private _getReferenceFromFile(file: string): string[] {
        const res: string[] = [];
        for (const type of Object.keys(this.cacheFile.cache)) {
            const category = this.cacheFile.cache[type as CacheType] as CacheCategory;
            for (const id of Object.keys(category)) {
                const { dcl, def, ref } = category[id] as CacheUnit;
                if ([...dcl ?? [], ...def ?? []].some(v => v.uri === `${Uri.file(file).toString()}`))
                    res.push(...ref?.filter(v => v.uri !== undefined).map(v => Uri.parse(v.uri!).fsPath) ?? []);
            }
        }
        return res;
    }
}

async function getConfig(dir: string): Promise<Config> {
    const configUri = Uri.file(path.resolve(dir, './.vscode/settings.json'));
    const config = await getConfiguration(configUri.fsPath);
    await loadLocale(config.env.language, 'en');
    return config;
}

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
async function getLatestVersions() {
    console.info('[LatestVersions] Fetching the latest versions...');
    const str = await Promise.race([
        requestText('https://launchermeta.mojang.com/mc/game/version_manifest.json'),
        setTimeOut(7_000)
    ]);
    const { latest: { release, snapshot }, versions }: { latest: { release: string, snapshot: string }, versions: { id: string }[] } = JSON.parse(str);
    const processedVersion = '1.16.2';
    const processedVersionIndex = versions.findIndex(v => v.id === processedVersion);
    const processedVersions = processedVersionIndex >= 0 ? versions.slice(0, processedVersionIndex + 1).map(v => v.id) : [];
    const ans = { latestRelease: release, latestSnapshot: snapshot, processedVersions };
    console.info(`[LatestVersions] versionInformation = ${JSON.stringify(ans)}`);
    return ans;
}