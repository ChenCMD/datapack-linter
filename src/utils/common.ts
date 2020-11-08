import { pathAccessible } from '@spgoding/datapack-language-server/lib/utils';
import path from 'path';
import { FileType, fileTypeFolderName } from '../types/FileTypes';

export function getResourcePath(filePath: string, datapackRoot: string, fileType: FileType | undefined): string {
    return path.relative(datapackRoot, filePath).replace(/\\/g, '/').replace(RegExp(`^data/([^/]+)/${fileType ? fileTypeFolderName[fileType] : '[^/]+'}/(.*)\\.(?:mcfunction|json)$`), '$1:$2');
}

export async function getDatapackRoot(filePath: string): Promise<string | undefined> {
    if (filePath === path.dirname(filePath))
        return undefined;
    if (await isDatapackRoot(filePath))
        return filePath;
    return getDatapackRoot(path.dirname(filePath));
}

export async function isDatapackRoot(testPath: string): Promise<boolean> {
    return await pathAccessible(path.join(testPath, 'pack.mcmeta')) && await pathAccessible(path.join(testPath, 'data'));
}