import { CacheFile, DefaultCacheFile } from '@spgoding/datapack-language-server/lib/types';

export async function getCache(dir: string): Promise<CacheFile> {
    const cacheFile: CacheFile = DefaultCacheFile;
    // TODO get cache
    return cacheFile;
}