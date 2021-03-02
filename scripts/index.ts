import { CacheFile, CacheVersion, isRelIncluded } from '@spgoding/datapack-language-server/lib/types';
import { walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import * as core from '@actions/core';
import { promises as fsp } from 'fs';
import path from 'path';
import { Result, getSafeRecordValue, printParseResult, generateChecksum } from './utils';
import { DocumentData } from './types/Results';
import mather from './matcher.json';
import { isCommitMessageIncluded, saveCache, tryGetCache } from './wrapper/actions';
import { EasyDatapackLanguageService } from './wrapper/DatapackLanguageService';
import { FileChangeChecker } from './utils/FileChangeChecker';
import { readFile } from '@spgoding/datapack-language-server';
import { Checksum } from './types/Checksum';

const dir = process.cwd();
lint();

async function lint() {
    // get inputs
    const testPath = core.getInput('outputDefine');

    // add Problem Matcher
    await fsp.writeFile(path.join(dir, 'matcher.json'), JSON.stringify(mather));
    core.info('::add-matcher::matcher.json');

    // log group start
    core.startGroup('init log');

    // Env Log
    console.log(`dir: ${dir}`);

    // cache path
    const globalStoragePath = path.join(dir, '.cache');
    const cachePath = path.join(globalStoragePath, './cache.json');
    const checksumPath = path.join(globalStoragePath, './checksum.json');

    // try restore cache and get cache files
    const isCacheRestoreSuccess = !isCommitMessageIncluded('[regenerate cache]') && await tryGetCache(CacheVersion);
    const cacheFile = isCacheRestoreSuccess ? JSON.parse(await readFile(cachePath)) as CacheFile : undefined;
    const checksumFile = isCacheRestoreSuccess ? JSON.parse(await readFile(checksumPath)) as Checksum : undefined;
    const fileChangeChecker = new FileChangeChecker(checksumFile);

    // Env Log
    if (core.isDebug()) {
        core.debug(JSON.stringify(checksumFile, undefined, '    '));
        core.debug(JSON.stringify(cacheFile, undefined, '    '));
    }

    // create EasyDLS
    const easyDLS = await EasyDatapackLanguageService.createInstance(dir, globalStoragePath, cacheFile, fileChangeChecker, 500);

    // pre parse Region
    const parsingFile: Record<string, DocumentData[]> = {};
    await Promise.all(easyDLS.roots.map(async root => await walkFile(
        root.fsPath,
        path.join(root.fsPath, 'data'),
        async (file, rel) => getSafeRecordValue(parsingFile, root.fsPath).push({ file, rel }),
        async (file, rel, stat) => isRelIncluded(rel, easyDLS.config) && (stat.isDirectory() || !fileChangeChecker.isFileNotEqualChecksum(file, await generateChecksum(file)))
    )));

    // log group end
    core.endGroup();

    // parse Region
    const result = new Result(testPath, easyDLS.config);
    for (const root of Object.keys(parsingFile).sort()) {
        for (const { file, rel } of parsingFile[root].sort()) {
            const parseRes = await easyDLS.parseDoc(file, rel);
            if (!parseRes) continue;
            result.addFailCount(printParseResult(parseRes.parseResult, parseRes.identityNode, parseRes.textDocument, root, rel));
            if (result.isOutDefine) result.appendDefineMessage(parseRes.parseResult, parseRes.identityNode, root, rel);
        }
    }

    // define message output
    if (result.isOutDefine) {
        core.startGroup('defines');
        result.defineMessage.forEach(core.info);
        core.endGroup();
    }

    // last message output
    if (result.hasFailCount()) {
        core.info('Check successful');
    } else {
        core.info(`Check failed (${result.getFailCountMessage()})`);
        if (!core.isDebug())
            process.exitCode = core.ExitCode.Failure;
        else
            core.info('Test forced pass. Because debug mode');
    }

    await easyDLS.writeCacheFile(cachePath);
    await fileChangeChecker.writeChecksumFile(checksumPath);
    await saveCache(CacheVersion);
}