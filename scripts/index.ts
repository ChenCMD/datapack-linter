import { getClientCapabilities, isRelIncluded, Uri, VersionInformation } from '@spgoding/datapack-language-server/lib/types';
import { getTextDocument, walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import { PluginLoader } from '@spgoding/datapack-language-server/lib/plugins/PluginLoader';
import { DatapackLanguageService, readFile, requestText } from '@spgoding/datapack-language-server';
import { IdentityNode } from '@spgoding/datapack-language-server/lib/nodes';
import { loadLocale } from '@spgoding/datapack-language-server/lib/locales';
import * as core from '@actions/core';
import path from 'path';
import { findDatapackRoots, getConfiguration, updateCacheFile, outputErrorMessage, getError, getDefine, outputDefineMessage } from './utils';
import { DefineData, ErrorData, getSafeMessageData, LintingData } from './types/Results';
import { promises as fsp } from 'fs';
import mather from './matcher.json';

const dir = process.cwd();
lint();

async function lint() {
    await fsp.writeFile(path.join(dir, 'matcher.json'), JSON.stringify(mather));
    // add Problem Matcher
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
        globalStoragePath: path.join(dir, '_storage'),
        plugins: await PluginLoader.load(),
        versionInformation: await getLatestVersions()
    });
    service.init();
    const dirUri = Uri.file(dir);
    const config = await service.getConfig(dirUri);
    service.roots.push(...await findDatapackRoots(dirUri, config));
    // Env Log
    console.log('datapack roots:');
    service.roots.forEach(v => console.log(v.path));
    await updateCacheFile(service);

    // Lint Region
    const errorResults: LintingData<ErrorData> = {};
    const defineResults: LintingData<DefineData> = {};
    // expect '' | 'public' | resourcePath
    const testPath = core.getInput('outputDefine');

    await Promise.all(service.roots.map(async root =>
        await walkFile(
            root.fsPath,
            path.join(root.fsPath, 'data'),
            async (file, rel) => {
                // language check region
                const dotIndex = file.lastIndexOf('.');
                const slashIndex = file.lastIndexOf('/');
                const langID = dotIndex !== -1 && slashIndex < dotIndex ? file.substring(dotIndex + 1) : '';
                if (!(langID === 'mcfunction' || langID === 'json'))
                    return;

                // parsing data
                const text = await readFile(file);
                const textDoc = await getTextDocument({ uri: Uri.file(file), langID, version: null, getText: async () => text });
                const parseData = await service.parseDocument(textDoc);

                // get IdentityNode
                const { id, category } = IdentityNode.fromRel(rel) ?? {};

                // undefined check
                if (!parseData || !id || !category)
                    return;

                // pushing message
                getSafeMessageData(errorResults, category).push(getError(parseData, id, textDoc, root, rel));
                if (testPath !== '')
                    getSafeMessageData(defineResults, category).push(getDefine(parseData, id, root, rel, testPath.split(/\n/), config));
            },
            async (_, rel) => isRelIncluded(rel, config)
        )
    ));

    // log group end
    core.endGroup();

    // define message output
    if (testPath !== '') {
        core.startGroup('defines');
        outputDefineMessage(defineResults);
        core.endGroup();
    }

    // message output
    const failCount = outputErrorMessage(errorResults);

    // last message output
    if (failCount.error + failCount.warning === 0) {
        core.info('Check successful');
    } else {
        const errorMul = failCount.error > 1 ? 's' : '';
        const warningMul = failCount.warning > 1 ? 's' : '';
        core.info(`Check failed (${failCount.error} error${errorMul}, ${failCount.warning} warning${warningMul})`);
        if (core.getInput('DEBUG') !== 'true')
            process.exitCode = core.ExitCode.Failure;
        else
            core.info('Test forced pass. Because debug mode');
    }
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