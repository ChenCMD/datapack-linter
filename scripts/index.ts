import { getClientCapabilities, isRelIncluded, Uri, VersionInformation } from '@spgoding/datapack-language-server/lib/types';
import { DatapackLanguageService, readFile, requestText } from '@spgoding/datapack-language-server';
import { getTextDocument, walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { PluginLoader } from '@spgoding/datapack-language-server/lib/plugins/PluginLoader';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { loadLocale } from '@spgoding/datapack-language-server/lib/locales';
import * as core from '@actions/core';
import { promises as fsp } from 'fs';
import path from 'path';
import { findDatapackRoots, getConfiguration, updateCacheFile, printParseResult, Result, DLSGarbageCollector } from './utils';
import { DocumentData, getSafeRecordValue } from './types/Results';
import mather from './matcher.json';

const dir = process.cwd();
lint();

async function lint() {
    // file path init
    const globalStoragePath = path.join(dir, '_storage');
    const matcherPath = path.join(dir, 'matcher.json');
    // get inputs
    const testPath = core.getInput('outputDefine');
    const isDebug = core.getInput('DEBUG') === 'true';

    // add Problem Matcher
    await fsp.writeFile(matcherPath, JSON.stringify(mather));
    core.info('::add-matcher::matcher.json');
    // log group start
    core.startGroup('init log');
    // Env Log
    console.log(`dir: ${dir}`);

    // initialize DatapackLanguageService
    const capabilities = getClientCapabilities({ workspace: { configuration: true, didChangeConfiguration: { dynamicRegistration: true } } });
    const service = new DatapackLanguageService({
        capabilities,
        fetchConfig,
        globalStoragePath,
        plugins: await PluginLoader.load(),
        versionInformation: await getLatestVersions()
    });
    service.init();
    const dirUri = Uri.file(dir);
    const config = await service.getConfig(dirUri);
    service.roots.push(...await findDatapackRoots(dirUri, config));

    // create GarbageCollector
    const garbageCollector = new DLSGarbageCollector(service, 500);

    await updateCacheFile(service, garbageCollector);

    // Env Log
    console.log('datapack roots:');
    service.roots.forEach(v => console.log(v.path));

    // pre parse Region
    const parsingFile: Record<string, DocumentData[]> = {};
    await Promise.all(service.roots.map(async root => await walkFile(
        root.fsPath,
        path.join(root.fsPath, 'data'),
        async (file, rel) => getSafeRecordValue(parsingFile, root.fsPath).push({ file, rel }),
        async (_, rel) => isRelIncluded(rel, config)
    )));

    // log group end
    core.endGroup();

    // parse Region
    const result = new Result(testPath, config);
    for (const root of Object.keys(parsingFile).sort()) {
        for (const { file, rel } of parsingFile[root].sort())
            await parseDoc(service, garbageCollector, root, file, rel, result);
    }

    // define message output
    if (result.isOutDefine) {
        core.startGroup('defines');
        result.defineMessage.forEach(core.info);
        core.endGroup();
    }

    // delete files.
    // await fsp.rm(globalStoragePath, { recursive: true, force: true });
    // await fsp.rm(matcherPath, { force: true });

    // last message output
    if (result.hasFailCount()) {
        core.info('Check successful');
    } else {
        core.info(`Check failed (${result.getFailCountMessage()})`);
        if (!isDebug)
            process.exitCode = core.ExitCode.Failure;
        else
            core.info('Test forced pass. Because debug mode');
    }
}

async function parseDoc(service: DatapackLanguageService, garbageCollector: DLSGarbageCollector, root: string, file: string, rel: string, result: Result): Promise<void> {
    // language check region
    const dotIndex = file.lastIndexOf('.');
    const slashIndex = file.lastIndexOf('/');
    const langID = dotIndex !== -1 && slashIndex < dotIndex ? file.substring(dotIndex + 1) : '';
    if (!(langID === 'mcfunction' || langID === 'json'))
        return;

    // parsing data
    const textDoc = await getTextDocument({ uri: Uri.file(file), langID, version: null, getText: async () => await readFile(file) });
    const parseData = await service.parseDocument(textDoc);

    // get IdentityNode
    const { id } = IdentityNode.fromRel(rel) ?? {};

    // undefined check
    if (!parseData || !id)
        return;

    garbageCollector.gc(parseData.nodes.length);

    // append data to result
    result.addFailCount(printParseResult(parseData, id, textDoc, root, rel));
    if (result.isOutDefine)
        result.appendDefineMessage(parseData, id, root, rel);
}

async function fetchConfig() {
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