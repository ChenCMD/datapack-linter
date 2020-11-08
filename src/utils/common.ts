import { pathAccessible } from '@spgoding/datapack-language-server';
import { getRootUri, walkRoot } from '@spgoding/datapack-language-server/lib/services/common';
import { Config, Uri } from '@spgoding/datapack-language-server/lib/types';
import path from 'path';
import { FileType, fileTypeFolderName } from '../types/FileTypes';

export function getResourcePath(filePath: string, datapackRoot: string, fileType: FileType | undefined): string {
    return path.relative(datapackRoot, filePath).replace(/\\/g, '/').replace(RegExp(`^data/([^/]+)/${fileType ? fileTypeFolderName[fileType] : '[^/]+'}/(.*)\\.(?:mcfunction|json)$`), '$1:$2');
}

export async function findDatapackRoots(dir: Uri, config: Config): Promise<Uri[]> {
    const rootCandidatePaths = new Set<string>();
    const dirPath = dir.fsPath;
    rootCandidatePaths.add(dirPath);
    await walkRoot(
        dir, dirPath,
        abs => rootCandidatePaths.add(abs),
        config.env.detectionDepth
    );
    const roots: Uri[] = [];
    for (const candidatePath of rootCandidatePaths) {
        const dataPath = path.join(candidatePath, 'data');
        const packMcmetaPath = path.join(candidatePath, 'pack.mcmeta');
        if (await pathAccessible(dataPath) && await pathAccessible(packMcmetaPath)) {
            const uri = getRootUri(Uri.file(candidatePath).toString());
            roots.push(uri);
        }
    }
    return roots;
}