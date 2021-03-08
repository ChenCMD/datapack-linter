import { context } from '@actions/github';
import * as cache from '@actions/cache';
import * as core from '@actions/core';

const cachedfiles = ['.cache'];

export function isCommitMessageIncluded(str: string): boolean {
    return !!context.payload.commits?.some((v: { message: string }) => v.message.toLowerCase().includes(str.toLowerCase()));
}

export function getActionEventName(): string {
    return context.eventName;
}

export async function tryGetCache(cacheVersion: number): Promise<boolean> {
    if (!context.payload.commits) return false;
    try {
        return !!await cache.restoreCache(cachedfiles, '', [getCacheKeyPrefix(cacheVersion)]);
    } catch (e) {
        core.warning('Failed to load the cache. The following errors may be resolved by reporting them in the datapack-linter repository.');
        core.warning(e);
        return false;
    }
}

export async function saveCache(cacheVersion: number): Promise<void> {
    try {
        return void await cache.saveCache(cachedfiles, getCacheKeyPrefix(cacheVersion) + context.runId);
    } catch (e) {
        core.warning('Failed to save the cache. The following errors may be resolved by reporting them in the datapack-linter repository.');
        core.warning(e);
    }
}

function getCacheKeyPrefix(version: number): string {
    return `datapack-linter-${context.payload.ref}-${version}-`;
}