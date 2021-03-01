import { CacheFile, Config, DatapackDocument, getClientCapabilities, isRelIncluded, trimCache, Uri, VersionInformation } from '@spgoding/datapack-language-server/lib/types';
import { getRelAndRootIndex, getTextDocument, partitionedIteration, walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { DatapackLanguageService, pathAccessible, readFile, requestText } from '@spgoding/datapack-language-server';
import { PluginLoader } from '@spgoding/datapack-language-server/lib/plugins/PluginLoader';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { loadLocale } from '@spgoding/datapack-language-server/lib/locales';
import { Plugin } from '@spgoding/datapack-language-server/lib/plugins';
import { TextDocument } from 'vscode-json-languageservice';
import path from 'path';
import { findDatapackRoots, getConfiguration } from '../utils';
import { isDiffInculuded, ProcessedCompare } from '../types/ProcessedCompare';

export class EasyDatapackLanguageService {
    private readonly _service: DatapackLanguageService;
    private _config: Config;
    private _analyzedObjectCount = 0;
    private _gcThreshold: number;

    private constructor(
        private readonly dir: string,
        globalStoragePath: string,
        cacheFile: CacheFile | undefined,
        plugins: Map<string, Plugin>,
        versionInformation: VersionInformation | undefined
    ) {
        const capabilities = getClientCapabilities({ workspace: { configuration: true, didChangeConfiguration: { dynamicRegistration: true } } });
        this._service = new DatapackLanguageService({
            cacheFile,
            capabilities,
            fetchConfig: this.getFetchConfig(),
            globalStoragePath,
            plugins,
            versionInformation
        });
    }

    static async createInstance(
        dir: string,
        globalStoragePath: string,
        cacheFile: CacheFile | undefined,
        gcThreshold: number
    ): Promise<EasyDatapackLanguageService> {
        const easyDLS = new EasyDatapackLanguageService(
            dir,
            globalStoragePath,
            cacheFile,
            await PluginLoader.load(),
            await getLatestVersions()
        );
        easyDLS._service.init();
        const dirUri = Uri.file(dir);

        easyDLS._gcThreshold = gcThreshold;
        easyDLS._config = await easyDLS._service.getConfig(dirUri);

        easyDLS._service.roots.push(...await findDatapackRoots(dirUri, easyDLS._config));

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

    async updateCacheFile(compareFiles: ProcessedCompare[] | undefined): Promise<void> {
        try {
            // Check the files saved in the cache file.
            const time1 = new Date().getTime();
            if (compareFiles) await this.checkFilesInCache(compareFiles);
            const time2 = new Date().getTime();
            await this.addNewFilesToCache(compareFiles);
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

    private async checkFilesInCache(compareFiles: ProcessedCompare[]) {
        const uriStrings = Object.keys(this.cacheFile.files).values();
        return partitionedIteration(uriStrings, async uriString => {
            const uri = this._service.parseUri(uriString);
            const { rel } = getRelAndRootIndex(uri, this.roots) ?? {};
            if (!rel || !isRelIncluded(rel, this.config)) {
                delete this.cacheFile.files[uriString];
            } else {
                if (!(await pathAccessible(uri.fsPath))) // removed/renamed is also processed here
                    this._service.onDeletedFile(uri);
                else if (isDiffInculuded(rel, compareFiles, ['modified']))
                    await this._service.onModifiedFile(uri);
            }
        });
    }

    private async addNewFilesToCache(compareFiles: ProcessedCompare[] | undefined) {
        return Promise.all(this.roots.map(root => {
            const dataPath = path.join(root.fsPath, 'data');
            return walkFile(
                root.fsPath,
                dataPath,
                async (abs, _rel, stat) => {
                    const uri = this._service.parseUri(Uri.file(abs).toString());
                    const uriString = uri.toString();
                    if (this.cacheFile.files[uriString] === undefined) {
                        this.gc();
                        await this._service.onAddedFile(uri);
                        this.cacheFile.files[uriString] = stat.mtimeMs;
                    }
                },
                async (_, rel) => isRelIncluded(rel, this.config)
                    && isDiffInculuded(rel, compareFiles, ['added', 'renamed'])
            );
        }));
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

    private getFetchConfig() {
        return async () => {
            const configUri = Uri.file(path.resolve(this.dir, './.vscode/settings.json'));
            const config = await getConfiguration(configUri.fsPath);
            await loadLocale(config.env.language, 'en');
            return config;
        };
    }
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
    let ans: VersionInformation | undefined;
    try {
        console.info('[LatestVersions] Fetching the latest versions...');
        const str = await Promise.race([
            requestText('https://launchermeta.mojang.com/mc/game/version_manifest.json'),
            new Promise<string>((_, reject) => setTimeout(() => reject(new Error('Time out!')), 7_000))
        ]);
        const { latest: { release, snapshot }, versions }: { latest: { release: string, snapshot: string }, versions: { id: string }[] } = JSON.parse(str);
        const processedVersion = '1.16.2';
        const processedVersionIndex = versions.findIndex(v => v.id === processedVersion);
        const processedVersions = processedVersionIndex >= 0 ? versions.slice(0, processedVersionIndex + 1).map(v => v.id) : [];
        ans = (release && snapshot) ? { latestRelease: release, latestSnapshot: snapshot, processedVersions } : undefined;
    } catch (e) {
        console.warn(`[LatestVersions] ${e}`);
    }
    console.info(`[LatestVersions] versionInformation = ${JSON.stringify(ans)}`);
    return ans;
}