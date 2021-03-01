import { CacheFile, CacheVersion } from '@spgoding/datapack-language-server/lib/types';
import { readFile } from '@spgoding/datapack-language-server';
import { context } from '@actions/github';
import cache from '@actions/cache';
import core from '@actions/core';
import path from 'path';

const cachedfiles = ['.cache'];
const key = `datapack-linter-${CacheVersion}-${context.payload.ref}`;

export async function tryGetCache(globalStoragePath: string): Promise<CacheFile | undefined> {
    if (!context.payload.commits) return undefined;
    try {
        const isSuccessRestore = await cache.restoreCache(cachedfiles, key);
        return isSuccessRestore ? JSON.parse(await readFile(path.join(globalStoragePath, './cache.json'))) : undefined;
    } catch (e) {
        core.warning('Failed to load the cache. The following errors may be resolved by reporting them in the datapack-linter repository.');
        core.warning(e);
        return undefined;
    }
}

export async function saveCache(): Promise<void> {
    try {
        return void await cache.saveCache(cachedfiles, key);
    } catch (e) {
        core.warning('Failed to save the cache. The following errors may be resolved by reporting them in the datapack-linter repository.');
        core.warning(e);
    }
}

export function isCommitMessageIncluded(str: string): boolean {
    return !!context.payload.commits?.some((v: { message: string }) => v.message.toLowerCase().includes(str.toLowerCase()));
}