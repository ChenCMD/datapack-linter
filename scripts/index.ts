import { isRelIncluded } from '@spgoding/datapack-language-server/lib/types';
import { walkFile } from '@spgoding/datapack-language-server/lib/services/common';
import * as core from '@actions/core';
import { promises as fsp } from 'fs';
import path from 'path';
import { Result, getSafeRecordValue, printParseResult } from './utils';
import { DocumentData } from './types/Results';
import mather from './matcher.json';
import { isCommitMessageIncluded, saveCache, tryGetCache } from './wrapper/actions';
import { EasyDatapackLanguageService } from './wrapper/DatapackLanguageService';
import { getDiffFiles, isDiffInculuded } from './types/ProcessedDiff';

const dir = process.cwd();
lint();

async function lint() {
    // cache path
    const globalStoragePath = path.join(dir, '.cache');

    // get inputs
    const testPath = core.getInput('outputDefine');
    const isDebug = core.getInput('DEBUG') === 'true';

    // add Problem Matcher
    await fsp.writeFile(path.join(dir, 'matcher.json'), JSON.stringify(mather));
    core.info('::add-matcher::matcher.json');

    // log group start
    core.startGroup('init log');

    // Env Log
    console.log(`dir: ${dir}`);

    // try restore cache and get compare files
    const isCacheRegeneration = isCommitMessageIncluded('[regenerate cache]');
    const cacheFile = isCacheRegeneration ? await tryGetCache(globalStoragePath) : undefined;
    const compareFiles = isCacheRegeneration ? await getDiffFiles() : undefined;

    // create EasyDLS
    const easyDLS = await EasyDatapackLanguageService.createInstance(dir, globalStoragePath, cacheFile, 500);

    await easyDLS.updateCacheFile(cacheFile ? compareFiles : undefined);

    // Env Log
    console.log('datapack roots:');
    easyDLS.roots.forEach(v => console.log(v.path));

    // pre parse Region
    const parsingFile: Record<string, DocumentData[]> = {};
    await Promise.all(easyDLS.roots.map(async root => await walkFile(
        root.fsPath,
        path.join(root.fsPath, 'data'),
        async (file, rel) => getSafeRecordValue(parsingFile, root.fsPath).push({ file, rel }),
        async (_, rel) => isRelIncluded(rel, easyDLS.config) && isDiffInculuded(rel, compareFiles, ['added', 'modified', 'renamed'])
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
        if (!isDebug)
            process.exitCode = core.ExitCode.Failure;
        else
            core.info('Test forced pass. Because debug mode');
    }

    await saveCache();
}