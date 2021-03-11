import { CacheFile, CacheVersion, isRelIncluded } from '@spgoding/datapack-language-server/lib/types';
import { walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import * as core from '@actions/core';
import { promises as fsp } from 'fs';
import path from 'path';
import { combineIndexSignatureForEach, FileChangeChecker, generateChecksum } from './utils';
import mather from './matcher.json';
import { getActionEventName, isCommitMessageIncluded, saveCache, tryGetCache } from './wrapper/actions';
import { EasyDatapackLanguageService } from './wrapper/DatapackLanguageService';
import { pathAccessible, readFile } from '@spgoding/datapack-language-server';
import { makeDefineData, makeLintData } from './parseResultProcessor';
import { Checksum, DocumentData, FailCount, IndexSignature, ParsedData } from './types';

async function run(dir: string) {
    // get inputs
    const testPath = core.getInput('outputDefine');

    // add Problem Matcher
    await fsp.writeFile(path.join(dir, 'matcher.json'), JSON.stringify(mather));
    core.info('::add-matcher::matcher.json');

    // #region pre cache restore: regenerate check
    let isRestoreCache = true;
    if (isCommitMessageIncluded('[regenerate cache]')) {
        core.info('The cache is not used because the commit message contains \'[regenerate cache]\'.');
        isRestoreCache = false;
    }
    if (getActionEventName() === 'workflow_dispatch') {
        core.info('The cache is not used because it is executed from the workflow_dispatch event.');
        isRestoreCache = false;
    }
    // #endregion

    // log group start
    core.startGroup('init log');

    // Env Log
    console.log(`dir: ${dir}`);

    // #region define cache paths
    const globalStoragePath = path.join(dir, '.cache');
    const cachePath = path.join(globalStoragePath, './cache.json');
    const checksumPath = path.join(globalStoragePath, './checksum.json');
    const lintCachePath = path.join(globalStoragePath, './lint.json');
    // #endregion

    // #region try restore cache and get cache files
    let checksumFile: Checksum | undefined = undefined;
    let cacheFile: CacheFile | undefined = undefined;
    let lintCache: IndexSignature<ParsedData> = {};
    if (isRestoreCache) {
        if (await tryGetCache(CacheVersion)) {
            checksumFile = JSON.parse(await readFile(checksumPath));
            cacheFile = JSON.parse(await readFile(cachePath));
            lintCache = JSON.parse(await readFile(lintCachePath));
        } else {
            core.info('The cache is not used because it failed to restore the cache. If this happens continuously, reporting it in the datapack-linter repository may help.');
        }
    }
    const fileChangeChecker = new FileChangeChecker(checksumFile);
    // #endregion

    // #region Cache Logs
    core.debug(JSON.stringify(checksumFile, undefined, ' '.repeat(4)));
    core.debug(JSON.stringify(cacheFile, undefined, ' '.repeat(4)));
    core.debug(JSON.stringify(lintCache, undefined, ' '.repeat(4)));
    // #endregion

    // #region post cache restore: Check config update
    const configFilePath = path.resolve(dir, './.vscode/settings.json');
    const configFileChecksum = await pathAccessible(configFilePath) ? await generateChecksum(configFilePath) : undefined;
    if (fileChangeChecker.isFileNotEqualChecksum(configFilePath, configFileChecksum)) {
        fileChangeChecker.clearChecksum();
        cacheFile = undefined;
        lintCache = {};
        fileChangeChecker.updateNextChecksum(configFilePath, configFileChecksum);
    }
    // #endregion

    // #region pre parse
    const easyDLS = await EasyDatapackLanguageService.createInstance(dir, globalStoragePath, cacheFile, fileChangeChecker, 500);
    const parseFiles: IndexSignature<DocumentData> = {};
    await Promise.all(easyDLS.roots.map(async root => await walkFile(
        root.fsPath,
        path.join(root.fsPath, 'data'),
        async (file, rel) => {
            if (fileChangeChecker.isFileNotEqualChecksum(file, await generateChecksum(file)))
                parseFiles[file] = { root: root.fsPath, rel };
        },
        async (_, rel) => isRelIncluded(rel, easyDLS.config)
    )));
    // #endregion

    // log group end
    core.endGroup();

    // #region parse and output lint results
    const failCount: FailCount = { warning: 0, error: 0 };
    await combineIndexSignatureForEach(
        lintCache,
        parseFiles,
        async ({ root, rel }, key) => {
            const { parseResult, identityNode, textDocument } = await easyDLS.parseDoc(key, rel) ?? {};
            if (!parseResult || !identityNode || !textDocument) return {};

            const lint = makeLintData(parseResult, identityNode, textDocument, root, rel);
            const define = makeDefineData(parseResult, identityNode, root, rel, testPath?.split(/\n/), easyDLS.config);
            const res: ParsedData = { lint, define };

            if (res.define?.length === 0) delete res.define;
            return res;
        },
        async ({ lint }, key, list) => {
            lint?.messages.forEach(core.info);

            const { warning, error } = lint?.failCount ?? { warning: 0, error: 0 };
            if (warning + error === 0) delete list[key].lint;

            failCount.warning += warning;
            failCount.error += error;
        }
    );

    const lintCacheKeys = Object.keys(lintCache);
    if (lintCacheKeys.some(v => lintCache[v].define)) {
        core.startGroup('defines');
        lintCacheKeys.forEach(v => lintCache[v].define?.forEach(core.info));
        core.endGroup();
    }
    // #endregion

    // #region end message output
    const { warning, error } = failCount;
    if (warning + error === 0) {
        core.info('Check successful');
    } else {
        core.info(`Check failed (${error} error${error > 1 ? 's' : ''}, ${warning} warning${warning > 1 ? 's' : ''})`);
        if (!core.isDebug())
            process.exitCode = core.ExitCode.Failure;
        else
            core.info('Test forced pass. Because debug mode');
    }
    // #endregion

    // save caches
    core.debug(await easyDLS.writeCacheFile(cachePath));
    core.debug(await fileChangeChecker.writeChecksumFile(checksumPath));
    core.debug(JSON.stringify(lintCache, undefined, ' '.repeat(4)));
    await fsp.writeFile(lintCachePath, JSON.stringify(lintCache), { encoding: 'utf8' });
    await saveCache(CacheVersion);
}

run(process.cwd());