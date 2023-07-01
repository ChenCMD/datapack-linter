import * as cache from '@actions/cache';
import * as core from '@actions/core';
import { context } from '@actions/github';

const cachedFiles = ['.cache'];
const privateCacheVersion = 3;

export function isCommitMessageIncluded(str: string): boolean {
    return !!context.payload.commits?.some((v: { message: string }) => v.message.toLowerCase().includes(str.toLowerCase()));
}

export function getActionEventName(): string {
    return context.eventName;
}

export function getActionInput(type: 'string', key: string, defaultVal: string, required?: boolean): string;
export function getActionInput(type: 'string[]', key: string, defaultVal: string[], required?: boolean): string[];
export function getActionInput(type: 'number', key: string, defaultVal: number, required?: boolean): number | undefined;
export function getActionInput(type: 'boolean', key: string, defaultVal: boolean, required?: boolean): boolean | undefined;
export function getActionInput(type: 'string' | 'string[]' | 'number' | 'boolean', key: string, defaultVal: string | string[] | number | boolean, required = false): string | string[] | number | boolean | undefined {
    const resStr = core.getInput(key, { required });
    if (resStr === '') return defaultVal;
    if (type === 'string') return resStr;
    if (type === 'string[]') return resStr.split('\n');
    if (type === 'number' && resStr.match(/^(?:\+|-)?\d+(\.\d+)?$/)) return parseFloat(resStr);
    if (type === 'boolean' && resStr.match(/^(?:true|false)$/)) return resStr.toLowerCase() === 'true';
    core.error(`A string like "${type}" was requested, but ${resStr} was entered`);
    return defaultVal;
}

export async function tryRestoreCache(cacheVersion: number): Promise<boolean> {
    try {
        const fbKey = getCacheKey(cacheVersion);
        if (context.payload.created && context.payload.base_ref)
            return !!await cache.restoreCache(cachedFiles, getCacheKey(cacheVersion, context.payload.base_ref), [fbKey]);
        return !!await cache.restoreCache(cachedFiles, getCacheKey(cacheVersion, context.ref), [fbKey]);
    } catch (e) {
        core.warning('Failed to load the cache. The following errors may be resolved by reporting them in the datapack-linter repository.');
        core.warning(e);
        return false;
    }
}

export async function saveCache(cacheVersion: number): Promise<void> {
    try {
        await cache.saveCache(cachedFiles, getCacheKey(cacheVersion, context.ref, Date.now()));
        return;
    } catch (e) {
        core.warning('Failed to save the cache. The following errors may be resolved by reporting them in the datapack-linter repository.');
        core.warning(e);
    }
}

function getCacheKey(version: number, branch?: string, uniqueID?: number): string {
    return `datapack-linter-${version + privateCacheVersion}-${branch ?? ''}${branch ? '-' : ''}${uniqueID ?? ''}`;
}